package io.github.hiveko.keycaps.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.hiveko.keycaps.Keycaps;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The colour groups and the rules that put each action into one: {@code groups.json} in the jar, replaced
 * by {@code config/keycaps/groups.json} when that file exists. A rule's pattern is matched against
 * "{@code <action id> <category or mod>}" in lower case; the first matching rule wins, and actions no
 * rule matches go to "other".
 */
public final class Groups {
    /** A colour group; {@code row} is its legend row (1, or 2 for mods with their own colour). */
    public record Group(String id, int color, int row) {}

    private record Rule(String group, Pattern pattern) {}

    private static final Map<String, Group> GROUPS = new LinkedHashMap<>();
    private static final List<Rule> RULES = new ArrayList<>();
    public static final String OTHER = "other";

    private Groups() {}

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("keycaps").resolve("groups.json");
    }

    /** (Re)reads the groups; called every time the screen opens, so edits show without a restart. */
    public static void load() {
        GROUPS.clear();
        RULES.clear();
        JsonObject root = null;
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                root = new Gson().fromJson(r, JsonObject.class);
            } catch (Exception e) {
                Keycaps.LOGGER.warn("Could not read {}, using the built-in groups", f, e);
            }
        }
        if (root == null) {
            try (InputStream in = Groups.class.getResourceAsStream("/assets/keycaps/groups.json")) {
                if (in != null) root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            } catch (Exception e) {
                Keycaps.LOGGER.error("Could not read the built-in groups", e);
            }
        }
        if (root != null) {
            JsonArray groups = root.getAsJsonArray("groups");
            if (groups != null) for (JsonElement g : groups) {
                JsonObject o = g.getAsJsonObject();
                String id = o.get("id").getAsString();
                int row = o.has("row") ? o.get("row").getAsInt() : 1;
                GROUPS.put(id, new Group(id, parseColor(o.get("color").getAsString()), row));
            }
            JsonArray rules = root.getAsJsonArray("rules");
            if (rules != null) for (JsonElement r : rules) {
                JsonObject o = r.getAsJsonObject();
                try {
                    RULES.add(new Rule(o.get("group").getAsString(), Pattern.compile(o.get("match").getAsString())));
                } catch (Exception e) {
                    Keycaps.LOGGER.warn("Skipping a group rule that is not a valid pattern: {}", o, e);
                }
            }
        }
        GROUPS.putIfAbsent(OTHER, new Group(OTHER, 0xFF5D6B7A, 1));
    }

    public static String groupOf(String id, String context) {
        String text = (id + " " + context).toLowerCase(Locale.ROOT);
        for (Rule r : RULES) {
            if (r.pattern.matcher(text).find() && GROUPS.containsKey(r.group)) return r.group;
        }
        return OTHER;
    }

    public static int color(String group) {
        Group g = GROUPS.get(group);
        return g != null ? g.color : 0xFF5D6B7A;
    }

    public static List<Group> all() {
        return List.copyOf(GROUPS.values());
    }

    private static int parseColor(String s) {
        String hex = s.startsWith("#") ? s.substring(1) : s;
        int v = (int) Long.parseLong(hex, 16);
        return hex.length() <= 6 ? 0xFF000000 | v : v;
    }
}
