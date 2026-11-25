package io.github.hiveko.keycaps.model;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import io.github.hiveko.keycaps.Keycaps;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Opens the settings screen of the mod a malilib hotkey belongs to (Litematica, MiniHUD, Tweakeroo, …),
 * on its hotkeys tab where the mod has one, with the keyboard map as the screen to go back to.
 * Everything by name: through Mod Menu's config screen factory when Mod Menu is there, else the mod's
 * own {@code fi.dy.masa.<mod>.gui.GuiConfigs}.
 */
public final class ModScreens {
    private ModScreens() {}

    /** The mod id of a malilib hotkey entry ("litematica" for "litematica:openGuiSettings"), or null. */
    public static String modOf(Entry e) {
        if (e == null || !Collector.isMalilib(e)) return null;
        int colon = e.id().indexOf(':');
        if (colon <= 0) return null;
        String mod = e.id().substring(0, colon);
        if (FabricLoader.getInstance().isModLoaded(mod)) return mod;
        String compact = mod.replace("_", "");
        return FabricLoader.getInstance().isModLoaded(compact) ? compact : null;
    }

    /** The mod's display name ("Litematica"). */
    public static String modName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(c -> c.getMetadata().getName()).orElse(modId);
    }

    /** Opens the mod's settings for this hotkey; false when it has none that can be opened. */
    public static boolean open(Entry e, Screen parent) {
        String mod = modOf(e);
        if (mod == null) return false;
        selectTab(mod, e.id().substring(e.id().indexOf(':') + 1));
        Screen screen = fromModMenu(mod, parent);
        if (screen == null) screen = fromMalilib(mod, parent);
        if (screen == null) return false;
        Minecraft.getInstance().gui.setScreen(screen);
        return true;
    }

    private static Screen fromModMenu(String mod, Screen parent) {
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) return null;
        try {
            Class<?> api = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi");
            Class<?> factoryType = Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory");
            Method getFactory = api.getMethod("getModConfigScreenFactory");
            Method create = factoryType.getMethod("create", Screen.class);
            List<EntrypointContainer<Object>> containers = FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class);
            for (EntrypointContainer<Object> c : containers) {
                if (!c.getProvider().getMetadata().getId().equals(mod)) continue;
                Object entry = c.getEntrypoint();
                if (!api.isInstance(entry)) continue;
                Object factory = getFactory.invoke(entry);
                if (factory == null) continue;
                Object screen = create.invoke(factory, parent);
                if (screen instanceof Screen s) return s;
            }
        } catch (Throwable t) {
            Keycaps.LOGGER.debug("No Mod Menu settings screen for {}", mod, t);
        }
        return null;
    }

    private static Screen fromMalilib(String mod, Screen parent) {
        try {
            Class<?> gui = Class.forName("fi.dy.masa." + mod + ".gui.GuiConfigs");
            Object screen = gui.getConstructor().newInstance();
            gui.getMethod("setParent", Screen.class).invoke(screen, parent);
            return screen instanceof Screen s ? s : null;
        } catch (Throwable t) {
            Keycaps.LOGGER.debug("No malilib settings screen for {}", mod, t);
            return null;
        }
    }

    /**
     * Picks the tab the hotkey is on: Tweakeroo's tweak and disable hotkeys sit with their toggles (TWEAKS,
     * DISABLES); otherwise HOTKEYS (Litematica) or GENERIC_HOTKEYS (MiniHUD, Tweakeroo).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void selectTab(String mod, String hotkey) {
        try {
            Class<?> tabs = Class.forName("fi.dy.masa." + mod + ".gui.GuiConfigs$ConfigGuiTab");
            String name = hotkey.toLowerCase(Locale.ROOT);
            String[] wanted = name.startsWith("tweak") ? new String[]{"TWEAKS", "GENERIC_HOTKEYS", "HOTKEYS"}
                    : name.startsWith("disable") ? new String[]{"DISABLES", "GENERIC_HOTKEYS", "HOTKEYS"}
                    : new String[]{"HOTKEYS", "GENERIC_HOTKEYS"};
            Object tab = null;
            for (String w : wanted) {
                try {
                    tab = Enum.valueOf((Class) tabs, w);
                    break;
                } catch (IllegalArgumentException e) {
                    // not in this mod
                }
            }
            if (tab == null) return;
            try {
                Class.forName("fi.dy.masa." + mod + ".data.DataManager").getMethod("setConfigGuiTab", tabs).invoke(null, tab);
                return;
            } catch (ReflectiveOperationException e) {
                // the tab is a field of the screen in this mod
            }
            Field f = Class.forName("fi.dy.masa." + mod + ".gui.GuiConfigs").getDeclaredField("tab");
            f.setAccessible(true);
            f.set(null, tab);
        } catch (Throwable t) {
            // opens on the tab it remembers
        }
    }
}
