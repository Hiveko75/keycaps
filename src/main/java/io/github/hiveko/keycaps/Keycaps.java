package io.github.hiveko.keycaps;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.hiveko.keycaps.screen.KeycapsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keycaps: a visual keyboard map of every key binding in the game, including the extra bindings and
 * modifiers of Multi Key Bindings and malilib hotkeys (MiniHUD, Tweakeroo, Litematica, …), with layers
 * for Ctrl, Alt, Shift and F3 combinations and key labels that follow the player's keyboard layout.
 *
 * <p>Opens with its own key (Home by default) in game, on the title screen and on the pause screen, and from
 * other mods through {@link KeycapsApi#open}.
 */
public class Keycaps implements ClientModInitializer {
    public static final String MOD_ID = "keycaps";
    public static final Logger LOGGER = LoggerFactory.getLogger("Keycaps");

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    public static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        KeycapsConfig.load();
        openKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.keycaps.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_HOME, CATEGORY));

        // In game: the key mapping is ticked as usual.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) {
                if (mc.gui.screen() == null) KeycapsApi.open(null);
            }
        });

        // Title and pause screens don't tick key mappings, so the key is checked there directly.
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof TitleScreen || screen instanceof PauseScreen) {
                ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
                    if (!openKey.isUnbound() && openKey.matches(event) && Minecraft.getInstance().gui.screen() == s) {
                        KeycapsApi.open(s);
                    }
                });
            }
        });
        LOGGER.info("Keycaps loaded");
    }

    /** Opens the keyboard map (used by {@link KeycapsApi}). */
    static void openScreen(net.minecraft.client.gui.screens.Screen parent) {
        Minecraft.getInstance().gui.setScreen(new KeycapsScreen(parent));
    }
}
