package io.github.hiveko.keycaps;

import java.util.List;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.hiveko.keycaps.model.Entry;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** The parts of Keycaps that differ by Minecraft version: 26.1.2 (GLFW input, Minecraft.setScreen). */
public final class Compat {
    private Compat() {}

    /** Keyboard keys as Minecraft keeps them on GLFW: key codes. */
    public static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYSYM;

    /**
     * The extra ISO key next to left Shift: GLFW calls it WORLD_2 on Windows and WORLD_1 on Linux and macOS
     * (Minecraft's key.keyboard.world.2 / world.1).
     */
    public static final String ISO_EXTRA = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
            ? "key.keyboard.world.2" : "key.keyboard.world.1";

    public static boolean isKeyDown(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }

    // ---------------------------------------------------------------- screens

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    public static Screen screen() {
        return Minecraft.getInstance().screen;
    }

    // ---------------------------------------------------------------- key mappings

    public static KeyMapping registerKey(String name, int key, String namespace, String category) {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(namespace, category));
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, KEYBOARD, key, cat));
    }

    public static boolean matches(KeyMapping mapping, KeyEvent event) {
        return mapping.matches(event);
    }

    /** Runs {@code action} after the key is pressed on this screen. */
    public static void afterKeyPress(Screen screen, KeyMapping mapping, Consumer<Screen> action) {
        ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
            if (!mapping.isUnbound() && mapping.matches(event)) action.accept(s);
        });
    }

    public static Component categoryLabel(KeyMapping mapping) {
        return mapping.getCategory().label();
    }

    public static String categoryId(KeyMapping mapping) {
        return mapping.getCategory().id().toString();
    }

    /** A category as another mod hands it over (Multi Key Bindings). */
    public static String categoryIdOf(Object category) {
        return category instanceof KeyMapping.Category c ? c.id().toString() : "";
    }

    // ---------------------------------------------------------------- debug (F3) keys: key mappings here

    public static List<KeyMapping> debugKeys(Options o) {
        return List.of(o.debugKeys);
    }

    public static KeyMapping debugModifier(Options o) {
        return o.keyDebugModifier;
    }

    public static KeyMapping debugOverlay(Options o) {
        return o.keyDebugOverlay;
    }

    public static InputConstants.Key debugModifierKey() {
        return KeyMappingHelper.getBoundKeyOf(Minecraft.getInstance().options.keyDebugModifier);
    }

    /** Versions with hard-coded F3 combinations list them here; this one has them as key mappings. */
    public static void addHardcodedDebug(List<Entry> out) {
    }

    // ---------------------------------------------------------------- drawing

    public static void push(GuiGraphicsExtractor g) {
        g.pose().pushMatrix();
    }

    public static void pop(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    public static void translate(GuiGraphicsExtractor g, float x, float y) {
        g.pose().translate(x, y);
    }

    public static void scale(GuiGraphicsExtractor g, float s) {
        g.pose().scale(s, s);
    }

    public static void tooltip(GuiGraphicsExtractor g, Font font, List<Component> lines, int mouseX, int mouseY) {
        g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    public static void nextLayer(GuiGraphicsExtractor g) {
        g.nextStratum();
    }
}
