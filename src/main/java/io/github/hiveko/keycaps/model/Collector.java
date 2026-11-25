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

    public static List<Entry> collect() {
        List<Entry> out = new ArrayList<>();
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
            if (debug.contains(km)) {
                if (km == o.keyDebugModifier) {
                    if (!key.equals(overlay)) {
                        out.add(new Entry(Layer.NORMAL, key.getName(), id, name, source, group,
                                Component.translatable("keycaps.note.debug_modifier")));
                    }
                } else if (km == o.keyDebugOverlay) {
                    Component note = key.equals(debugModifier) ? Component.translatable("keycaps.note.debug_modifier") : null;
                    out.add(new Entry(Layer.NORMAL, key.getName(), id, name, source, group, note));
                } else {
                    out.add(new Entry(Layer.F3, key.getName(), id, name, source, group, null));
                }
                continue;
            }
            addWithModifiers(out, key.getName(), mkb.modifiers(id), id, name, source, group);
        }

        // Multi Key Bindings: extra bindings for an action, and its own toggle actions
        for (Mkb.Extra b : mkb.extras()) {
            String base = b.action().startsWith("multi.") ? b.action().substring("multi.".length()) : b.action();
            String group = Groups.groupOf(b.action(), base + " " + b.category());
            Component source = Component.translatable("keycaps.source.mkb");
            List<String> mods = mkb.modifiers(b.id());
            if (mods.isEmpty()) mods = mkb.modifiers(b.action());
            addWithModifiers(out, b.key(), mods, b.action(), b.name(), source, group);
        }

        Malilib.collect(out);
        return out;
    }

    /** Puts an action into the layer its modifiers stand for; other held keys become a note. */
    static void addWithModifiers(List<Entry> out, String key, List<String> modifiers, String id,
                                 Component name, Component source, String group) {
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
        out.add(new Entry(layer, key, id, name, source, group, note));
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
        private Method getAllModifiers, getKeyBindings, getId, getAction, getKey, getDisplayName, getCategory, isUnbound;

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
                    getDisplayName = mkb.getMethod("getDisplayName");
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

        List<Extra> extras() {
            if (!present) return List.of();
            List<Extra> out = new ArrayList<>();
            try {
                for (Object b : (Collection<?>) getKeyBindings.invoke(null)) {
                    if ((boolean) isUnbound.invoke(b)) continue;
                    InputConstants.Key key = (InputConstants.Key) getKey.invoke(b);
                    Object cat = getCategory.invoke(b);
                    String category = cat instanceof KeyMapping.Category c ? c.id().toString() : "";
                    out.add(new Extra(String.valueOf(getId.invoke(b)), (String) getAction.invoke(b), key.getName(),
                            (Component) getDisplayName.invoke(b), category));
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

            List<String> ordinary = new ArrayList<>();
            for (String h : held) if (Layer.ofModifier(h) == null) ordinary.add(h);
            if (ordinary.isEmpty()) {
                addWithModifiers(out, main, held, id, name, source, group);
            } else if (held.size() == 1) {
                String debugKey = KeyMappingHelper.getBoundKeyOf(Minecraft.getInstance().options.keyDebugModifier).getName();
                Layer layer = held.get(0).equals(debugKey) ? Layer.F3 : Layer.hold(held.get(0));
                out.add(new Entry(layer, main, id, name, source, group, null));
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
