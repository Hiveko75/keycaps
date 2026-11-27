package io.github.hiveko.keycaps;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.hiveko.keycaps.model.Entry;
import io.github.hiveko.keycaps.model.Groups;
import io.github.hiveko.keycaps.model.Layer;
import io.github.hiveko.keycaps.model.Situations;
import io.github.hiveko.keycaps.screen.KeyEvent;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The parts of Keycaps that differ by Minecraft version: 1.21.1 (GLFW input, Minecraft.setScreen, key
 * categories as strings, the F3 combinations hard-coded in KeyboardHandler, PoseStack, tooltips through
 * the screen).
 */
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
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key);
    }

    // ---------------------------------------------------------------- screens

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    public static Screen screen() {
        return Minecraft.getInstance().screen;
    }

    // ---------------------------------------------------------------- key mappings

    /** 1.21.1 key categories are translation keys; the same one as 26.x's keycaps:main category. */
    public static KeyMapping registerKey(String name, int key, String namespace, String category) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, KEYBOARD, key, "key.category." + namespace + "." + category));
    }

    public static boolean matches(KeyMapping mapping, KeyEvent event) {
        return mapping.matches(event.key(), event.scancode());
    }

    public static void afterKeyPress(Screen screen, KeyMapping mapping, Consumer<Screen> action) {
        ScreenKeyboardEvents.afterKeyPress(screen).register((s, key, scancode, modifiers) -> {
            if (!mapping.isUnbound() && mapping.matches(key, scancode)) action.accept(s);
        });
    }

    public static Component categoryLabel(KeyMapping mapping) {
        return Component.translatable(mapping.getCategory());
    }

    public static String categoryId(KeyMapping mapping) {
        return mapping.getCategory();
    }

    public static String categoryIdOf(Object category) {
        return category instanceof String s ? s : "";
    }

    // ---------------------------------------------------------------- debug (F3) keys: hard-coded here

    public static List<KeyMapping> debugKeys(Options o) {
        return List.of();
    }

    public static KeyMapping debugModifier(Options o) {
        return null;
    }

    public static KeyMapping debugOverlay(Options o) {
        return null;
    }

    /** F3 is not a key mapping before 1.21.9. */
    public static InputConstants.Key debugModifierKey() {
        return InputConstants.getKey("key.keyboard.f3");
    }

    /**
     * 1.21.1's F3 combinations (KeyboardHandler.handleDebugKeys and the F3 + Esc / F3 alone handling), with
     * the ids 26.x gives them as key mappings, so groups and situations.json treat them the same. Names come
     * from Minecraft's own F3 + Q help lines ("F3 + A = Reload chunks" without the "F3 + A = ").
     */
    public static void addHardcodedDebug(List<Entry> out) {
        Component source = Component.translatable("keycaps.source.debug");
        String context = "key.categories.debug " + source.getString();
        // the F3 key itself (on the normal layer), like 26.x's Toggle Overlay
        add(out, Layer.NORMAL, "key.keyboard.f3", "key.debug.overlay", Component.translatable("keycaps.debug.overlay"),
                source, context, Component.translatable("keycaps.note.debug_modifier"));
        Object[][] f3 = {
                {"a", "key.debug.reloadChunk", help("debug.reload_chunks.help")},
                {"b", "key.debug.showHitboxes", help("debug.show_hitboxes.help")},
                {"c", "key.debug.copyLocation", help("debug.copy_location.help")},
                {"c", "key.debug.crash", Component.translatable("keycaps.debug.crash")},
                {"d", "key.debug.clearChat", help("debug.clear_chat.help")},
                {"g", "key.debug.showChunkBorders", help("debug.chunk_boundaries.help")},
                {"h", "key.debug.showAdvancedTooltips", help("debug.advanced_tooltips.help")},
                {"i", "key.debug.copyRecreateCommand", help("debug.inspect.help")},
                {"l", "key.debug.profiling", help("debug.profiling.help")},
                {"n", "key.debug.spectate", help("debug.creative_spectator.help")},
                {"p", "key.debug.focusPause", help("debug.pause_focus.help")},
                {"q", "keycaps.debug.help", help("debug.help.help")},
                {"s", "key.debug.dumpDynamicTextures", help("debug.dump_dynamic_textures.help")},
                {"t", "key.debug.reloadResourcePacks", help("debug.reload_resourcepacks.help")},
                {"f4", "key.debug.switchGameMode", help("debug.gamemodes.help")},
                {"escape", "keycaps.debug.pause", help("debug.pause.help")},
                {"1", "key.debug.profilingChart", Component.translatable("keycaps.debug.profiling_chart")},
                {"2", "key.debug.fpsCharts", Component.translatable("keycaps.debug.fps_charts")},
                {"3", "key.debug.networkCharts", Component.translatable("keycaps.debug.network_charts")},
        };
        for (Object[] k : f3) {
            add(out, Layer.F3, "key.keyboard." + k[0], (String) k[1], (Component) k[2], source, context, null);
        }
    }

    private static void add(List<Entry> out, Layer layer, String key, String id, Component name, Component source,
                            String context, Component note) {
        Situations.Rule rule = Situations.ruleOf(id, context);
        Set<String> when = Situations.of(rule, Situations.IN_WORLD);
        out.add(new Entry(layer, key, id, name, source, Groups.groupOf(id, context), note, when));
    }

    /** "F3 + A = Reload chunks" → "Reload chunks" (also the first part only of "…, hold F3 + C to crash"). */
    private static Component help(String key) {
        String s = Component.translatable(key).getString();
        int eq = s.indexOf(" = ");
        if (eq >= 0) s = s.substring(eq + 3);
        int comma = s.indexOf(", ");
        if (comma > 0 && key.equals("debug.copy_location.help")) s = s.substring(0, comma);
        return Component.literal(s);
    }

    // ---------------------------------------------------------------- drawing

    public static void push(GuiGraphics g) {
        g.pose().pushPose();
    }

    public static void pop(GuiGraphics g) {
        g.pose().popPose();
    }

    public static void translate(GuiGraphics g, float x, float y) {
        g.pose().translate(x, y, 0.0F);
    }

    public static void scale(GuiGraphics g, float s) {
        g.pose().scale(s, s, 1.0F);
    }

    /** 1.21.1 draws a tooltip when the screen is done, if the screen was given one this frame. */
    public static void tooltip(GuiGraphics g, Font font, List<Component> lines, int mouseX, int mouseY) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return;
        List<FormattedCharSequence> text = new ArrayList<>();
        for (Component line : lines) text.addAll(font.split(line, Integer.MAX_VALUE));
        screen.setTooltipForNextRenderPass(text);
    }

    public static void nextLayer(GuiGraphics g) {
        g.flush();
        g.pose().translate(0.0F, 0.0F, 300.0F);
    }
}
