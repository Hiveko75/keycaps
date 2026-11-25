package io.github.hiveko.keycaps.screen;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.hiveko.keycaps.Keycaps;
import io.github.hiveko.keycaps.KeycapsConfig;
import io.github.hiveko.keycaps.model.Collector;
import io.github.hiveko.keycaps.model.Entry;
import io.github.hiveko.keycaps.model.Groups;
import io.github.hiveko.keycaps.model.KeyboardShape;
import io.github.hiveko.keycaps.model.Layer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The keyboard map: a tenkeyless keyboard (plus the three mouse buttons) coloured by what each key does,
 * with tabs for the Ctrl, Alt, Shift and F3 layers. Holding the real modifier key shows its layer;
 * hovering a key lists everything on it; clicking opens Minecraft's Key Binds screen.
 */
public class KeycapsScreen extends Screen {
    private static final int PANEL_W = 430, PANEL_H = 262;
    private static final int TEXT = 0xFFFFFFFF, TEXT_DIM = 0xFF9AA3AD, TEXT_FAINT = 0xFF6B737C;

    private final Screen parent;
    private List<Entry> entries = List.of();
    /** key name -> layer -> entries */
    private final Map<String, Map<Layer, List<Entry>>> byKey = new HashMap<>();
    private List<KeyboardShape.Cap> caps = List.of();
    private final Set<String> capKeys = new HashSet<>();
    private List<Layer> layers = List.of();
    private Layer selected = Layer.NORMAL;
    private final Map<Layer, Button> tabs = new EnumMap<>(Layer.class);
    private Button shapeButton;

    private int px, py, pw, ph, unit, kbX, kbY, elsewhereY, legendY;
    private String hoveredKey;
    private boolean hoveredElsewhere;

    public KeycapsScreen(Screen parent) {
        super(Component.translatable("keycaps.title"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- data and layout

    @Override
    protected void init() {
        Groups.load();
        entries = Collector.collect();
        byKey.clear();
        for (Entry e : entries) {
            byKey.computeIfAbsent(e.key(), k -> new EnumMap<>(Layer.class))
                    .computeIfAbsent(e.layer(), l -> new ArrayList<>()).add(e);
        }
        caps = KeyboardShape.caps(KeycapsConfig.effectiveShape() == KeycapsConfig.Shape.ISO);
        capKeys.clear();
        for (KeyboardShape.Cap c : caps) capKeys.add(c.key());

        Set<Layer> used = new LinkedHashSet<>();
        for (Entry e : entries) used.add(e.layer());
        layers = new ArrayList<>(List.of(Layer.NORMAL, Layer.CTRL, Layer.ALT));
        if (used.contains(Layer.SHIFT)) layers.add(Layer.SHIFT);
        layers.add(Layer.F3);
        if (!layers.contains(selected)) selected = Layer.NORMAL;

        pw = Math.min(PANEL_W, width - 8);
        ph = Math.min(PANEL_H, height - 8);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        // tabs
        tabs.clear();
        int tabW = Math.min(60, (pw - 12) / layers.size());
        int tx = px + 6;
        for (Layer l : layers) {
            Button b = Button.builder(layerLabel(l), btn -> {
                selected = l;
                updateTabs();
            }).bounds(tx, py + 18, tabW - 2, 16).build();
            tabs.put(l, addRenderableWidget(b));
            tx += tabW;
        }
        updateTabs();

        // keyboard
        int top = py + 38;
        int bottom = py + ph - 46;
        unit = (int) Math.max(8, Math.floor(Math.min((pw - 12) / KeyboardShape.WIDTH, (bottom - top) / KeyboardShape.HEIGHT)));
        int kbW = Math.round(KeyboardShape.WIDTH * unit);
        kbX = px + (pw - kbW) / 2;
        kbY = top;
        elsewhereY = kbY + Math.round(KeyboardShape.HEIGHT * unit) + 3;
        legendY = elsewhereY + 11;

        // footer: keyboard shape, Key Binds, caption size, Done
        int by = py + ph - 24;
        int fw = pw - 12 - 3 * 4;
        int shapeW = fw * 31 / 100, keysW = fw * 23 / 100, sizeW = fw * 26 / 100, doneW = fw - shapeW - keysW - sizeW;
        int fx = px + 6;
        shapeButton = addRenderableWidget(Button.builder(shapeLabel(), b -> {
            KeycapsConfig.Shape next = switch (KeycapsConfig.shape()) {
                case AUTO -> KeycapsConfig.Shape.ANSI;
                case ANSI -> KeycapsConfig.Shape.ISO;
                case ISO -> KeycapsConfig.Shape.AUTO;
            };
            KeycapsConfig.setShape(next);
            rebuildWidgets();
        }).bounds(fx, by, shapeW, 20).build());
        fx += shapeW + 4;
        addRenderableWidget(Button.builder(Component.translatable("controls.keybinds"),
                b -> openKeyBinds()).bounds(fx, by, keysW, 20).build());
        fx += keysW + 4;
        addRenderableWidget(new CaptionSizeSlider(fx, by, sizeW, 20));
        fx += sizeW + 4;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(fx, by, doneW, 20).build());
    }

    /**
     * Size of the action names on the keys. The steps are whole screen pixels per font pixel (1 … GUI scale),
     * so the small text stays sharp; shown as a percentage of normal text.
     */
    private final class CaptionSizeSlider extends AbstractSliderButton {
        private final int steps;

        CaptionSizeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), 0);
            steps = Math.max(1, minecraft.getWindow().getGuiScale());
            int n = captionPixels();
            value = steps <= 1 ? 1 : (n - 1) / (double) (steps - 1);
            active = steps > 1;
            updateMessage();
        }

        private int stepOfValue() {
            return steps <= 1 ? 1 : 1 + (int) Math.round(value * (steps - 1));
        }

        @Override
        protected void updateMessage() {
            int percent = Math.round(100f * stepOfValue() / steps);
            setMessage(Component.translatable("keycaps.button.caption", percent));
        }

        @Override
        protected void applyValue() {
            KeycapsConfig.setCaptionSize(Math.round(100f * stepOfValue() / steps));
        }
    }

    private void updateTabs() {
        for (Map.Entry<Layer, Button> t : tabs.entrySet()) {
            MutableComponent label = layerLabel(t.getKey()).copy();
            if (t.getKey() == selected) label = label.withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE);
            t.getValue().setMessage(label);
        }
    }

    private Component shapeLabel() {
        KeycapsConfig.Shape s = KeycapsConfig.shape();
        Component name = s == KeycapsConfig.Shape.AUTO
                ? Component.translatable("keycaps.shape.auto", shapeName(KeycapsConfig.effectiveShape()))
                : shapeName(s);
        return Component.translatable("keycaps.button.shape", name);
    }

    private static Component shapeName(KeycapsConfig.Shape s) {
        return Component.translatable(s == KeycapsConfig.Shape.ISO ? "keycaps.shape.iso" : "keycaps.shape.ansi");
    }

    private Component layerLabel(Layer l) {
        if (l == Layer.F3) {
            return Component.translatable(l.translationKey, KeyMappingHelper.getBoundKeyOf(minecraft.options.keyDebugModifier).getDisplayName());
        }
        return Component.translatable(l.translationKey);
    }

    /** The layer being shown: the one whose modifier key is held, else the selected tab. */
    private Layer view() {
        if (InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL)) return Layer.CTRL;
        if (InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT)) return Layer.ALT;
        if (layers.contains(Layer.SHIFT)
                && (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT))) return Layer.SHIFT;
        InputConstants.Key dbg = KeyMappingHelper.getBoundKeyOf(minecraft.options.keyDebugModifier);
        if (dbg.getType() == InputConstants.Type.KEYBOARD && InputConstants.isKeyDown(dbg.getValue())) return Layer.F3;
        return selected;
    }

    private List<Entry> on(String key, Layer layer) {
        Map<Layer, List<Entry>> m = byKey.get(key);
        if (m == null) return List.of();
        List<Entry> l = m.get(layer);
        return l == null ? List.of() : l;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Layer view = view();
        float cs = captionScale();

        g.fill(px, py, px + pw, py + ph, 0xD0101318);
        g.outline(px, py, pw, ph, 0xFF3C434C);
        g.centeredText(font, title, width / 2, py + 6, TEXT);

        hoveredKey = null;
        for (KeyboardShape.Cap c : caps) {
            int x0 = kbX + Math.round(c.x() * unit), y0 = kbY + Math.round(c.y() * unit);
            int x1 = kbX + Math.round((c.x() + c.w()) * unit) - 1, y1 = kbY + Math.round((c.y() + c.h()) * unit) - 1;
            boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
            if (hover) hoveredKey = c.key();
            drawCap(g, c, x0, y0, x1, y1, view, hover, cs);
        }
        drawElsewhere(g, view, mouseX, mouseY, cs);
        drawLegend(g, view, cs);

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        if (hoveredKey != null) {
            g.setComponentTooltipForNextFrame(font, keyTooltip(hoveredKey), mouseX, mouseY);
        } else if (hoveredElsewhere) {
            g.setComponentTooltipForNextFrame(font, elsewhereTooltip(), mouseX, mouseY);
        }
    }

    /** The key's own label is drawn a little smaller: one screen pixel less per font pixel than the GUI scale. */
    private float captionScale() {
        int s = minecraft.getWindow().getGuiScale();
        return s >= 2 ? (s - 1f) / s : 1f;
    }

    /** Screen pixels per font pixel for the action names, from the caption size setting (50% by default). */
    private int captionPixels() {
        int s = Math.max(1, minecraft.getWindow().getGuiScale());
        int n = (int) Math.floor(s * KeycapsConfig.captionSize() / 100.0 + 0.05);
        return Math.max(1, Math.min(s, n));
    }

    /** Scale of the action names on the keys (whole screen pixels per font pixel, so the text stays sharp). */
    private float functionScale() {
        return captionPixels() / (float) Math.max(1, minecraft.getWindow().getGuiScale());
    }

    private void drawCap(GuiGraphicsExtractor g, KeyboardShape.Cap c, int x0, int y0, int x1, int y1, Layer view, boolean hover, float cs) {
        List<Entry> here = on(c.key(), view);
        Layer modifierLayer = Layer.ofModifier(c.key());
        boolean heldModifier = view != Layer.NORMAL && (modifierLayer == view
                || (view == Layer.F3 && c.key().equals(KeyMappingHelper.getBoundKeyOf(minecraft.options.keyDebugModifier).getName())));

        String caption = null;
        int fill, textColor = TEXT;
        if (!here.isEmpty()) {
            fill = (Groups.color(here.get(0).group()) & 0x00FFFFFF) | 0xD8000000;
            caption = Labels.caption(here.get(0));
            if (distinctIds(here) > 1) caption = caption + " +" + (distinctIds(here) - 1);
        } else if (view == Layer.NORMAL && hasOtherLayers(c.key())) {
            fill = 0xA0262B31;
            textColor = TEXT_DIM;
            Entry first = firstOtherLayer(c.key());
            caption = shortLayer(first.layer()) + " " + Labels.caption(first);
        } else {
            fill = 0x80181C21;
            textColor = TEXT_FAINT;
        }
        if (heldModifier) fill = 0xE05A4A14;

        g.fill(x0, y0, x1, y1, fill);
        int border = hover ? 0xFFFFFFFF : distinctIds(here) > 1 ? 0xFFE0453A : heldModifier ? 0xFFE8C34A : 0x50000000;
        g.outline(x0, y0, x1 - x0, y1 - y0, border);

        // key label, top left
        int innerPx = x1 - x0 - 3;
        g.pose().pushMatrix();
        g.pose().translate(x0 + 2, y0 + 2);
        g.pose().scale(cs, cs);
        String label = font.plainSubstrByWidth(Labels.keyLabel(c.key()), (int) (innerPx / cs));
        g.text(font, label, 0, 0, here.isEmpty() ? TEXT_DIM : 0xFFFFFFFF, false);
        g.pose().popMatrix();
        // action name under it, in smaller text (caption size setting), as many lines as fit (at most 3)
        if (caption != null) {
            float fs = functionScale();
            float labelH = (font.lineHeight - 1) * cs + 1;
            int innerW = (int) (innerPx / fs);
            float lineH = font.lineHeight * fs;
            int maxLines = Math.max(1, Math.min(3, (int) ((y1 - y0 - 3 - labelH) / lineH)));
            List<FormattedCharSequence> lines = font.split(Component.literal(caption), innerW);
            int n = Math.min(lines.size(), maxLines);
            g.pose().pushMatrix();
            g.pose().translate(x0 + 2, y0 + 2 + labelH);
            g.pose().scale(fs, fs);
            for (int i = 0; i < n; i++) {
                FormattedCharSequence line = lines.get(i);
                if (i == n - 1 && lines.size() > n) {
                    line = Component.literal(ellipsize(lastLineText(caption, lines, i), innerW)).getVisualOrderText();
                }
                g.text(font, line, 0, font.lineHeight * i, textColor, false);
            }
            g.pose().popMatrix();
        }
    }

    private String lastLineText(String caption, List<FormattedCharSequence> lines, int index) {
        // rebuild the plain text of lines index.. and cut it with an ellipsis
        StringBuilder sb = new StringBuilder();
        for (int i = index; i < lines.size(); i++) {
            lines.get(i).accept((pos, style, cp) -> {
                sb.appendCodePoint(cp);
                return true;
            });
            if (i < lines.size() - 1) sb.append(' ');
        }
        return sb.toString();
    }

    private String ellipsize(String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        return font.plainSubstrByWidth(s, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    private static int distinctIds(List<Entry> list) {
        Set<String> ids = new HashSet<>();
        for (Entry e : list) ids.add(e.id());
        return ids.size();
    }

    private boolean hasOtherLayers(String key) {
        Map<Layer, List<Entry>> m = byKey.get(key);
        return m != null && m.keySet().stream().anyMatch(l -> l != Layer.NORMAL);
    }

    private Entry firstOtherLayer(String key) {
        for (Layer l : layers) {
            if (l == Layer.NORMAL) continue;
            List<Entry> e = on(key, l);
            if (!e.isEmpty()) return e.get(0);
        }
        return on(key, Layer.SHIFT).get(0);
    }

    private String shortLayer(Layer l) {
        return switch (l) {
            case CTRL -> "Ctrl:";
            case ALT -> "Alt:";
            case SHIFT -> "Shift:";
            case F3 -> KeyMappingHelper.getBoundKeyOf(minecraft.options.keyDebugModifier).getDisplayName().getString() + ":";
            default -> "";
        };
    }

    /** One line under the keyboard for actions on keys it doesn't show (numpad, extra mouse buttons, F13+). */
    private void drawElsewhere(GuiGraphicsExtractor g, Layer view, int mouseX, int mouseY, float cs) {
        hoveredElsewhere = false;
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, Map<Layer, List<Entry>>> k : byKey.entrySet()) {
            if (capKeys.contains(k.getKey())) continue;
            List<Entry> l = k.getValue().get(view);
            if (l == null) continue;
            items.add(Labels.keyName(k.getKey()).getString() + ": " + Labels.caption(l.get(0)));
        }
        if (items.isEmpty()) return;
        items.sort(String::compareTo);
        String text = Component.translatable("keycaps.elsewhere").getString() + " " + String.join(" · ", items);
        int maxW = pw - 12;
        g.text(font, ellipsize(text, maxW), px + 6, elsewhereY, TEXT_DIM, false);
        hoveredElsewhere = mouseY >= elsewhereY - 1 && mouseY < elsewhereY + font.lineHeight && mouseX >= px + 6 && mouseX < px + 6 + maxW;
    }

    private List<Component> elsewhereTooltip() {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("keycaps.elsewhere.title").withStyle(ChatFormatting.WHITE));
        Map<String, Map<Layer, List<Entry>>> sorted = new LinkedHashMap<>();
        byKey.entrySet().stream().filter(e -> !capKeys.contains(e.getKey()))
                .sorted(Map.Entry.comparingByKey()).forEach(e -> sorted.put(e.getKey(), e.getValue()));
        for (Map.Entry<String, Map<Layer, List<Entry>>> k : sorted.entrySet()) {
            for (Layer l : layers) {
                for (Entry e : k.getValue().getOrDefault(l, List.of())) {
                    out.add(Component.empty().append(prefix(l)).append(Labels.keyName(k.getKey())).append(": ")
                            .append(e.name()).append(Component.literal("  " + e.source().getString()).withStyle(ChatFormatting.DARK_GRAY)));
                }
            }
        }
        return out;
    }

    private void drawLegend(GuiGraphicsExtractor g, Layer view, float cs) {
        Set<String> present = new LinkedHashSet<>();
        for (Groups.Group gr : Groups.all()) {
            for (String k : capKeys) {
                for (Entry e : on(k, view)) if (e.group().equals(gr.id())) present.add(gr.id());
            }
        }
        int x = px + 6;
        int maxX = px + pw - 6;
        for (String id : present) {
            Component name = Component.translatable("keycaps.group." + id);
            int w = 8 + (int) Math.ceil(font.width(name) * cs) + 8;
            if (x + w > maxX) break;
            g.fill(x, legendY + 1, x + 6, legendY + 7, Groups.color(id));
            g.pose().pushMatrix();
            g.pose().translate(x + 8, legendY + 1);
            g.pose().scale(cs, cs);
            g.text(font, name, 0, 0, TEXT_DIM, false);
            g.pose().popMatrix();
            x += w;
        }
    }

    private Component prefix(Layer l) {
        return l == Layer.NORMAL ? Component.empty() : Component.empty().append(layerLabel(l)).append(" ");
    }

    private List<Component> keyTooltip(String key) {
        List<Component> out = new ArrayList<>();
        out.add(Labels.keyName(key).copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        boolean any = false;
        for (Layer l : layers) {
            List<Entry> list = on(key, l);
            boolean clash = distinctIds(list) > 1;
            for (Entry e : list) {
                any = true;
                MutableComponent line = Component.empty().append(prefix(l)).append(e.name().copy());
                line = line.withStyle(clash ? ChatFormatting.RED : ChatFormatting.WHITE);
                line.append(Component.literal("  " + e.source().getString()).withStyle(ChatFormatting.DARK_GRAY));
                out.add(line);
                if (e.note() != null) out.add(Component.literal("   ").append(e.note()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
        if (!any) out.add(Component.translatable("keycaps.tooltip.nothing").withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable("keycaps.tooltip.click").withStyle(ChatFormatting.DARK_GRAY));
        return out;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (hoveredKey != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            openKeyBinds();
            return true;
        }
        return false;
    }

    /** The open key (Home by default) closes the map again. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (Keycaps.openKey != null && !Keycaps.openKey.isUnbound() && Keycaps.openKey.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void openKeyBinds() {
        minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options));
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
