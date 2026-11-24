package io.github.hiveko.keycaps;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keycaps: a visual keyboard map of every key binding in the game, including the extra bindings and
 * modifiers of Multi Key Bindings and malilib hotkeys (MiniHUD, Tweakeroo, Litematica, …), with layers
 * for Ctrl, Alt and F3 combinations and key labels that follow the player's keyboard layout.
 *
 * <p>Nothing is built yet beyond this entry point; the design is in the project notes (keymap screen).
 */
public class Keycaps implements ClientModInitializer {
    public static final String MOD_ID = "keycaps";
    public static final Logger LOGGER = LoggerFactory.getLogger("Keycaps");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Keycaps loaded");
    }
}
