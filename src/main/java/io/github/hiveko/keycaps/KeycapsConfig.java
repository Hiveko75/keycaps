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

/**
 * {@code config/keycaps.json}: the keyboard shape (auto, ansi or iso) and the size of the action names on
 * the keys, in percent of normal text (50 by default).
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

    private KeycapsConfig() {}

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("keycaps.json");
    }

    public static Shape shape() {
        return shape;
    }

    /** The shape to draw: the configured one, or a guess from the computer's region. */
    public static Shape effectiveShape() {
        if (shape != Shape.AUTO) return shape;
        String country = Locale.getDefault().getCountry();
        return country.isEmpty() || ANSI_COUNTRIES.contains(country.toUpperCase(Locale.ROOT)) ? Shape.ANSI : Shape.ISO;
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
        } catch (Exception e) {
            Keycaps.LOGGER.warn("Could not read {}, using the defaults", f, e);
        }
    }

    public static void save() {
        JsonObject o = new JsonObject();
        o.addProperty("keyboardShape", shape.name().toLowerCase(Locale.ROOT));
        o.addProperty("captionSize", captionSize);
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
