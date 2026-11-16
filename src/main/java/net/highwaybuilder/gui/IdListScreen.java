package net.highwaybuilder.gui;

import net.highwaybuilder.config.Config;
import net.highwaybuilder.config.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Small editor for block/item lists: one text field, comma separated ids. */
public final class IdListScreen extends Screen {
    private final Screen parent;
    private final Setting.IdList setting;
    private TextFieldWidget field;
    private String error = "";

    public IdListScreen(Screen parent, Setting.IdList setting) {
        super(Text.literal(HighwayScreen.pretty(setting.name)));
        this.parent = parent;
        this.setting = setting;
    }

    @Override
    protected void init() {
        int w = Math.min(360, width - 40);
        String old = field != null ? field.getText() : String.join(", ", setting.get().stream()
            .map(id -> id.startsWith("minecraft:") ? id.substring(10) : id).toList());

        field = new TextFieldWidget(textRenderer, width / 2 - w / 2, height / 2 - 10, w, 20, Text.literal("ids"));
        field.setMaxLength(2048);
        field.setText(old);
        addDrawableChild(field);
        setInitialFocus(field);

        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> save())
            .dimensions(width / 2 - 154, height / 2 + 30, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Default"), b -> {
            setting.reset();
            field.setText(String.join(", ", setting.get().stream()
                .map(id -> id.startsWith("minecraft:") ? id.substring(10) : id).toList()));
            error = "";
        }).dimensions(width / 2 - 50, height / 2 + 30, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> client.setScreen(parent))
            .dimensions(width / 2 + 54, height / 2 + 30, 100, 20).build());
    }

    private void save() {
        try {
            setting.parse(field.getText());
        } catch (IllegalArgumentException e) {
            error = e.getMessage();
            return;
        }
        Config.save();
        client.setScreen(parent); // re-runs the parent's init, so its labels update
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 50, 0xFFFFAA00);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal(setting.description), width / 2, height / 2 - 36, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Comma separated, e.g. obsidian, crying_obsidian  (or: none)"), width / 2, height / 2 + 14, 0xFF777777);
        if (!error.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(error), width / 2, height / 2 + 58, 0xFFFF5555);
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
