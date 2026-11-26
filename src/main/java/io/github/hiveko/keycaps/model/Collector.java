package io.github.hiveko.keycaps.model;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.hiveko.keycaps.Keycaps;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads every key binding live: Minecraft's key mappings (which includes every mod that uses the Key
 * Binds menu) with the debug (F3) combinations, Multi Key Bindings' extra bindings and modifiers, and
 * malilib hotkeys. Multi Key Bindings and malilib are read through reflection, so they stay optional.
 */
public final class Collector {
    private static final Set<String> WARNED = new HashSet<>();

    private Collector() {}

    /** Ids of the malilib hotkeys found by the last {@link #collect()}. */
    private static final Set<String> MALILIB_IDS = new HashSet<>();

    public static boolean isMalilib(Entry e) {
        return MALILIB_IDS.contains(e.id());
    }

    public static List<Entry> collect() {
        List<Entry> out = new ArrayList<>();
        MALILIB_IDS.clear();
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        Mkb mkb = Mkb.get();

        Set<KeyMapping> debug = new HashSet<>(List.of(o.debugKeys));
        InputConstants.Key debugModifier = KeyMappingHelper.getBoundKeyOf(o.keyDebugModifier);
        InputConstants.Key overlay = KeyMappingHelper.getBoundKeyOf(o.keyDebugOverlay);

        for (KeyMapping km : o.keyMappings) {
            if (km.isUnbound()) continue;
            InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(km);
            String id = km.getName();
            Component name = Component.translatable(id);
            Component source = km.getCategory().label();
            String context = km.getCategory().id().toString() + " " + source.getString();
            String group = Groups.groupOf(id, context);
            Situations.Rule rule = Situations.ruleOf(id, context);
            Component off = offNote(rule);
            Set<String> when = off != null ? Situations.SWITCHED_OFF : Situations.of(rule, Situations.IN_WORLD);
            if (debug.contains(km)) {
                if (km == o.keyDebugModifier) {
                    if (!key.equals(overlay)) {
                        out.add(new Entry(Layer.NORMAL, key.getName(), id, name, source, group,
                                join(Component.translatable("keycaps.note.debug_modifier"), off), when));
                    }
                } else if (km == o.keyDebugOverlay) {
                    Component note = key.equals(debugModifier) ? Component.translatable("keycaps.note.debug_modifier") : null;
                    out.add(new Entry(Layer.NORMAL, key.getName(), id, name, source, group, join(note, off), when));
                } else {
                    out.add(new Entry(Layer.F3, key.getName(), id, name, source, group, off, when));
                }
                continue;
            }
            addWithModifiers(out, key.getName(), withAdded(mkb.modifiers(id), rule), id, name, source, group, when, off);
        }

        // Multi Key Bindings: extra bindings for an action, and its own toggle actions
        for (Mkb.Extra b : mkb.extras()) {
            String base = b.action().startsWith("multi.") ? b.action().substring("multi.".length()) : b.action();
            String group = Groups.groupOf(b.action(), base + " " + b.category());
            Situations.Rule rule = Situations.ruleOf(base, b.category());
            Component source = Component.translatable("keycaps.source.mkb");
            List<String> mods = mkb.modifiers(b.id());
            if (mods.isEmpty()) mods = mkb.modifiers(b.action());
            Component off = offNote(rule);
            addWithModifiers(out, b.key(), withAdded(mods, rule), b.action(), b.name(), source, group,
                    off != null ? Situations.SWITCHED_OFF : Situations.of(rule, Situations.IN_WORLD), off);
        }

        Malilib.collect(out);
        return out;
    }

    /** The modifiers plus the one a situation rule says the mod adds itself (the emoji picker's Ctrl). */
    static List<String> withAdded(List<String> modifiers, Situations.Rule rule) {
        String added = Situations.addedModifier(rule);
        if (added == null) return modifiers;
        Layer layer = Layer.ofModifier(added);
        for (String m : modifiers) if (Layer.ofModifier(m) == layer) return modifiers;
        List<String> out = new ArrayList<>(modifiers);
        out.add(0, added);
        return out;
    }

    /**
     * "Works only while … is on" when an option the rule needs is switched off now ({@link Needs}), else null.
     * Such an action is {@link Situations#OFF}: shown, but never part of a conflict.
     */
    static Component offNote(Situations.Rule rule) {
        List<String> off = Needs.switchedOff(rule);
        if (off.isEmpty()) return null;
        return Component.translatable("keycaps.note.needs", String.join(", ", off));
    }

    /** Both notes in one line ("a · b"), or the one that is there. */
    static Component join(Component a, Component b) {
        if (a == null) return b;
        if (b == null) return a;
        return Component.empty().append(a).append(" · ").append(b);
    }

    /** Puts an action into the layer its modifiers stand for; other held keys become a note. */
    static void addWithModifiers(List<Entry> out, String key, List<String> modifiers, String id,
                                 Component name, Component source, String group, Set<String> when, Component extra) {
        Layer layer = Layer.NORMAL;
        List<String> other = new ArrayList<>();
        for (String m : modifiers) {
            Layer l = Layer.ofModifier(m);
            if (l != null && layer == Layer.NORMAL) layer = l;
            else other.add(m);
        }
        Component note = null;
        if (!other.isEmpty()) {
            net.minecraft.network.chat.MutableComponent held = Component.empty();
            for (int i = 0; i < other.size(); i++) {
                if (i > 0) held.append(" + ");
                held.append(InputConstants.getKey(other.get(i)).getDisplayName());
            }
            note = Component.translatable("keycaps.note.hold", held);
        }
        out.add(new Entry(layer, key, id, name, source, group, join(note, extra), when));
    }

    static void warnOnce(String what, Throwable t) {
        if (WARNED.add(what)) Keycaps.LOGGER.warn("Keycaps could not read {}; leaving it out", what, t);
    }

    // ------------------------------------------------------------------------------------------------

    /** Multi Key Bindings (us.kenny), through reflection. */
    static final class Mkb {
        record Extra(String id, String action, String key, Component name, String category) {}

        private static Mkb instance;
        private final boolean present;
        private Method getAllModifiers, getKeyBindings, getId, getAction, getKey, getTranslationKey, getCategory, isUnbound;

        static Mkb get() {
            if (instance == null) instance = new Mkb();
            return instance;
        }

        private Mkb() {
            boolean ok = false;
            if (FabricLoader.getInstance().isModLoaded("multi-key-bindings") || classExists("us.kenny.ModifierManager")) {
                try {
                    Class<?> mm = Class.forName("us.kenny.ModifierManager");
                    getAllModifiers = mm.getMethod("getAllModifiers");
                    Class<?> kbm = Class.forName("us.kenny.MultiKeyBindingManager");
                    getKeyBindings = kbm.getMethod("getKeyBindings");
                    Class<?> mkb = Class.forName("us.kenny.core.MultiKeyBinding");
                    getId = mkb.getMethod("getId");
                    getAction = mkb.getMethod("getAction");
                    getKey = mkb.getMethod("getKey");
                    try {
                        getTranslationKey = mkb.getMethod("getTranslationKey");
                    } catch (NoSuchMethodException e) {
                        getTranslationKey = null; // older Multi Key Bindings: worked out from the action below
                    }
                    getCategory = mkb.getMethod("getCategory");
                    isUnbound = mkb.getMethod("isUnbound");
                    ok = true;
                } catch (Throwable t) {
                    warnOnce("Multi Key Bindings", t);
                }
            }
            present = ok;
        }

        List<String> modifiers(String action) {
            if (!present) return List.of();
            try {
                Map<?, ?> all = (Map<?, ?>) getAllModifiers.invoke(null);
                Object list = all.get(action);
                if (!(list instanceof Collection<?> c)) return List.of();
                List<String> names = new ArrayList<>();
                for (Object k : c) if (k instanceof InputConstants.Key key) names.add(key.getName());
                return names;
            } catch (Throwable t) {
                warnOnce("Multi Key Bindings modifiers", t);
                return List.of();
            }
        }

        /**
         * The action's name key. Multi Key Bindings' getDisplayName() is the bound key's name ("Middle Button"),
         * so the name comes from getTranslationKey(): its own toggles keep their id ("multi.toggle.options.autoJump"
         * = Auto-Jump Toggle), extra bindings of a key binding drop the "multi." prefix ("key.crawl").
         */
        private String translationKey(Object binding, String action) {
            try {
                if (getTranslationKey != null && getTranslationKey.invoke(binding) instanceof String k && !k.isEmpty()) return k;
            } catch (Throwable t) {
                warnOnce("a Multi Key Bindings action name", t);
            }
            if (action.startsWith("multi.toggle.")) return action;
            return action.startsWith("multi.") ? action.substring("multi.".length()) : action;
        }

        List<Extra> extras() {
            if (!present) return List.of();
            List<Extra> out = new ArrayList<>();
            try {
                for (Object b : (Collection<?>) getKeyBindings.invoke(null)) {
                    if ((boolean) isUnbound.invoke(b)) continue;
                    InputConstants.Key key = (InputConstants.Key) getKey.invoke(b);
                    Object cat = getCategory.invoke(b);
                    String category = cat instanceof KeyMapping.Category c ? c.id().toString() : "";
                    String action = (String) getAction.invoke(b);
                    out.add(new Extra(String.valueOf(getId.invoke(b)), action, key.getName(),
                            Component.translatable(translationKey(b, action)), category));
                }
            } catch (Throwable t) {
                warnOnce("Multi Key Bindings bindings", t);
            }
            return out;
        }
    }

    /** malilib hotkeys (MiniHUD, Tweakeroo, Litematica, …), through reflection. */
    static final class Malilib {
        private static final Component SOURCE_FALLBACK = Component.literal("malilib");

        private static Method getKeybindManager, getKeybindCategories, getModName, getHotkeys, getKeybind, getKeys,
                getSettings, getContext, getName, getPrettyName;

        static void collect(List<Entry> out) {
            if (!FabricLoader.getInstance().isModLoaded("malilib")) return;
            try {
                if (getKeybindManager == null) {
                    // methods taken from the public interfaces, so the classes behind them may be anything
                    getKeybindManager = Class.forName("fi.dy.masa.malilib.event.InputEventHandler").getMethod("getKeybindManager");
                    getKeybindCategories = Class.forName("fi.dy.masa.malilib.hotkeys.IKeybindManager").getMethod("getKeybindCategories");
                    Class<?> cat = Class.forName("fi.dy.masa.malilib.hotkeys.KeybindCategory");
                    getModName = cat.getMethod("getModName");
                    getHotkeys = cat.getMethod("getHotkeys");
                    getKeybind = Class.forName("fi.dy.masa.malilib.hotkeys.IHotkey").getMethod("getKeybind");
                    Class<?> keybind = Class.forName("fi.dy.masa.malilib.hotkeys.IKeybind");
                    getKeys = keybind.getMethod("getKeys");
                    getSettings = keybind.getMethod("getSettings");
                    getContext = Class.forName("fi.dy.masa.malilib.hotkeys.KeybindSettings").getMethod("getContext");
                    Class<?> config = Class.forName("fi.dy.masa.malilib.config.IConfigBase");
                    getName = config.getMethod("getName");
                    getPrettyName = config.getMethod("getPrettyName");
                }
                Object manager = getKeybindManager.invoke(null);
                for (Object cat : (List<?>) getKeybindCategories.invoke(manager)) {
                    String modName = (String) getModName.invoke(cat);
                    for (Object hk : (List<?>) getHotkeys.invoke(cat)) {
                        try {
                            add(out, modName, hk);
                        } catch (Throwable t) {
                            warnOnce("a malilib hotkey of " + modName, t);
                        }
                    }
                }
            } catch (Throwable t) {
                warnOnce("malilib hotkeys", t);
            }
        }

        /**
         * One malilib hotkey. A single key goes on the plain keyboard. Combinations get their own tabs, so the
         * plain keyboard stays single keys only: Ctrl / Alt / Shift + key go to those layers, debug key + key
         * to the F3 layer, and a sequence that starts with an ordinary key (Litematica's B, C) to that key's
         * "B +" layer. Other sequences (two ordinary keys before the last) are left out.
         */
        private static void add(List<Entry> out, String modName, Object hotkey) throws Exception {
            Object keybind = getKeybind.invoke(hotkey);
            List<?> keys = (List<?>) getKeys.invoke(keybind);
            if (keys == null || keys.isEmpty()) return;
            Object context = getContext.invoke(getSettings.invoke(keybind));
            if (context instanceof Enum<?> e && "GUI".equals(e.name())) return; // only inside its own screens
            // malilib says where a hotkey works: INGAME (no screen open) or ANY (also in screens)
            boolean anywhere = context instanceof Enum<?> e && "ANY".equals(e.name());

            List<String> names = new ArrayList<>();
            for (Object k : keys) {
                String n = keyName(((Number) k).intValue());
                if (n == null) return;
                names.add(n);
            }
            String main = names.get(names.size() - 1);
            List<String> held = new ArrayList<>(names.subList(0, names.size() - 1));

            String hotkeyName = (String) getName.invoke(hotkey);
            String pretty = (String) getPrettyName.invoke(hotkey);
            String id = modName.toLowerCase().replace(' ', '_') + ":" + hotkeyName;
            Component source = modName.isEmpty() ? SOURCE_FALLBACK : Component.literal(modName);
            String group = Groups.groupOf(id, modName);
            Component name = Component.literal(pretty);
            MALILIB_IDS.add(id);
            Situations.Rule rule = Situations.ruleOf(id, modName);
            Component off = offNote(rule);
            Set<String> when = off != null ? Situations.SWITCHED_OFF
                    : Situations.of(rule, anywhere ? Situations.WORLD_AND_MENU : Situations.IN_WORLD);

            List<String> ordinary = new ArrayList<>();
            for (String h : held) if (Layer.ofModifier(h) == null) ordinary.add(h);
            if (ordinary.isEmpty()) {
                addWithModifiers(out, main, held, id, name, source, group, when, off);
            } else if (held.size() == 1) {
                String debugKey = KeyMappingHelper.getBoundKeyOf(Minecraft.getInstance().options.keyDebugModifier).getName();
                Layer layer = held.get(0).equals(debugKey) ? Layer.F3 : Layer.hold(held.get(0));
                out.add(new Entry(layer, main, id, name, source, group, off, when));
            }
        }

        /**
         * malilib 26.3 stores SDL scan codes (the key's position, like Minecraft's own key values: A = 4,
         * Left Alt = 226) and mouse buttons as Minecraft's button number minus 100 (left = -99).
         */
        private static String keyName(int code) {
            if (code >= -100 && code < 0) {
                return InputConstants.Type.MOUSE.getOrCreate(code + 100).getName();
            }
            if (code <= 0) return null;
            return InputConstants.Type.KEYBOARD.getOrCreate(code).getName();
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, Collector.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
