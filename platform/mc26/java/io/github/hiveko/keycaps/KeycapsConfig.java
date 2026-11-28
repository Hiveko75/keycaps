package io.github.hiveko.keycaps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@code config/keycaps.json}: the keyboard shape (auto, ansi or iso), the size of the action names on the
 * keys in percent of normal text (50 by default), the smart conflict check (on by default) and the pairs of
 * actions marked "not a conflict" (right-click on a key), each as {@code layer|key|action|action}.
 */
public final class KeycapsConfig {
    public enum Shape { AUTO, ANSI, ISO }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Countries whose keyboards are usually ANSI-shaped (one-row Enter, no extra key next to left Shift). */
    private static final Set<String> ANSI_COUNTRIES = Set.of(
            "US", "CA", "AU", "NZ", "PH", "IN", "CN", "TW", "HK", "KR", "JP", "TH", "SG", "MY", "ID", "VN", "IL");

    public static final int DEFAULT_CAPTION_SIZE = 50;

    private static Shape shape = Shape.AUTO;
    private static int captionSize = DEFAULT_CAPTION_SIZE;
    private static boolean smartConflicts = true;
    private static final Set<String> NOT_CONFLICTS = new TreeSet<>();

    private KeycapsConfig() {}

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("keycaps.json");
    }

    public static Shape shape() {
        return shape;
    }

    /**
     * The shape to draw: the configured one, or a guess. On Windows the keyboard layout in use decides
     * (Hungarian → ISO, US → ANSI). Otherwise the country comes from the game's language when it is not
     * American English (hu_hu → HU), else from the computer's regional format, else from its display language.
     */
    public static Shape effectiveShape() {
        if (shape != Shape.AUTO) return shape;
        Shape fromLayout = windowsLayoutShape();
        if (fromLayout != null) return fromLayout;
        String country = countryOf(gameLanguage());
        if (country.isEmpty()) country = Locale.getDefault(Locale.Category.FORMAT).getCountry();
        if (country.isEmpty()) country = Locale.getDefault().getCountry();
        return country.isEmpty() || ANSI_COUNTRIES.contains(country.toUpperCase(Locale.ROOT)) ? Shape.ANSI : Shape.ISO;
    }

    private static String gameLanguage() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            return mc == null || mc.getLanguageManager() == null ? "" : mc.getLanguageManager().getSelected();
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
    }

    /**
     * "hu_hu" → "HU"; "" for American English (also the game's default, so it says nothing). Resource pack
     * variants of a language ("en_us-showtext" from Colourful Containers) count as that language.
     */
    private static String countryOf(String language) {
        if (language == null) return "";
        int dash = language.indexOf('-');
        if (dash > 0) language = language.substring(0, dash);
        int i = language.indexOf('_');
        if (i < 0 || language.equalsIgnoreCase("en_us")) return "";
        return language.substring(i + 1).toUpperCase(Locale.ROOT);
    }

    /** Windows keyboard layouts (language ids) whose keyboards are ANSI-shaped; every other one is ISO. */
    private static final Set<Integer> ANSI_LAYOUTS = Set.of(
            0x0409, 0x1009, 0x0C09, 0x1409, 0x4009, 0x3409, 0x4809, 0x4409); // English: US, CA, AU, NZ, IN, PH, SG, MY
    /** Primary languages whose keyboards are ANSI-shaped (Chinese, Japanese, Korean, Thai, Hebrew, Vietnamese …). */
    private static final Set<Integer> ANSI_LANGUAGES = Set.of(0x04, 0x11, 0x12, 0x1E, 0x0D, 0x2A, 0x21, 0x3E);

    /**
     * The shape from the Windows keyboard layout in use (GetKeyboardLayout through JNA, which Minecraft ships),
     * or null when it can't be read (not Windows, no JNA). The layout id is the high word of the HKL unless it is
     * a special id (0xF…), then the low word (the input language).
     */
    private static Shape windowsLayoutShape() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return null;
        try {
            Class<?> user32 = Class.forName("com.sun.jna.platform.win32.User32");
            Object instance = user32.getField("INSTANCE").get(null);
            Object hkl = user32.getMethod("GetKeyboardLayout", int.class).invoke(instance, 0);
            if (hkl == null) return null;
            Object pointer = hkl.getClass().getMethod("getPointer").invoke(hkl);
            Class<?> pointerClass = Class.forName("com.sun.jna.Pointer");
            long value = (long) pointerClass.getMethod("nativeValue", pointerClass).invoke(null, pointer);
            int layout = (int) ((value >>> 16) & 0xFFFF);
            if (layout == 0 || (layout & 0xF000) == 0xF000) layout = (int) (value & 0xFFFF);
            if (layout == 0) return null;
            if (ANSI_LAYOUTS.contains(layout) || ANSI_LANGUAGES.contains(layout & 0x3FF)) return Shape.ANSI;
            return Shape.ISO;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void setShape(Shape s) {
        shape = s;
        save();
    }

    /** Size of the action names on the keys, in percent of normal text (10 to 100). */
    public static int captionSize() {
        return captionSize;
    }

    public static void setCaptionSize(int percent) {
        captionSize = Math.max(10, Math.min(100, percent));
        save();
    }

    /** Smart conflict check: shared keys only count when both actions can fire at the same time. */
    public static boolean smartConflicts() {
        return smartConflicts;
    }

    public static void setSmartConflicts(boolean on) {
        smartConflicts = on;
        save();
    }

    /** The stored form of a pair: {@code layer|key|a|b}, the two action ids sorted. */
    public static String pair(String layer, String key, String a, String b) {
        return a.compareTo(b) <= 0 ? layer + "|" + key + "|" + a + "|" + b : layer + "|" + key + "|" + b + "|" + a;
    }

    public static boolean notConflict(String pair) {
        return NOT_CONFLICTS.contains(pair);
    }

    public static void setNotConflict(String pair, boolean mark) {
        if (mark ? NOT_CONFLICTS.add(pair) : NOT_CONFLICTS.remove(pair)) save();
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject o = GSON.fromJson(r, JsonObject.class);
            if (o != null && o.has("keyboardShape")) {
                shape = Shape.valueOf(o.get("keyboardShape").getAsString().toUpperCase(Locale.ROOT));
            }
            if (o != null && o.has("captionSize")) {
                captionSize = Math.max(10, Math.min(100, o.get("captionSize").getAsInt()));
            }
            if (o != null && o.has("smartConflicts")) smartConflicts = o.get("smartConflicts").getAsBoolean();
            NOT_CONFLICTS.clear();
            if (o != null && o.has("notConflicts")) {
                for (var e : o.getAsJsonArray("notConflicts")) NOT_CONFLICTS.add(e.getAsString());
            }
        } catch (Exception e) {
            Keycaps.LOGGER.warn("Could not read {}, using the defaults", f, e);
        }
    }

    public static void save() {
        JsonObject o = new JsonObject();
        o.addProperty("keyboardShape", shape.name().toLowerCase(Locale.ROOT));
        o.addProperty("captionSize", captionSize);
        o.addProperty("smartConflicts", smartConflicts);
        com.google.gson.JsonArray pairs = new com.google.gson.JsonArray();
        for (String p : NOT_CONFLICTS) pairs.add(p);
        o.add("notConflicts", pairs);
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(o, w);
            }
        } catch (Exception e) {
            Keycaps.LOGGER.warn("Could not write {}", file(), e);
        }
    }
}
