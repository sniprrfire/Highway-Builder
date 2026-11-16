package net.highwaybuilder;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.highwaybuilder.config.Config;
import net.highwaybuilder.config.Setting;
import net.minecraft.command.CommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * /highway                      toggle
 * /highway on | off
 * /highway settings             list everything
 * /highway get &lt;setting&gt;
 * /highway set &lt;setting&gt; &lt;value&gt;
 * /highway reset [setting]
 */
public final class HighwayCommand {
    private HighwayCommand() {}

    private static final SuggestionProvider<FabricClientCommandSource> SETTING_NAMES = (ctx, builder) ->
        CommandSource.suggestMatching(Config.ALL.stream().map(s -> s.name), builder);

    public static void register(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(literal("highway")
            .executes(ctx -> {
                HighwayBuilder.INSTANCE.toggle();
                return 1;
            })
            .then(literal("on").executes(ctx -> {
                if (!HighwayBuilder.INSTANCE.isActive()) HighwayBuilder.INSTANCE.enable();
                return 1;
            }))
            .then(literal("off").executes(ctx -> {
                HighwayBuilder.INSTANCE.disable("Disabled.");
                return 1;
            }))
            .then(literal("gui").executes(ctx -> {
                HighwayBuilderMod.openGuiNextTick = true;
                return 1;
            }))
            .then(literal("settings").executes(HighwayCommand::list))
            .then(literal("get")
                .then(argument("setting", StringArgumentType.word()).suggests(SETTING_NAMES)
                    .executes(HighwayCommand::get)))
            .then(literal("set")
                .then(argument("setting", StringArgumentType.word()).suggests(SETTING_NAMES)
                    .then(argument("value", StringArgumentType.greedyString())
                        .executes(HighwayCommand::set))))
            .then(literal("reset")
                .executes(ctx -> {
                    Config.ALL.forEach(Setting::reset);
                    Config.save();
                    ctx.getSource().sendFeedback(prefix().append(Text.literal("All settings reset.").formatted(Formatting.GRAY)));
                    return 1;
                })
                .then(argument("setting", StringArgumentType.word()).suggests(SETTING_NAMES)
                    .executes(ctx -> {
                        Setting<?> s = find(ctx);
                        if (s == null) return 0;
                        s.reset();
                        Config.save();
                        ctx.getSource().sendFeedback(prefix().append(line(s)));
                        return 1;
                    }))));
    }

    private static int list(CommandContext<FabricClientCommandSource> ctx) {
        String group = null;
        ctx.getSource().sendFeedback(prefix().append(Text.literal("Settings (/highway set <name> <value>)").formatted(Formatting.GRAY)));
        for (Setting<?> s : Config.ALL) {
            if (!s.group.equals(group)) {
                group = s.group;
                ctx.getSource().sendFeedback(Text.literal(group).formatted(Formatting.YELLOW, Formatting.BOLD));
            }
            ctx.getSource().sendFeedback(Text.literal("  ").append(line(s)));
        }
        return 1;
    }

    private static int get(CommandContext<FabricClientCommandSource> ctx) {
        Setting<?> s = find(ctx);
        if (s == null) return 0;
        ctx.getSource().sendFeedback(prefix().append(line(s)));
        ctx.getSource().sendFeedback(Text.literal("  " + s.description).formatted(Formatting.GRAY));
        ctx.getSource().sendFeedback(Text.literal("  Values: " + s.hint()).formatted(Formatting.DARK_GRAY));
        return 1;
    }

    private static int set(CommandContext<FabricClientCommandSource> ctx) {
        Setting<?> s = find(ctx);
        if (s == null) return 0;
        String value = StringArgumentType.getString(ctx, "value");
        try {
            s.parse(value);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal(s.name + ": " + e.getMessage()));
            return 0;
        }
        Config.save();
        ctx.getSource().sendFeedback(prefix().append(line(s)));
        return 1;
    }

    private static Setting<?> find(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "setting");
        Setting<?> s = Config.byName(name);
        if (s == null) ctx.getSource().sendError(Text.literal("Unknown setting: " + name + ". Try /highway settings"));
        return s;
    }

    private static MutableText line(Setting<?> s) {
        return Text.literal(s.name).formatted(Formatting.WHITE)
            .append(Text.literal(" = ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal(s.asString()).formatted(Formatting.AQUA));
    }

    private static MutableText prefix() {
        return Text.literal("[Highway Builder] ").formatted(Formatting.GOLD);
    }
}
