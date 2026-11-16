package net.highwaybuilder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.highwaybuilder.config.Config;
import net.highwaybuilder.gui.HighwayScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HighwayBuilderMod implements ClientModInitializer {
    public static final String MOD_ID = "highwaybuilder";
    public static final Logger LOG = LoggerFactory.getLogger("Highway Builder");

    /** Set by /highway gui; the screen opens on the next tick, after the chat screen has closed. */
    public static boolean openGuiNextTick;

    private static KeyBinding guiKey;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        // Shows up under Options > Controls > Key Binds > "Highway Builder"
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.highwaybuilder.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.highwaybuilder.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> HighwayCommand.register(dispatcher));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (guiKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new HighwayScreen(null));
            }
            while (toggleKey.wasPressed()) {
                if (client.player != null) HighwayBuilder.INSTANCE.toggle();
            }
            if (openGuiNextTick) {
                openGuiNextTick = false;
                client.setScreen(new HighwayScreen(null));
            }
            HighwayBuilder.INSTANCE.tick();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HighwayBuilder.INSTANCE.onDisconnect());

        LOG.info("Highway Builder loaded. Press Right Shift or use /highway gui in game.");
    }
}
