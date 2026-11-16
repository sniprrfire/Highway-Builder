package net.highwaybuilder.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.highwaybuilder.HighwayBuilderMod;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** All settings of the mod, plus loading/saving to config/highwaybuilder.json. */
public final class Config {
    private Config() {}

    public enum FloorMode { REPLACE, PLACE_MISSING }

    /** CARDINAL = N/E/S/W only, DIAGONAL = NE/SE/SW/NW only, AUTO = nearest of all 8. */
    public enum DirectionMode { CARDINAL, DIAGONAL, AUTO }

    private static final List<Setting<?>> SETTINGS = new ArrayList<>();
    public static final List<Setting<?>> ALL = Collections.unmodifiableList(SETTINGS);

    private static <S extends Setting<?>> S add(S s) {
        SETTINGS.add(s);
        return s;
    }

    private static boolean isBlockId(String id) {
        Identifier i = Identifier.tryParse(id);
        return i != null && Registries.BLOCK.containsId(i);
    }

    private static boolean isItemId(String id) {
        Identifier i = Identifier.tryParse(id);
        return i != null && Registries.ITEM.containsId(i);
    }

    // ------------------------------------------------------------------ General

    public static final Setting.Int WIDTH = add(new Setting.Int("General", "width",
        "Width of the walkable lane, railings not included. On diagonals, values below 2 are treated as 2.", 4, 1, 8));

    public static final Setting.Int HEIGHT = add(new Setting.Int("General", "height",
        "Height of the tunnel that gets cleared above the floor.", 3, 2, 6));

    public static final Setting.Choice<FloorMode> FLOOR = add(new Setting.Choice<>("General", "floor",
        "replace: mines floor blocks that are not in blocks-to-place and paves them. place-missing: only fills holes.", FloorMode.REPLACE));

    public static final Setting.Bool RAILINGS = add(new Setting.Bool("General", "railings",
        "Places a one block high railing on both sides of the lane.", true));

    public static final Setting.Bool RAILING_SUPPORT = add(new Setting.Bool("General", "railing-support",
        "Places a block under each railing. When off, nothing is placed under the railings and railings over air or liquid are force placed in mid-air (air place).", true));

    public static final Setting.Bool MINE_ABOVE_RAILINGS = add(new Setting.Bool("General", "mine-above-railings",
        "Also clears the blocks above the railings so the tunnel is wider.", true));

    public static final Setting.Choice<DirectionMode> DIRECTION = add(new Setting.Choice<>("General", "direction",
        "cardinal: snap to N/E/S/W. diagonal: snap to NE/SE/SW/NW. auto: nearest of all eight, based on where you look when you start.", DirectionMode.CARDINAL));

    public static final Setting.Bool AUTO_WALK = add(new Setting.Bool("General", "auto-walk",
        "Walks forward on its own once the area in reach is finished.", true));

    public static final Setting.Bool CENTER = add(new Setting.Bool("General", "keep-centered",
        "Strafes left/right to stay on the center line of the highway.", true));

    public static final Setting.IdList BLOCKS_TO_PLACE = add(new Setting.IdList("General", "blocks-to-place",
        "Blocks used for the floor and railings.", List.of("minecraft:obsidian"), Config::isBlockId));

    public static final Setting.IdList TRASH_ITEMS = add(new Setting.IdList("General", "trash-items",
        "Items that get thrown out when the inventory is full.",
        List.of("minecraft:netherrack", "minecraft:quartz", "minecraft:gold_nugget", "minecraft:glowstone_dust",
            "minecraft:blackstone", "minecraft:basalt", "minecraft:soul_sand", "minecraft:soul_soil",
            "minecraft:magma_block", "minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:stone",
            "minecraft:dirt", "minecraft:gravel", "minecraft:flint", "minecraft:end_stone"),
        Config::isItemId));


    // ------------------------------------------------------------------ Interaction

    public static final Setting.Bool ROTATE = add(new Setting.Bool("Interaction", "rotate",
        "Sends a rotation towards each block before placing or mining it.", true));

    public static final Setting.Dbl PLACE_RANGE = add(new Setting.Dbl("Interaction", "place-range",
        "Maximum distance from your eyes to a block that gets placed.", 4.5, 1, 6));

    public static final Setting.Dbl BREAK_RANGE = add(new Setting.Dbl("Interaction", "break-range",
        "Maximum distance from your eyes to a block that gets mined.", 4.5, 1, 6));

    public static final Setting.Int PLACE_DELAY = add(new Setting.Int("Interaction", "place-delay",
        "Ticks to wait between placements.", 1, 0, 20));

    public static final Setting.Int BLOCKS_PER_TICK = add(new Setting.Int("Interaction", "blocks-per-tick",
        "How many blocks may be placed in one tick.", 1, 1, 8));

    public static final Setting.Bool DONT_BREAK_TOOLS = add(new Setting.Bool("Interaction", "dont-break-tools",
        "Never use a tool whose durability is at or below min-durability.", true));

    public static final Setting.Int MIN_DURABILITY = add(new Setting.Int("Interaction", "min-durability",
        "Durability left at which a tool is no longer used.", 15, 1, 500));

    public static final Setting.Bool PAUSE_WHILE_EATING = add(new Setting.Bool("Interaction", "pause-while-eating",
        "Stops building while you are using an item (eating, drinking).", true));

    public static final Setting.Bool DESTROY_CRYSTAL_TRAPS = add(new Setting.Bool("Interaction", "destroy-crystal-traps",
        "Hits end crystals close to you before they can be used against you.", true));

    // ------------------------------------------------------------------ Inventory

    public static final Setting.Bool MINE_ENDER_CHESTS = add(new Setting.Bool("Inventory", "mine-ender-chests",
        "When you run out of obsidian, places ender chests from your inventory and mines them for obsidian.", true));

    public static final Setting.Int SAVE_ENDER_CHESTS = add(new Setting.Int("Inventory", "save-ender-chests",
        "How many ender chests are never mined.", 1, 0, 64));

    public static final Setting.Int REFILL_TARGET = add(new Setting.Int("Inventory", "refill-target",
        "When mining ender chests, keep going until you have at least this much obsidian.", 64, 8, 576));

    // ------------------------------------------------------------------ Misc

    public static final Setting.Bool STATUS_BAR = add(new Setting.Bool("Misc", "status-bar",
        "Shows direction, block count and state above the hotbar.", true));

    public static final Setting.Bool DISABLE_ON_DISCONNECT = add(new Setting.Bool("Misc", "disable-on-disconnect",
        "Turns the builder off when you leave the world.", true));

    // ------------------------------------------------------------------ helpers

    public static Setting<?> byName(String name) {
        for (Setting<?> s : SETTINGS) if (s.name.equalsIgnoreCase(name)) return s;
        return null;
    }


    // ------------------------------------------------------------------ persistence

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("highwaybuilder.json");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(f)) {
            JsonObject obj = GSON.fromJson(r, JsonObject.class);
            if (obj == null) return;
            for (Setting<?> s : SETTINGS) {
                JsonElement e = obj.get(s.name);
                if (e == null || !e.isJsonPrimitive()) continue;
                try {
                    s.parse(e.getAsString());
                } catch (IllegalArgumentException ex) {
                    HighwayBuilderMod.LOG.warn("Ignoring invalid value for {}: {}", s.name, ex.getMessage());
                    s.reset();
                }
            }
        } catch (Exception e) {
            HighwayBuilderMod.LOG.error("Could not read config", e);
        }
    }

    public static void save() {
        JsonObject obj = new JsonObject();
        for (Setting<?> s : SETTINGS) obj.addProperty(s.name, s.asString());
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file())) {
                GSON.toJson(obj, w);
            }
        } catch (Exception e) {
            HighwayBuilderMod.LOG.error("Could not save config", e);
        }
    }
}
