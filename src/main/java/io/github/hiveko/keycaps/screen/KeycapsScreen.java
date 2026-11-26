package io.github.hiveko.keycaps.screen;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.hiveko.keycaps.Keycaps;
import io.github.hiveko.keycaps.KeycapsConfig;
import io.github.hiveko.keycaps.model.Collector;
import io.github.hiveko.keycaps.model.Entry;
import io.github.hiveko.keycaps.model.Groups;
import io.github.hiveko.keycaps.model.KeyboardShape;
import io.github.hiveko.keycaps.model.Layer;
import io.github.hiveko.keycaps.model.ModScreens;
import io.github.hiveko.keycaps.model.Situations;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The keyboard map: a tenkeyless keyboard (plus the three mouse buttons) coloured by what each key does,
 * with tabs for the Ctrl, Alt, Shift and F3 layers (no tab selected: keys without a modifier; clicking the
 * selected tab again goes back there, like the colour filter). Holding the real modifier key shows its layer;
 * hovering a key lists everything on it; clicking opens Minecraft's Key Binds screen (or the malilib mod's
 * settings). A key is a conflict when two of its actions can fire at the same time: with the smart check on,
 * only actions that share a situation ({@link Situations}) count; right-click marks pairs as fine.
 */
public class KeycapsScreen extends Screen {
    /** Space left around the window, like Better Advancements; the window fills the rest of the screen. */
    private static final int MARGIN = 30;
    /** Key size limits (GUI pixels per key): below MIN the numeric keypad is left out, above MAX the window shrinks. */
    private static final int MIN_UNIT_NUMPAD = 18, MAX_UNIT = 36;
    private static final int TEXT = 0xFFFFFFFF, TEXT_DIM = 0xFF9AA3AD, TEXT_FAINT = 0xFF6B737C;

    private final Screen parent;
    private List<Entry> entries = List.of();
    /** key name -> layer -> entries */
    private final Map<String, Map<Layer, List<Entry>>> byKey = new HashMap<>();
    private List<KeyboardShape.Cap> caps = List.of();
    private final Set<String> capKeys = new HashSet<>();
    private List<Layer> layers = List.of();
    private Layer selected = Layer.NORMAL;
    private final Map<Layer, Button> tabs = new LinkedHashMap<>();
    private Button shapeButton;

    private int px, py, pw, ph, unit, kbX, kbY, elsewhereY, legendY;
    private String hoveredKey;
    private boolean hoveredElsewhere;
    /** Filters: only one colour group (legend click), or only keys with more than one action. */
    private String filterGroup;
    private boolean conflictsOnly;
    private Button conflictsButton, smartButton;
    /** The right-click list of pairs on one key, or null. */
    private PairsPopup popup;
    private final List<LegendHit> legendHits = new ArrayList<>();
    private String hoveredLegend;

    private record LegendHit(int x0, int y0, int x1, int y1, String id) {
        boolean contains(double mx, double my) {
            return mx >= x0 && mx < x1 && my >= y0 && my < y1;
        }
    }

    public KeycapsScreen(Screen parent) {
        super(Component.translatable("keycaps.title"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- data and layout

    @Override
    protected void init() {
        Groups.load();
        Situations.load();
        popup = null;
        entries = Collector.collect();
        byKey.clear();
        for (Entry e : entries) {
            byKey.computeIfAbsent(e.key(), k -> new HashMap<>())
                    .computeIfAbsent(e.layer(), l -> new ArrayList<>()).add(e);
        }
        Set<Layer> used = new LinkedHashSet<>();
        for (Entry e : entries) used.add(e.layer());
        layers = new ArrayList<>(List.of(Layer.NORMAL, Layer.CTRL, Layer.ALT));
        if (used.contains(Layer.SHIFT)) layers.add(Layer.SHIFT);
        layers.add(Layer.F3);
        // "B +", "Z +": malilib key sequences that start with an ordinary key, by that key's label
        List<Layer> holds = new ArrayList<>();
        for (Layer l : used) if (l.isHold()) holds.add(l);
        holds.sort((a, b) -> Labels.keyLabel(a.holdKey).compareToIgnoreCase(Labels.keyLabel(b.holdKey)));
        layers.addAll(holds);
        if (!layers.contains(selected)) selected = Layer.NORMAL;

        // window: the screen minus a margin (smaller screens: almost all of it)
        pw = width - 2 * MARGIN >= 320 ? width - 2 * MARGIN : width - 8;
        ph = height - 2 * MARGIN >= 220 ? height - 2 * MARGIN : height - 8;

        // keyboard size: with the numeric keypad when its keys stay big enough
        int areaW = pw - 12, areaH = ph - 38 - 46;
        boolean numpad = unitFor(areaW, areaH, true) >= MIN_UNIT_NUMPAD;
        unit = Math.min(MAX_UNIT, unitFor(areaW, areaH, numpad));
        float kbWidth = KeyboardShape.width(numpad);
        int kbW = Math.round(kbWidth * unit), kbH = Math.round(KeyboardShape.HEIGHT * unit);
        if (unit == MAX_UNIT) {
            // very large screens: the window hugs the keyboard instead of growing further
            pw = Math.min(pw, kbW + 12);
            ph = Math.min(ph, kbH + 38 + 46 + 12);
            areaH = ph - 38 - 46;
        }
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        caps = KeyboardShape.caps(KeycapsConfig.effectiveShape() == KeycapsConfig.Shape.ISO, numpad);
        capKeys.clear();
        for (KeyboardShape.Cap c : caps) capKeys.add(c.key());

        // tabs: one per modifier layer; the plain keys are shown when none is selected
        tabs.clear();
        int tabW = Math.min(60, (pw - 12) / Math.max(1, layers.size() - 1));
        int tx = px + 6;
        for (Layer l : layers) {
            if (l == Layer.NORMAL) continue;
            Button b = Button.builder(layerLabel(l), btn -> {
                selected = selected == l ? Layer.NORMAL : l;
                updateTabs();
            }).bounds(tx, py + 18, tabW - 2, 16).build();
            tabs.put(l, addRenderableWidget(b));
            tx += tabW;
        }
        updateTabs();

        // keyboard, with the "Elsewhere" line and the legend under it, centred between the tabs and the footer
        int blockH = kbH + 3 + 11 + 19; // keyboard, "Elsewhere", legend (up to two rows)
        kbX = px + (pw - kbW) / 2;
        kbY = py + 38 + Math.max(0, (areaH + 46 - 24 - 4 - blockH) / 2);
        elsewhereY = kbY + kbH + 3;
        legendY = elsewhereY + 11;

        // footer: keyboard shape, Key Binds, conflicts, smart check, caption size, Done
        int by = py + ph - 24;
        int fw = pw - 12 - 5 * 4;
        int shapeW = fw * 22 / 100, keysW = fw * 15 / 100, conflictsW = fw * 17 / 100, smartW = fw * 14 / 100,
                sizeW = fw * 18 / 100;
        int doneW = fw - shapeW - keysW - conflictsW - smartW - sizeW;
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
        // own short label (vanilla's "Key Binds..." is very long in some languages), its full title as tooltip
        addRenderableWidget(Button.builder(Component.translatable("keycaps.button.keybinds"),
                b -> openKeyBinds()).bounds(fx, by, keysW, 20)
                .tooltip(Tooltip.create(Component.translatable("controls.keybinds.title"))).build());
        fx += keysW + 4;
        conflictsButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            conflictsOnly = !conflictsOnly;
            if (conflictsOnly) filterGroup = null;
        }).bounds(fx, by, conflictsW, 20).tooltip(Tooltip.create(Component.translatable("keycaps.tooltip.conflicts"))).build());
        fx += conflictsW + 4;
        smartButton = addRenderableWidget(Button.builder(smartLabel(), b -> {
            KeycapsConfig.setSmartConflicts(!KeycapsConfig.smartConflicts());
            b.setMessage(smartLabel());
        }).bounds(fx, by, smartW, 20).tooltip(Tooltip.create(Component.translatable("keycaps.tooltip.smart"))).build());
        fx += smartW + 4;
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

    private static int unitFor(int areaW, int areaH, boolean numpad) {
        return (int) Math.max(8, Math.floor(Math.min(areaW / KeyboardShape.width(numpad), areaH / KeyboardShape.HEIGHT)));
    }

    private void updateTabs() {
        for (Map.Entry<Layer, Button> t : tabs.entrySet()) {
            MutableComponent label = layerLabel(t.getKey()).copy();
            boolean on = t.getKey() == selected;
            if (on) label = label.withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE);
            t.getValue().setMessage(label);
            t.getValue().setTooltip(on ? Tooltip.create(Component.translatable("keycaps.tooltip.layer.off")) : null);
        }
    }

    private static Component smartLabel() {
        return Component.translatable("keycaps.button.smart",
                KeycapsConfig.smartConflicts() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
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
        if (l.isHold()) {
            return Component.translatable(l.translationKey, Labels.keyLabel(l.holdKey));
        }
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
        for (Layer l : layers) {
            if (!l.isHold()) continue;
            InputConstants.Key k = InputConstants.getKey(l.holdKey);
            if (k.getType() == InputConstants.Type.KEYBOARD && InputConstants.isKeyDown(k.getValue())) return l;
        }
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
        int realMouseX = mouseX, realMouseY = mouseY;
        float cs = captionScale();

        g.fill(px, py, px + pw, py + ph, 0xD0101318);
        g.outline(px, py, pw, ph, 0xFF3C434C);
        g.centeredText(font, title, width / 2, py + 6, TEXT);

        if (popup != null) {
            // the list covers the keyboard: nothing under it is hovered
            mouseX = -1000;
            mouseY = -1000;
        }
        hoveredKey = null;
        for (KeyboardShape.Cap c : caps) {
            int x0 = kbX + Math.round(c.x() * unit), y0 = kbY + Math.round(c.y() * unit);
            int x1 = kbX + Math.round((c.x() + c.w()) * unit) - 1, y1 = kbY + Math.round((c.y() + c.h()) * unit) - 1;
            boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
            if (hover) hoveredKey = c.key();
            drawCap(g, c, x0, y0, x1, y1, view, hover, cs);
        }
        drawElsewhere(g, view, mouseX, mouseY, cs);
        drawLegend(g, view, cs, mouseX, mouseY);
        updateConflictsButton(view);

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        if (popup != null) {
            popup.extract(g, realMouseX, realMouseY);
        } else if (hoveredKey != null) {
            g.setComponentTooltipForNextFrame(font, keyTooltip(hoveredKey), mouseX, mouseY);
        } else if (hoveredElsewhere) {
            g.setComponentTooltipForNextFrame(font, elsewhereTooltip(), mouseX, mouseY);
        } else if (hoveredLegend != null) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("keycaps.group." + hoveredLegend).withStyle(ChatFormatting.WHITE),
                    Component.translatable(hoveredLegend.equals(filterGroup) ? "keycaps.tooltip.legend.off" : "keycaps.tooltip.legend")
                            .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
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
        List<Entry> all = on(c.key(), view);
        boolean conflict = isConflict(c.key(), view);
        List<Entry> here = conflictsOnly && !conflict ? List.of() : filtered(all);
        Layer modifierLayer = Layer.ofModifier(c.key());
        boolean heldModifier = view != Layer.NORMAL && (modifierLayer == view
                || (view == Layer.F3 && c.key().equals(KeyMappingHelper.getBoundKeyOf(minecraft.options.keyDebugModifier).getName()))
                || (view.isHold() && c.key().equals(view.holdKey)));

        String caption = null;
        int fill, textColor = TEXT;
        if (!here.isEmpty()) {
            fill = (Groups.color(here.get(0).group()) & 0x00FFFFFF) | 0xD8000000;
            caption = Labels.caption(here.get(0));
            if (distinctIds(all) > 1) caption = caption + " +" + (distinctIds(all) - 1);
        } else if (!conflictsOnly && view == Layer.NORMAL && hasOtherLayers(c.key())) {
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
        int border = hover ? 0xFFFFFFFF : conflict && !here.isEmpty() ? 0xFFE0453A : heldModifier ? 0xFFE8C34A : 0x50000000;
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

    /** Two different actions on one key in one layer, and whether they count as a conflict. */
    private record Pair(Entry a, Entry b, boolean together, boolean marked, String stored) {
        boolean counts() {
            return together && !marked;
        }
    }

    /**
     * Every pair of different actions on this key in this layer. "together": they can fire at the same time
     * (always, with the smart check off); "marked": right-clicked as not a conflict.
     */
    private List<Pair> pairs(String key, Layer layer) {
        Map<String, Entry> byId = new LinkedHashMap<>();
        for (Entry e : on(key, layer)) byId.putIfAbsent(e.id(), e);
        List<Entry> list = new ArrayList<>(byId.values());
        List<Pair> out = new ArrayList<>();
        boolean smart = KeycapsConfig.smartConflicts();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                Entry a = list.get(i), b = list.get(j);
                String stored = KeycapsConfig.pair(layer.id, key, a.id(), b.id());
                out.add(new Pair(a, b, !smart || Situations.overlap(a.when(), b.when()), KeycapsConfig.notConflict(stored), stored));
            }
        }
        return out;
    }

    private boolean isConflict(String key, Layer layer) {
        if (distinctIds(on(key, layer)) < 2) return false;
        for (Pair p : pairs(key, layer)) if (p.counts()) return true;
        return false;
    }

    /** The actions on this key and layer that are part of a conflict (red in the hover text). */
    private Set<String> clashingIds(String key, Layer layer) {
        Set<String> out = new HashSet<>();
        if (distinctIds(on(key, layer)) < 2) return out;
        for (Pair p : pairs(key, layer)) {
            if (!p.counts()) continue;
            out.add(p.a().id());
            out.add(p.b().id());
        }
        return out;
    }

    private static int distinctIds(List<Entry> list) {
        Set<String> ids = new HashSet<>();
        for (Entry e : list) ids.add(e.id());
        return ids.size();
    }

    /** The action the key shows (its caption): first in the layer shown, else the grey one from another layer. */
    private Entry shownEntry(String key, Layer view) {
        List<Entry> all = on(key, view);
        List<Entry> here = conflictsOnly && !isConflict(key, view) ? List.of() : filtered(all);
        if (!here.isEmpty()) return here.get(0);
        if (!conflictsOnly && view == Layer.NORMAL) return firstOtherLayer(key);
        return null;
    }

    /** The entries the colour filter lets through (all of them without a filter). */
    private List<Entry> filtered(List<Entry> list) {
        if (filterGroup == null || list.isEmpty()) return list;
        List<Entry> out = new ArrayList<>();
        for (Entry e : list) if (e.group().equals(filterGroup)) out.add(e);
        return out;
    }

    private boolean hasOtherLayers(String key) {
        return firstOtherLayer(key) != null;
    }

    /** The first action on this key in another layer (that the colour filter lets through), or null. */
    private Entry firstOtherLayer(String key) {
        for (Layer l : layers) {
            if (l == Layer.NORMAL) continue;
            List<Entry> e = filtered(on(key, l));
            if (!e.isEmpty()) return e.get(0);
        }
        return null;
    }

    /** "Conflicts: N" for the layer shown; highlighted while only conflicts are shown. */
    private void updateConflictsButton(Layer view) {
        if (conflictsButton == null) return;
        int n = 0;
        for (String key : byKey.keySet()) if (isConflict(key, view)) n++;
        MutableComponent label = Component.translatable("keycaps.button.conflicts", n);
        if (conflictsOnly) label = label.withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE);
        conflictsButton.setMessage(label);
        conflictsButton.active = n > 0 || conflictsOnly;
    }

    private String shortLayer(Layer l) {
        if (l == Layer.CTRL) return "Ctrl:";
        if (l == Layer.ALT) return "Alt:";
        if (l == Layer.SHIFT) return "Shift:";
        if (l == Layer.F3) return KeyMappingHelper.getBoundKeyOf(minecraft.options.keyDebugModifier).getDisplayName().getString() + ":";
        if (l.isHold()) return Labels.keyLabel(l.holdKey) + ":";
        return "";
    }

    /** One line under the keyboard for actions on keys it doesn't show (numpad, extra mouse buttons, F13+). */
    private void drawElsewhere(GuiGraphicsExtractor g, Layer view, int mouseX, int mouseY, float cs) {
        hoveredElsewhere = false;
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, Map<Layer, List<Entry>>> k : byKey.entrySet()) {
            if (capKeys.contains(k.getKey())) continue;
            List<Entry> l = k.getValue().get(view);
            if (l == null || (conflictsOnly && !isConflict(k.getKey(), view))) continue;
            l = filtered(l);
            if (l.isEmpty()) continue;
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

    /**
     * The colour groups on the keys of this layer, in two centred rows: the general groups, then the mods
     * with their own colour (Litematica, Tweakeroo, MiniHUD, Axiom; "row": 2 in groups.json). Clicking one
     * shows only its keys; clicking the chosen one again shows all keys.
     */
    private void drawLegend(GuiGraphicsExtractor g, Layer view, float cs, int mouseX, int mouseY) {
        legendHits.clear();
        hoveredLegend = null;
        List<Groups.Group> row1 = new ArrayList<>(), row2 = new ArrayList<>();
        for (Groups.Group gr : Groups.all()) {
            boolean present = gr.id().equals(filterGroup);
            search:
            for (Map<Layer, List<Entry>> m : byKey.values()) {
                if (present) break;
                for (Entry e : m.getOrDefault(view, List.of())) {
                    if (e.group().equals(gr.id())) {
                        present = true;
                        break search;
                    }
                }
            }
            if (present) (gr.row() >= 2 ? row2 : row1).add(gr);
        }
        if (row1.isEmpty()) {
            row1 = row2;
            row2 = List.of();
        }
        drawLegendRow(g, row1, legendY, cs, mouseX, mouseY);
        drawLegendRow(g, row2, legendY + 10, cs, mouseX, mouseY);
    }

    private void drawLegendRow(GuiGraphicsExtractor g, List<Groups.Group> row, int y, float cs, int mouseX, int mouseY) {
        if (row.isEmpty()) return;
        int maxW = pw - 12;
        int[] widths = new int[row.size()];
        int total = 0;
        for (int i = 0; i < row.size(); i++) {
            widths[i] = 8 + (int) Math.ceil(font.width(Component.translatable("keycaps.group." + row.get(i).id())) * cs);
            total += widths[i];
        }
        // gap between items: 8 when it fits, less on a narrow window
        int gap = row.size() > 1 ? Math.max(2, Math.min(8, (maxW - total) / (row.size() - 1))) : 0;
        int rowW = total + gap * (row.size() - 1);
        int x = px + 6 + Math.max(0, (maxW - rowW) / 2);
        for (int i = 0; i < row.size(); i++) {
            if (x + widths[i] > px + pw - 6) break; // a very narrow window: the rest does not fit
            String id = row.get(i).id();
            Component name = Component.translatable("keycaps.group." + id);
            boolean chosen = id.equals(filterGroup);
            boolean faded = filterGroup != null && !chosen;
            int color = Groups.color(id);
            g.fill(x, y + 1, x + 6, y + 7, faded ? (color & 0x00FFFFFF) | 0x60000000 : color);
            if (chosen) g.outline(x - 1, y, 8, 8, 0xFFFFFFFF);
            g.pose().pushMatrix();
            g.pose().translate(x + 8, y + 1);
            g.pose().scale(cs, cs);
            g.text(font, chosen ? name.copy().withStyle(ChatFormatting.UNDERLINE) : name, 0, 0,
                    chosen ? TEXT : faded ? TEXT_FAINT : TEXT_DIM, false);
            g.pose().popMatrix();
            LegendHit hit = new LegendHit(x - 1, y - 1, x + widths[i] + 1, y + 9, id);
            legendHits.add(hit);
            if (hit.contains(mouseX, mouseY)) hoveredLegend = id;
            x += widths[i] + gap;
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
            Set<String> clash = clashingIds(key, l);
            for (Entry e : list) {
                any = true;
                MutableComponent line = Component.empty().append(prefix(l)).append(e.name().copy());
                line = line.withStyle(clash.contains(e.id()) ? ChatFormatting.RED : ChatFormatting.WHITE);
                line.append(Component.literal("  " + e.source().getString()).withStyle(ChatFormatting.DARK_GRAY));
                Component when = whenText(e);
                if (when != null) line.append(Component.literal(" · ").append(when).withStyle(ChatFormatting.DARK_GRAY));
                out.add(line);
                if (e.note() != null) out.add(Component.literal("   ").append(e.note()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
        if (!any) out.add(Component.translatable("keycaps.tooltip.nothing").withStyle(ChatFormatting.GRAY));
        Layer view = view();
        String mod = ModScreens.modOf(shownEntry(key, view));
        out.add((mod != null ? Component.translatable("keycaps.tooltip.click.mod", ModScreens.modName(mod))
                : Component.translatable("keycaps.tooltip.click")).withStyle(ChatFormatting.DARK_GRAY));
        if (distinctIds(on(key, view)) > 1) {
            out.add(Component.translatable("keycaps.tooltip.rightclick").withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    /** When the action works, unless it is just "in the world" (most actions): "in menus", "in chat", … */
    private static Component whenText(Entry e) {
        if (e.when().equals(Situations.IN_WORLD)) return null;
        MutableComponent out = Component.empty();
        boolean first = true;
        for (String s : e.when()) {
            if (!first) out.append(", ");
            first = false;
            String k = "keycaps.when." + s;
            out.append(net.minecraft.locale.Language.getInstance().has(k) ? Component.translatable(k) : Component.literal(s));
        }
        return out;
    }

    // ---------------------------------------------------------------- right-click: pairs that are fine

    /**
     * The pairs of actions on one key, each with a box to tick when the pair is not a conflict. Pairs the
     * smart check already lets through ("never at the same time") are shown greyed out.
     */
    private final class PairsPopup {
        private static final int ROW = 12;
        private final String key;
        private final Layer layer;
        private final int x, y, w, h;

        PairsPopup(String key, Layer layer, int mouseX, int mouseY) {
            this.key = key;
            this.layer = layer;
            int width = font.width(title()) + 12;
            for (Pair p : pairs(key, layer)) width = Math.max(width, 22 + font.width(rowText(p)) + 8);
            width = Math.max(width, font.width(Component.translatable("keycaps.pairs.hint")) + 12);
            w = Math.min(width, pw - 12);
            h = 6 + 11 + pairs(key, layer).size() * ROW + 4 + 10 + 4;
            x = Math.max(px + 6, Math.min(mouseX + 4, px + pw - 6 - w));
            y = Math.max(py + 6, Math.min(mouseY + 4, py + ph - 6 - h));
        }

        private Component title() {
            return Component.translatable("keycaps.pairs.title", Component.empty().append(prefix(layer)).append(Labels.keyName(key)));
        }

        private String rowText(Pair p) {
            String text = p.a().name().getString() + "  ↔  " + p.b().name().getString();
            if (!p.together()) {
                boolean off = p.a().when().contains(Situations.OFF) || p.b().when().contains(Situations.OFF);
                text += "  (" + Component.translatable(off ? "keycaps.pairs.off" : "keycaps.pairs.apart").getString() + ")";
            }
            return text;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        private int rowAt(double my) {
            int top = y + 6 + 11;
            if (my < top) return -1;
            int i = (int) ((my - top) / ROW);
            return i < pairs(key, layer).size() ? i : -1;
        }

        void extract(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            g.nextStratum();
            g.fill(x, y, x + w, y + h, 0xF0101318);
            g.outline(x, y, w, h, 0xFFFFFFFF);
            g.text(font, ellipsize(title().getString(), w - 12), x + 6, y + 6, TEXT, false);
            List<Pair> list = pairs(key, layer);
            int hover = contains(mouseX, mouseY) ? rowAt(mouseY) : -1;
            for (int i = 0; i < list.size(); i++) {
                Pair p = list.get(i);
                int ry = y + 6 + 11 + i * ROW;
                if (i == hover && p.together()) g.fill(x + 2, ry - 1, x + w - 2, ry + ROW - 1, 0xFF2C333B);
                // the box: ticked = not a conflict; greyed when the smart check already lets the pair through
                int box = p.together() ? 0xFFD0D0D0 : 0xFF4A5058;
                g.outline(x + 6, ry, 9, 9, box);
                if (p.marked() || !p.together()) g.text(font, "✔", x + 7, ry + 1, p.together() ? 0xFF7FD86A : 0xFF6B737C, false);
                int color = !p.together() ? TEXT_FAINT : p.marked() ? TEXT_DIM : 0xFFFF8A80;
                g.text(font, font.plainSubstrByWidth(rowText(p), w - 30), x + 20, ry + 1, color, false);
            }
            g.text(font, ellipsize(Component.translatable("keycaps.pairs.hint").getString(), w - 12), x + 6, y + h - 13, TEXT_FAINT, false);
        }

        /** Ticks or unticks the pair under the mouse; true when the click was inside the list. */
        boolean click(double mx, double my) {
            if (!contains(mx, my)) return false;
            int i = rowAt(my);
            List<Pair> list = pairs(key, layer);
            if (i >= 0 && list.get(i).together()) {
                Pair p = list.get(i);
                KeycapsConfig.setNotConflict(p.stored(), !p.marked());
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (popup != null) {
            // a click outside the list closes it
            if (!popup.click(event.x(), event.y())) popup = null;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && hoveredKey != null
                && distinctIds(on(hoveredKey, view())) > 1) {
            popup = new PairsPopup(hoveredKey, view(), (int) event.x(), (int) event.y());
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (LegendHit hit : legendHits) {
                if (!hit.contains(event.x(), event.y())) continue;
                filterGroup = hit.id().equals(filterGroup) ? null : hit.id();
                if (filterGroup != null) conflictsOnly = false;
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        if (hoveredKey != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            // a malilib hotkey (Litematica, MiniHUD, Tweakeroo, …) opens its own mod's settings
            if (!ModScreens.open(shownEntry(hoveredKey, view()), this)) openKeyBinds();
            return true;
        }
        return false;
    }

    /** The open key (Home by default) closes the map again. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (popup != null && event.key() == InputConstants.KEY_ESCAPE) {
            popup = null;
            return true;
        }
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
