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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * When an action can fire, for the smart conflict check: two actions on one key only clash when they share a
 * situation. {@code situations.json} in the jar, replaced by {@code config/keycaps/situations.json} when that
 * file exists. A rule's pattern is matched like a colour rule ("{@code <action id> <category or mod>}" in
 * lower case, first match wins).
 * <ul>
 *   <li>{@code world}: playing, no screen open (every key binding unless a rule or malilib says otherwise);</li>
 *   <li>{@code menu}: in the inventory and other container screens;</li>
 *   <li>{@code chat}: in the chat screen;</li>
 *   <li>{@code passive}: only changes what is shown while held (Jade's details), never a clash;</li>
 *   <li>any other word is a situation of its own (for example {@code replay} for Flashback's editor).</li>
 * </ul>
 * A rule can also say the mod adds a modifier itself ({@code "adds": "ctrl"}: the emoji picker is Ctrl + E
 * although its binding says E), so the action is shown on that layer, and which options the action needs
 * ({@code "needs"}: Tweakeroo's flexible placement keys do nothing while Flexible Block Placement is off).
 * While one of those is off the action is {@link #OFF} and never a clash ({@link Needs}).
 */
public final class Situations {
    public static final String WORLD = "world", MENU = "menu", CHAT = "chat", PASSIVE = "passive";
    /** The action needs an option that is switched off now, so it does nothing (from a rule's "needs"). */
    public static final String OFF = "off";
    public static final Set<String> IN_WORLD = Set.of(WORLD);
    public static final Set<String> WORLD_AND_MENU = Set.of(WORLD, MENU);
    public static final Set<String> SWITCHED_OFF = Set.of(OFF);

    /**
     * What a rule says about the matching actions: their situations (null = keep), a modifier the mod adds,
     * and the options they need ({@code <class>#<static field>}, empty = none).
     */
    public record Rule(Pattern pattern, Set<String> when, String adds, List<String> needs) {}

    private static final List<Rule> RULES = new ArrayList<>();

    private Situations() {}

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("keycaps").resolve("situations.json");
    }

    /** (Re)reads the rules; called every time the screen opens. */
    public static void load() {
        RULES.clear();
        JsonObject root = null;
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                root = new Gson().fromJson(r, JsonObject.class);
            } catch (Exception e) {
                Keycaps.LOGGER.warn("Could not read {}, using the built-in situations", f, e);
            }
        }
        if (root == null) {
            try (InputStream in = Situations.class.getResourceAsStream("/assets/keycaps/situations.json")) {
                if (in != null) root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            } catch (Exception e) {
                Keycaps.LOGGER.error("Could not read the built-in situations", e);
            }
        }
        if (root == null || !root.has("rules")) return;
        for (JsonElement r : root.getAsJsonArray("rules")) {
            try {
                JsonObject o = r.getAsJsonObject();
                Set<String> when = null;
                if (o.has("when")) {
                    when = new LinkedHashSet<>();
                    JsonElement w = o.get("when");
                    JsonArray list = w.isJsonArray() ? w.getAsJsonArray() : new JsonArray();
                    if (!w.isJsonArray()) list.add(w);
                    for (JsonElement s : list) when.add(s.getAsString().toLowerCase(Locale.ROOT));
                    when = Collections.unmodifiableSet(when);
                }
                String adds = o.has("adds") ? o.get("adds").getAsString().toLowerCase(Locale.ROOT) : null;
                List<String> needs = new ArrayList<>();
                if (o.has("needs")) {
                    JsonElement n = o.get("needs");
                    if (n.isJsonArray()) for (JsonElement x : n.getAsJsonArray()) needs.add(x.getAsString());
                    else needs.add(n.getAsString());
                }
                RULES.add(new Rule(Pattern.compile(o.get("match").getAsString()), when, adds, List.copyOf(needs)));
            } catch (Exception e) {
                Keycaps.LOGGER.warn("Skipping a situation rule that is not valid: {}", r, e);
            }
        }
    }

    /** The first rule for this action, or null. */
    public static Rule ruleOf(String id, String context) {
        String text = (id + " " + context).toLowerCase(Locale.ROOT);
        for (Rule r : RULES) if (r.pattern.matcher(text).find()) return r;
        return null;
    }

    /** The action's situations: the rule's, else the default given by where the action comes from. */
    public static Set<String> of(Rule rule, Set<String> fallback) {
        return rule != null && rule.when() != null ? rule.when() : fallback;
    }

    /** Minecraft's name of the modifier key a rule adds ("ctrl" → left Control), or null. */
    public static String addedModifier(Rule rule) {
        if (rule == null || rule.adds() == null) return null;
        return switch (rule.adds()) {
            case "ctrl", "control" -> "key.keyboard.left.control";
            case "alt" -> "key.keyboard.left.alt";
            case "shift" -> "key.keyboard.left.shift";
            default -> null;
        };
    }

    /**
     * True when the two actions can fire at the same time (they share a situation; passive and switched-off
     * actions never clash).
     */
    public static boolean overlap(Set<String> a, Set<String> b) {
        if (a.contains(PASSIVE) || b.contains(PASSIVE) || a.contains(OFF) || b.contains(OFF)) return false;
        for (String s : a) if (b.contains(s)) return true;
        return false;
    }
}
