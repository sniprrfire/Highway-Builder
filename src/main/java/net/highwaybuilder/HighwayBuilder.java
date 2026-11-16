package net.highwaybuilder;

import net.highwaybuilder.config.Config;
import net.highwaybuilder.util.InvUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The highway builder itself. Driven once per client tick by {@link HighwayBuilderMod}. */
public final class HighwayBuilder {
    public static final HighwayBuilder INSTANCE = new HighwayBuilder();

    private enum State { BUILD, PLACE_ECHEST, MINE_ECHEST, COLLECT }

    private static final Direction[] PLACE_ORDER = {
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP
    };

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private boolean active;
    private HighwayGeometry geo;
    private int floorY; // y of the player's feet; the floor is floorY - 1
    private State state = State.BUILD;

    private BlockPos breaking;
    private BlockPos echestPos;
    private int placeTimer, stateTicks, idleTicks, crystalTimer, statusTimer, trashTimer, echestFails;
    private boolean refilling;
    private String status = "";

    private HighwayBuilder() {}

    public boolean isActive() {
        return active;
    }

    public HighwayGeometry geometry() {
        return geo;
    }

    // ------------------------------------------------------------------ on / off

    public void toggle() {
        if (active) disable("Disabled.");
        else enable();
    }

    public void enable() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        int[] d = HighwayGeometry.snap(p.getYaw(), Config.DIRECTION.get());
        geo = new HighwayGeometry(d[0], d[1], p.getBlockPos());
        floorY = p.getBlockPos().getY();
        state = State.BUILD;
        breaking = null;
        echestPos = null;
        refilling = false;
        idleTicks = stateTicks = placeTimer = echestFails = 0;
        active = true;
        p.setYaw(geo.yaw());
        info("Building " + geo.name() + (geo.diagonal ? " (diagonal)" : "") + " highway at Y " + floorY + ".");
    }

    public void disable(String reason) {
        if (!active) return;
        active = false;
        releaseKeys();
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        breaking = null;
        if (reason != null) info(reason);
    }

    /** Called when leaving a world. */
    public void onDisconnect() {
        if (!active) return;
        if (Config.DISABLE_ON_DISCONNECT.get()) {
            active = false;
            releaseKeys();
        }
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        if (!active) return;
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null || mc.interactionManager == null) return;

        if (p.getBlockPos().getY() < floorY - 1) {
            disable("You fell off the highway, stopping.");
            return;
        }
        if (mc.currentScreen != null || (Config.PAUSE_WHILE_EATING.get() && p.isUsingItem())) {
            releaseKeys();
            status = "Paused";
            showStatus();
            return;
        }

        if (placeTimer > 0) placeTimer--;
        if (Config.DESTROY_CRYSTAL_TRAPS.get()) hitCrystals();
        if (++trashTimer >= 5) {
            trashTimer = 0;
            InvUtil.dropTrashIfFull();
        }

        switch (state) {
            case BUILD -> build();
            case PLACE_ECHEST -> placeEchest();
            case MINE_ECHEST -> mineEchest();
            case COLLECT -> collect();
        }
        if (active) showStatus();
    }

    // ------------------------------------------------------------------ building

    private void build() {
        ClientPlayerEntity p = mc.player;
        Vec3d eye = p.getEyePos();
        double pa = geo.alongExact(p.getX(), p.getZ());

        List<BlockPos> toMine = new ArrayList<>();
        List<BlockPos> toPlace = new ArrayList<>();
        boolean floorAheadMissing = collectTasks(pa, eye, toMine, toPlace);

        boolean didSomething = false;

        // 1) mining (keep the current target so breaking progress is not lost)
        if (!toMine.isEmpty()) {
            BlockPos target = breaking != null && toMine.contains(breaking) ? breaking : toMine.get(0);
            if (!mine(target, false)) return; // disabled inside
            didSomething = true;
        } else if (breaking != null) {
            breaking = null;
        }

        // 2) placing
        if (!toPlace.isEmpty() && placeTimer <= 0) {
            if (InvUtil.count(InvUtil::isPlaceBlock) == 0) {
                outOfBlocks();
                return;
            }
            int placed = 0;
            for (BlockPos pos : toPlace) {
                if (placed >= Config.BLOCKS_PER_TICK.get()) break;
                if (place(pos, InvUtil::isPlaceBlock)) placed++;
            }
            if (placed > 0) {
                placeTimer = Config.PLACE_DELAY.get();
                didSomething = true;
            }
        } else if (!toPlace.isEmpty()) {
            didSomething = true; // waiting on the delay
        }

        // 3) movement
        boolean done = toMine.isEmpty() && toPlace.isEmpty() && !floorAheadMissing;
        p.setYaw(geo.yaw());
        if (Config.AUTO_WALK.get()) mc.options.forwardKey.setPressed(done);
        center();

        if (refilling && done) refilling = false;
        status = done ? "Walking" : "Building";

        if (didSomething || done) idleTicks = 0;
        else if (++idleTicks > 100) disable("Stuck: something ahead can't be mined or placed. Stopping.");
    }

    /**
     * Fills the two lists with work inside reach.
     *
     * @return true if the floor right in front of the player is not finished yet.
     */
    private boolean collectTasks(double pa, Vec3d eye, List<BlockPos> toMine, List<BlockPos> toPlace) {
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        int height = Config.HEIGHT.get();
        boolean railings = Config.RAILINGS.get();
        boolean mineAbove = Config.MINE_ABOVE_RAILINGS.get();
        boolean railSupport = Config.RAILING_SUPPORT.get();
        boolean replace = Config.FLOOR.get() == Config.FloorMode.REPLACE;
        double placeRange = Config.PLACE_RANGE.get(), breakRange = Config.BREAK_RANGE.get();
        int step = geo.step();
        int r = (int) Math.ceil(Math.max(placeRange, breakRange)) + 2;
        Box standing = p.getBoundingBox().offset(0, -0.5, 0).expand(0.05, 0, 0.05);
        Box body = p.getBoundingBox();
        boolean floorAheadMissing = false;

        BlockPos.Mutable m = new BlockPos.Mutable();
        int px = p.getBlockPos().getX(), pz = p.getBlockPos().getZ();

        for (int x = px - r; x <= px + r; x++) {
            for (int z = pz - r; z <= pz + r; z++) {
                int a = geo.along(x, z);
                if (a < pa - 2 * step) continue; // behind us, already done
                int l = geo.lateral(x, z);
                boolean lane = geo.inLane(l);
                boolean rail = railings && geo.isRailing(l);
                if (!lane && !rail) continue;

                for (int y = floorY - 1; y < floorY + height; y++) {
                    m.set(x, y, z);
                    BlockState st = w.getBlockState(m);
                    Vec3d center = Vec3d.ofCenter(m);
                    double dist = eye.distanceTo(center);
                    BlockPos pos = m.toImmutable();

                    // railing-support off: never touch the block under a railing
                    if (rail && !lane && y == floorY - 1 && !railSupport) continue;

                    boolean wantSolid = y == floorY - 1 || (rail && y == floorY);
                    boolean wantAir = !wantSolid && (lane || (rail && mineAbove));
                    if (!wantSolid && !wantAir) continue;

                    if (wantSolid) {
                        boolean placeBlockThere = isPlaceBlock(st);
                        if (st.isReplaceable()) {
                            if (lane && y == floorY - 1 && a > pa && a <= pa + 1.5 * step) floorAheadMissing = true;
                            if (dist <= placeRange && !body.intersects(new Box(pos))) toPlace.add(pos);
                        } else if (replace && !placeBlockThere && breakable(st, pos)) {
                            if (y == floorY - 1 && standing.intersects(new Box(pos))) continue; // never dig under our feet
                            if (lane && y == floorY - 1 && a > pa && a <= pa + 1.5 * step) floorAheadMissing = true;
                            if (dist <= breakRange) toMine.add(pos);
                        }
                    } else {
                        if (st.isAir()) continue;
                        if (!st.getFluidState().isEmpty()) {
                            // fill liquids first, the block gets mined afterwards
                            if (dist <= placeRange && !body.intersects(new Box(pos))) toPlace.add(pos);
                        } else if (breakable(st, pos) && dist <= breakRange) {
                            toMine.add(pos);
                        }
                    }
                }
            }
        }

        // nearest part of the highway first; mine top to bottom so falling blocks land in reach
        toMine.sort(Comparator.<BlockPos>comparingInt(b -> geo.along(b.getX(), b.getZ()))
            .thenComparingInt(b -> -b.getY())
            .thenComparingDouble(b -> eye.squaredDistanceTo(Vec3d.ofCenter(b))));
        toPlace.sort(Comparator.<BlockPos>comparingInt(b -> geo.along(b.getX(), b.getZ()))
            .thenComparingInt(BlockPos::getY)
            .thenComparingDouble(b -> eye.squaredDistanceTo(Vec3d.ofCenter(b))));
        return floorAheadMissing;
    }

    private boolean isPlaceBlock(BlockState st) {
        return Config.BLOCKS_TO_PLACE.get().contains(net.minecraft.registry.Registries.BLOCK.getId(st.getBlock()).toString());
    }

    private boolean breakable(BlockState st, BlockPos pos) {
        return !st.isAir() && st.getHardness(mc.world, pos) >= 0;
    }

    // ------------------------------------------------------------------ actions

    /** @return false if the builder had to stop. */
    private boolean mine(BlockPos pos, boolean forbidSilkTouch) {
        ClientPlayerEntity p = mc.player;
        BlockState st = mc.world.getBlockState(pos);

        int tool = InvUtil.bestTool(st, forbidSilkTouch);
        if (tool == InvUtil.ONLY_WORN_TOOLS) {
            disable("Your tools are almost broken (dont-break-tools). Stopping.");
            return false;
        }
        if (tool >= 0) {
            InvUtil.select(tool);
        } else if (InvUtil.isWorn(p.getMainHandStack())) {
            // mining by hand: make sure we are not holding a worn tool
            InvUtil.select(s -> !s.isDamageable());
        }

        Direction side = facingSide(p.getEyePos(), pos);
        if (Config.ROTATE.get()) look(Vec3d.ofCenter(pos));
        if (mc.interactionManager.updateBlockBreakingProgress(pos, side)) p.swingHand(Hand.MAIN_HAND);
        breaking = pos;
        return true;
    }

    /** Places one block from the inventory matching {@code item}. @return true if a placement was sent. */
    private boolean place(BlockPos pos, java.util.function.Predicate<net.minecraft.item.ItemStack> item) {
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;

        Direction dir = findSupport(pos);
        BlockPos support;
        Direction face;
        Vec3d hit;
        if (dir != null) {
            support = pos.offset(dir);
            face = dir.getOpposite();
            hit = Vec3d.ofCenter(support).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
        } else if (canAirPlace(pos)) {
            // Air place: click the empty spot itself. The server places the block right there,
            // because the clicked block is replaceable. No supporting block needed.
            support = pos;
            face = Direction.UP;
            hit = Vec3d.ofCenter(pos);
        } else {
            return false;
        }

        int slot = InvUtil.select(item);
        if (slot < 0) return false;

        if (Config.ROTATE.get()) look(hit);
        ActionResult result = mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hit, face, support, false));
        if (result.isAccepted()) p.swingHand(Hand.MAIN_HAND);
        return true;
    }

    /** Railings are force placed in mid-air when railing-support is off. */
    private boolean canAirPlace(BlockPos pos) {
        if (Config.RAILING_SUPPORT.get() || !Config.RAILINGS.get() || geo == null) return false;
        return pos.getY() == floorY && geo.isRailing(geo.lateral(pos.getX(), pos.getZ()));
    }

    /** Direction from {@code pos} to a neighbour that can be clicked to place a block there, or null. */
    private Direction findSupport(BlockPos pos) {
        for (Direction d : PLACE_ORDER) {
            BlockState s = mc.world.getBlockState(pos.offset(d));
            if (s.isReplaceable() || s.getBlock() instanceof BlockWithEntity) continue; // avoid opening chests
            return d;
        }
        return null;
    }

    private void hitCrystals() {
        if (crystalTimer > 0) {
            crystalTimer--;
            return;
        }
        ClientPlayerEntity p = mc.player;
        for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class, p.getBoundingBox().expand(5), e -> true)) {
            if (p.getEyePos().distanceTo(c.getBoundingBox().getCenter()) > 5) continue;
            if (Config.ROTATE.get()) look(c.getBoundingBox().getCenter());
            mc.interactionManager.attackEntity(p, c);
            p.swingHand(Hand.MAIN_HAND);
            crystalTimer = 4;
            return;
        }
    }

    // ------------------------------------------------------------------ ender chests

    private void outOfBlocks() {
        boolean canUseEchests = Config.MINE_ENDER_CHESTS.get()
            && Config.BLOCKS_TO_PLACE.get().contains("minecraft:obsidian")
            && InvUtil.count(s -> s.isOf(Items.ENDER_CHEST)) > Config.SAVE_ENDER_CHESTS.get();
        if (!canUseEchests) {
            disable("Out of blocks to place. Stopping.");
            return;
        }
        refilling = true;
        switchState(State.PLACE_ECHEST);
    }

    private void placeEchest() {
        releaseMovement();
        ClientPlayerEntity p = mc.player;
        Vec3d eye = p.getEyePos();
        double pa = geo.alongExact(p.getX(), p.getZ());
        double pl = geo.lateralExact(p.getX(), p.getZ());

        // a finished lane spot next to / in front of us, on solid floor
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int px = p.getBlockPos().getX(), pz = p.getBlockPos().getZ();
        for (int x = px - 3; x <= px + 3; x++) {
            for (int z = pz - 3; z <= pz + 3; z++) {
                int a = geo.along(x, z), l = geo.lateral(x, z);
                if (!geo.inLane(l) || a <= pa) continue;
                BlockPos pos = new BlockPos(x, floorY, z);
                if (!mc.world.getBlockState(pos).isAir() || !mc.world.getBlockState(pos.up()).isAir()) continue;
                if (mc.world.getBlockState(pos.down()).isReplaceable()) continue;
                if (p.getBoundingBox().intersects(new Box(pos))) continue;
                double dist = eye.distanceTo(Vec3d.ofCenter(pos));
                if (dist > Math.min(Config.PLACE_RANGE.get(), 3.5)) continue;
                double score = dist + Math.abs(l - pl) * 0.5;
                if (score < bestScore) {
                    bestScore = score;
                    best = pos;
                }
            }
        }
        if (best == null) {
            disable("No room to place an ender chest. Stopping.");
            return;
        }
        if (mc.world.getBlockState(best).isOf(Blocks.ENDER_CHEST)) {
            echestPos = best;
            switchState(State.MINE_ECHEST);
            return;
        }
        if (stateTicks++ == 0 || stateTicks % 10 == 0) {
            if (!place(best, s -> s.isOf(Items.ENDER_CHEST))) {
                disable("Could not place an ender chest. Stopping.");
                return;
            }
            echestPos = best;
        }
        if (echestPos != null && mc.world.getBlockState(echestPos).isOf(Blocks.ENDER_CHEST)) switchState(State.MINE_ECHEST);
        else if (stateTicks > 40) {
            if (++echestFails >= 3) disable("Ender chest placement keeps failing. Stopping.");
            else switchState(State.BUILD);
        }
    }

    private void mineEchest() {
        releaseMovement();
        if (echestPos == null || !mc.world.getBlockState(echestPos).isOf(Blocks.ENDER_CHEST)) {
            breaking = null;
            switchState(State.COLLECT);
            return;
        }
        int tool = InvUtil.bestTool(mc.world.getBlockState(echestPos), true);
        if (tool < 0 || !mc.player.getInventory().getStack(tool).isIn(net.minecraft.registry.tag.ItemTags.PICKAXES)) {
            disable("Need a pickaxe without Silk Touch to mine ender chests. Stopping.");
            return;
        }
        if (!mine(echestPos, true)) return;
        if (++stateTicks > 200) disable("Mining the ender chest takes too long. Stopping.");
    }

    private void collect() {
        ClientPlayerEntity p = mc.player;
        stateTicks++;
        // step onto the spot where the chest was so the drops get picked up
        if (echestPos != null) {
            Vec3d c = Vec3d.ofBottomCenter(echestPos);
            double dist = Math.hypot(c.x - p.getX(), c.z - p.getZ());
            p.setYaw(geo.yaw());
            mc.options.forwardKey.setPressed(dist > 0.6 && geo.alongExact(c.x, c.z) > geo.alongExact(p.getX(), p.getZ()));
        }
        int obsidian = InvUtil.count(s -> s.isOf(Items.OBSIDIAN));
        if (stateTicks >= 8 && obsidian > 0) {
            echestFails = 0;
            releaseMovement();
            boolean more = obsidian < Config.REFILL_TARGET.get()
                && InvUtil.count(s -> s.isOf(Items.ENDER_CHEST)) > Config.SAVE_ENDER_CHESTS.get()
                && InvUtil.hasEmptySlot();
            switchState(more ? State.PLACE_ECHEST : State.BUILD);
        } else if (stateTicks > 60) {
            releaseMovement();
            if (++echestFails >= 3) disable("Could not pick up obsidian from ender chests. Stopping.");
            else switchState(State.BUILD);
        }
    }

    private void switchState(State s) {
        state = s;
        stateTicks = 0;
    }

    // ------------------------------------------------------------------ movement & rotation

    private void center() {
        ClientPlayerEntity p = mc.player;
        if (!Config.CENTER.get()) return;
        double err = geo.lateralExact(p.getX(), p.getZ()) - geo.centerLateral();
        // lateral units are 1/sqrt(2) blocks on diagonals
        double tolerance = geo.diagonal ? 0.3 : 0.2;
        // positive lateral = to the player's right
        mc.options.leftKey.setPressed(err > tolerance);
        mc.options.rightKey.setPressed(err < -tolerance);
    }

    private void releaseMovement() {
        mc.options.forwardKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
    }

    private void releaseKeys() {
        if (mc.options == null) return;
        releaseMovement();
    }

    private void look(Vec3d target) {
        ClientPlayerEntity p = mc.player;
        Vec3d eye = p.getEyePos();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(pitch, -90f, 90f);
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, p.isOnGround(), p.horizontalCollision));
    }

    private static Direction facingSide(Vec3d eye, BlockPos pos) {
        Vec3d c = Vec3d.ofCenter(pos);
        double dx = eye.x - c.x, dy = eye.y - c.y, dz = eye.z - c.z;
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        if (ay >= ax && ay >= az) return dy > 0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    // ------------------------------------------------------------------ messages

    private void showStatus() {
        if (!Config.STATUS_BAR.get() || ++statusTimer < 10) return;
        statusTimer = 0;
        String s = switch (state) {
            case BUILD -> status;
            case PLACE_ECHEST, MINE_ECHEST, COLLECT -> "Refilling obsidian";
        };
        int blocks = InvUtil.count(InvUtil::isPlaceBlock);
        mc.inGameHud.setOverlayMessage(Text.literal("Highway Builder ").formatted(Formatting.GOLD)
            .append(Text.literal(geo.name() + " | " + blocks + " blocks | " + s).formatted(Formatting.GRAY)), false);
    }

    public static void info(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud == null) return;
        mc.inGameHud.getChatHud().addMessage(Text.literal("[Highway Builder] ").formatted(Formatting.GOLD)
            .append(Text.literal(msg).formatted(Formatting.GRAY)));
    }
}
