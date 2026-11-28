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
     * The shape to draw: the configured one, or a guess. The country comes from the game's language when it
     * is not American English (hu_hu → HU), else from the computer's regional format (Windows: Region), else
     * from its display language; Windows in English with a Hungarian keyboard still gets ISO that way.
     */
    public static Shape effectiveShape() {
        if (shape != Shape.AUTO) return shape;
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

    /** "hu_hu" → "HU"; "" for American English (also the game's default, so it says nothing). */
    private static String countryOf(String language) {
        if (language == null) return "";
        int i = language.indexOf('_');
        if (i < 0 || language.equalsIgnoreCase("en_us")) return "";
        return language.substring(i + 1).toUpperCase(Locale.ROOT);
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
