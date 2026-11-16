package net.highwaybuilder.gui;

import net.highwaybuilder.HighwayBuilder;
import net.highwaybuilder.config.Config;
import net.highwaybuilder.config.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Settings screen built only from vanilla widgets. One tab per setting group, two columns. */
public final class HighwayScreen extends Screen {
    private static final int WIDGET_W = 150, WIDGET_H = 20, ROW = 24, GAP = 10;

    private final Screen parent;
    private static String currentGroup; // remembered between openings

    public HighwayScreen(Screen parent) {
        super(Text.literal("Highway Builder"));
        this.parent = parent;
    }

    private static List<String> groups() {
        LinkedHashSet<String> g = new LinkedHashSet<>();
        for (Setting<?> s : Config.ALL) g.add(s.group);
        return new ArrayList<>(g);
    }

    @Override
    protected void init() {
        List<String> groups = groups();
        if (currentGroup == null || !groups.contains(currentGroup)) currentGroup = groups.get(0);

        // ---- tabs
        int tabW = 80, tabGap = 4;
        int tabsTotal = groups.size() * tabW + (groups.size() - 1) * tabGap;
        int tx = width / 2 - tabsTotal / 2;
        for (String g : groups) {
            ButtonWidget tab = ButtonWidget.builder(Text.literal(g), b -> {
                currentGroup = g;
                clearAndInit();
            }).dimensions(tx, 30, tabW, WIDGET_H).build();
            tab.active = !g.equals(currentGroup);
            addDrawableChild(tab);
            tx += tabW + tabGap;
        }

        // ---- settings of the current tab, two columns
        List<Setting<?>> list = Config.ALL.stream().filter(s -> s.group.equals(currentGroup)).toList();
        int leftX = width / 2 - WIDGET_W - GAP / 2;
        int rightX = width / 2 + GAP / 2;
        int top = 62;
        for (int i = 0; i < list.size(); i++) {
            int x = i % 2 == 0 ? leftX : rightX;
            int y = top + (i / 2) * ROW;
            ClickableWidget w = widgetFor(list.get(i), x, y);
            w.setTooltip(Tooltip.of(Text.literal(list.get(i).description)));
            addDrawableChild(w);
        }

        // ---- bottom bar
        int by = height - 28;
        boolean active = HighwayBuilder.INSTANCE.isActive();
        addDrawableChild(ButtonWidget.builder(
                Text.literal(active ? "Stop building" : "Start building").formatted(active ? Formatting.RED : Formatting.GREEN),
                b -> {
                    if (HighwayBuilder.INSTANCE.isActive()) {
                        HighwayBuilder.INSTANCE.disable("Disabled.");
                        clearAndInit();
                    } else {
                        close(); // start after the screen is gone, using the direction you look at
                        HighwayBuilder.INSTANCE.enable();
                    }
                })
            .dimensions(width / 2 - 154, by, 100, WIDGET_H)
            .tooltip(Tooltip.of(Text.literal("Starts in the direction you are looking.")))
            .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset tab"), b -> {
                for (Setting<?> s : Config.ALL) if (s.group.equals(currentGroup)) s.reset();
                Config.save();
                clearAndInit();
            })
            .dimensions(width / 2 - 50, by, 100, WIDGET_H).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
            .dimensions(width / 2 + 54, by, 100, WIDGET_H).build());
    }

    // ------------------------------------------------------------------ widgets per type

    private ClickableWidget widgetFor(Setting<?> s, int x, int y) {
        if (s instanceof Setting.Bool b) {
            return ButtonWidget.builder(boolLabel(b), btn -> {
                b.parse("toggle");
                Config.save();
                btn.setMessage(boolLabel(b));
            }).dimensions(x, y, WIDGET_W, WIDGET_H).build();
        }
        if (s instanceof Setting.Choice<?> c) {
            return ButtonWidget.builder(choiceLabel(c), btn -> {
                c.cycle();
                Config.save();
                btn.setMessage(choiceLabel(c));
            }).dimensions(x, y, WIDGET_W, WIDGET_H).build();
        }
        if (s instanceof Setting.Int i) return new IntSlider(i, x, y);
        if (s instanceof Setting.Dbl d) return new DoubleSlider(d, x, y);
        if (s instanceof Setting.IdList l) {
            return ButtonWidget.builder(listLabel(l), btn -> client.setScreen(new IdListScreen(this, l)))
                .dimensions(x, y, WIDGET_W, WIDGET_H).build();
        }
        // fallback for future string settings
        return ButtonWidget.builder(Text.literal(pretty(s.name) + ": " + s.asString()), btn -> {})
            .dimensions(x, y, WIDGET_W, WIDGET_H).build();
    }

    private static Text boolLabel(Setting.Bool b) {
        return Text.literal(pretty(b.name) + ": ")
            .append(b.get() ? Text.literal("ON").formatted(Formatting.GREEN) : Text.literal("OFF").formatted(Formatting.RED));
    }

    private static Text choiceLabel(Setting.Choice<?> c) {
        return Text.literal(pretty(c.name) + ": ").append(Text.literal(pretty(c.asString())).formatted(Formatting.AQUA));
    }

    private static Text listLabel(Setting.IdList l) {
        int n = l.get().size();
        String first = n == 0 ? "none" : pretty(l.get().get(0).replace("minecraft:", ""));
        String more = n > 1 ? " +" + (n - 1) : "";
        return Text.literal(pretty(l.name) + ": ").append(Text.literal(first + more).formatted(Formatting.AQUA));
    }

    /** "mine-above-railings" -> "Mine above railings" */
    static String pretty(String id) {
        String s = id.replace('-', ' ').replace('_', ' ');
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private static final class IntSlider extends SliderWidget {
        private final Setting.Int setting;

        IntSlider(Setting.Int s, int x, int y) {
            super(x, y, WIDGET_W, WIDGET_H, Text.empty(), toSlider(s.get(), s.min(), s.max()));
            this.setting = s;
            updateMessage();
        }

        private static double toSlider(double v, double min, double max) {
            return max == min ? 0 : (v - min) / (max - min);
        }

        private int current() {
            return (int) Math.round(setting.min() + value * (setting.max() - setting.min()));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(pretty(setting.name) + ": ").append(Text.literal(String.valueOf(current())).formatted(Formatting.AQUA)));
        }

        @Override
        protected void applyValue() {
            setting.set(current());
            Config.save();
        }
    }

    private static final class DoubleSlider extends SliderWidget {
        private final Setting.Dbl setting;

        DoubleSlider(Setting.Dbl s, int x, int y) {
            super(x, y, WIDGET_W, WIDGET_H, Text.empty(), (s.get() - s.min()) / (s.max() - s.min()));
            this.setting = s;
            updateMessage();
        }

        private double current() {
            double raw = setting.min() + value * (setting.max() - setting.min());
            return Math.round(raw * 10) / 10.0; // 0.1 steps
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(pretty(setting.name) + ": ").append(Text.literal(String.valueOf(current())).formatted(Formatting.AQUA)));
        }

        @Override
        protected void applyValue() {
            setting.set(current());
            Config.save();
        }
    }

    // ------------------------------------------------------------------ screen

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFAA00);
        if (HighwayBuilder.INSTANCE.isActive() && HighwayBuilder.INSTANCE.geometry() != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Building " + HighwayBuilder.INSTANCE.geometry().name() + " (paused while this menu is open)"),
                width / 2, height - 42, 0xFFAAAAAA);
        }
    }

    @Override
    public void close() {
        Config.save();
        client.setScreen(parent);
    }
}
