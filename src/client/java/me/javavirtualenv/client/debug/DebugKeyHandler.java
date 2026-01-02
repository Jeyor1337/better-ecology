package me.javavirtualenv.client.debug;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Handles key bindings for the ecology debug overlay system.
 * Registers F7 as the toggle key for debug mode.
 */
public final class DebugKeyHandler {

    private static final String KEY_CATEGORY = "key.better-ecology.category";
    private static final String KEY_TOGGLE_DEBUG = "key.better-ecology.toggle_debug";

    private static KeyMapping debugToggleKey;

    private DebugKeyHandler() {
        // Static utility class
    }

    /**
     * Registers the debug toggle keybinding and the client tick event handler.
     * Should be called during client initialization.
     */
    public static void register() {
        debugToggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                KEY_TOGGLE_DEBUG,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(DebugKeyHandler::onClientTick);
    }

    /**
     * Handles key press detection during client tick.
     *
     * @param client the Minecraft client instance
     */
    private static void onClientTick(Minecraft client) {
        if (debugToggleKey == null) {
            return;
        }

        while (debugToggleKey.consumeClick()) {
            boolean newState = DebugConfig.toggleDebug();
            sendToggleMessage(client, newState);
        }
    }

    /**
     * Sends a chat message to the player indicating the new debug state.
     *
     * @param client the Minecraft client instance
     * @param enabled the new debug state
     */
    private static void sendToggleMessage(Minecraft client, boolean enabled) {
        if (client.player == null) {
            return;
        }

        String messageKey = enabled
                ? "message.better-ecology.debug_enabled"
                : "message.better-ecology.debug_disabled";
        String fallbackText = enabled ? "Ecology Debug: ON" : "Ecology Debug: OFF";

        Component message = Component.translatable(messageKey).withStyle(style ->
                style.withColor(enabled ? 0x55FF55 : 0xFF5555)
        );

        client.player.displayClientMessage(message, true);
    }
}
