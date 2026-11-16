package net.highwaybuilder.util;

import net.highwaybuilder.config.Config;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;

import java.util.function.Predicate;

/** Inventory helpers. Inventory indices: 0-8 hotbar, 9-35 main inventory. */
public final class InvUtil {
    private InvUtil() {}

    public static final int NOT_FOUND = -1;
    /** Returned by {@link #bestTool} when a suitable tool exists but is protected by dont-break-tools. */
    public static final int ONLY_WORN_TOOLS = -2;

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    public static boolean isPlaceBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) return false;
        String id = Registries.BLOCK.getId(bi.getBlock()).toString();
        return Config.BLOCKS_TO_PLACE.get().contains(id);
    }

    public static boolean isTrash(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return Config.TRASH_ITEMS.get().contains(Registries.ITEM.getId(stack.getItem()).toString());
    }

    public static boolean hasSilkTouch(ItemStack stack) {
        for (var entry : stack.getEnchantments().getEnchantmentEntries()) {
            if (entry.getKey().matchesKey(Enchantments.SILK_TOUCH)) return true;
        }
        return false;
    }

    public static int count(Predicate<ItemStack> filter) {
        PlayerInventory inv = mc().player.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (filter.test(s)) n += s.getCount();
        }
        return n;
    }

    public static int find(Predicate<ItemStack> filter) {
        PlayerInventory inv = mc().player.getInventory();
        for (int i = 0; i < 36; i++) if (!inv.getStack(i).isEmpty() && filter.test(inv.getStack(i))) return i;
        return NOT_FOUND;
    }

    public static boolean hasEmptySlot() {
        PlayerInventory inv = mc().player.getInventory();
        for (int i = 0; i < 36; i++) if (inv.getStack(i).isEmpty()) return true;
        return false;
    }

    /**
     * Finds the fastest tool for a block in the whole inventory.
     *
     * @return inventory index, {@link #NOT_FOUND} if nothing beats the hand,
     * or {@link #ONLY_WORN_TOOLS} if every usable tool is too damaged.
     */
    public static int bestTool(BlockState state, boolean forbidSilkTouch) {
        PlayerInventory inv = mc().player.getInventory();
        int best = NOT_FOUND;
        float bestSpeed = 1f;
        boolean rejected = false;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            float speed = s.getMiningSpeedMultiplier(state);
            if (speed <= 1f) continue;
            if (forbidSilkTouch && hasSilkTouch(s)) continue;
            if (isWorn(s)) {
                rejected = true;
                continue;
            }
            // prefer the hotbar on ties so we swap less
            if (speed > bestSpeed || (speed == bestSpeed && i < 9 && best >= 9)) {
                bestSpeed = speed;
                best = i;
            }
        }
        if (best == NOT_FOUND && rejected) return ONLY_WORN_TOOLS;
        return best;
    }

    public static boolean isWorn(ItemStack s) {
        return Config.DONT_BREAK_TOOLS.get() && s.isDamageable()
            && s.getMaxDamage() - s.getDamage() <= Config.MIN_DURABILITY.get();
    }

    /**
     * Makes sure the item at inventory index {@code index} is in the hotbar and selects it.
     *
     * @return the selected hotbar slot, or -1 if it could not be done.
     */
    public static int select(int index) {
        ClientPlayerEntity p = mc().player;
        if (index < 0) return -1;
        if (index >= 9) {
            if (p.currentScreenHandler != p.playerScreenHandler) return -1;
            int target = freeHotbarSlot();
            // In the player screen handler, inventory 9-35 has the same slot ids. Button = hotbar slot.
            mc().interactionManager.clickSlot(p.playerScreenHandler.syncId, index, target, SlotActionType.SWAP, p);
            index = target;
        }
        p.getInventory().setSelectedSlot(index);
        return index;
    }

    /** Selects the first matching stack, moving it to the hotbar if needed. */
    public static int select(Predicate<ItemStack> filter) {
        PlayerInventory inv = mc().player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (!inv.getStack(i).isEmpty() && filter.test(inv.getStack(i))) return select(i);
        }
        return select(find(filter));
    }

    /** A hotbar slot that can be overwritten without losing something important. */
    private static int freeHotbarSlot() {
        PlayerInventory inv = mc().player.getInventory();
        for (int i = 0; i < 9; i++) if (inv.getStack(i).isEmpty()) return i;
        for (int i = 8; i >= 0; i--) {
            ItemStack s = inv.getStack(i);
            if (s.isIn(ItemTags.PICKAXES) || s.contains(DataComponentTypes.FOOD) || isPlaceBlock(s)
                || s.isOf(Items.ENDER_CHEST) || s.isOf(Items.TOTEM_OF_UNDYING)) continue;
            return i;
        }
        return 8;
    }

    /** Throws one trash stack if the inventory is full. */
    public static boolean dropTrashIfFull() {
        ClientPlayerEntity p = mc().player;
        if (hasEmptySlot() || p.currentScreenHandler != p.playerScreenHandler) return false;
        PlayerInventory inv = p.getInventory();
        int selected = inv.getSelectedSlot();
        for (int i = 35; i >= 0; i--) {
            if (i == selected) continue;
            if (!isTrash(inv.getStack(i))) continue;
            int slotId = i < 9 ? 36 + i : i;
            // button 1 + THROW = drop the whole stack
            mc().interactionManager.clickSlot(p.playerScreenHandler.syncId, slotId, 1, SlotActionType.THROW, p);
            return true;
        }
        return false;
    }
}
