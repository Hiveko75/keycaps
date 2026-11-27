package io.github.hiveko.keycaps;

import net.minecraft.client.gui.screens.Screen;

/**
 * The entry point for other mods (for example Vanilla Finest Helper's setup screen), meant to be called
 * through reflection so that they don't need Keycaps to compile:
 *
 * <pre>
 * Class.forName("io.github.hiveko.keycaps.KeycapsApi").getMethod("open", Screen.class).invoke(null, parent);
 * </pre>
 */
public final class KeycapsApi {
    private KeycapsApi() {}

    /** Opens the keyboard map; Done (or Esc) goes back to {@code parent}, or to the game when it is null. */
    public static void open(Screen parent) {
        Keycaps.openScreen(parent);
    }
}
