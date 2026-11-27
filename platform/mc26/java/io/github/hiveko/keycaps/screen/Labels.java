package io.github.hiveko.keycaps.screen;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.hiveko.keycaps.model.Entry;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Short texts for the key caps: the key's own label and a caption for the action on it. */
final class Labels {
    /** Words that only make a caption longer ("Toggle Freecam" → "Freecam"). */
    private static final List<String> PREFIXES = List.of("toggle ", "open ", "show ", "switch ", "key ", "keybind ");
    private static final List<String> SUFFIXES = List.of(" ki-/bekapcsolása", " be/ki", " megnyitása", " megjelenítése");

    private Labels() {}

    /** The letter or short name printed on a key; follows the player's keyboard layout. */
    static String keyLabel(String keyName) {
        String k = "keycaps.cap." + keyName;
        if (Language.getInstance().has(k)) return Component.translatable(k).getString();
        String s = InputConstants.getKey(keyName).getDisplayName().getString();
        return s.length() <= 4 ? s : s.substring(0, 3) + ".";
    }

    /** The full name of a key, for the hover text. */
    static Component keyName(String keyName) {
        return InputConstants.getKey(keyName).getDisplayName();
    }

    /** The caption of an action: its short label from the language file, or a shortened name. */
    static String caption(Entry e) {
        String k = "keycaps.short." + e.id();
        if (Language.getInstance().has(k)) return Component.translatable(k).getString();
        String s = e.name().getString().trim();
        String lower = s.toLowerCase(Locale.ROOT);
        for (String p : PREFIXES) {
            if (lower.startsWith(p) && s.length() > p.length()) {
                s = s.substring(p.length());
                break;
            }
        }
        for (String x : SUFFIXES) {
            if (s.endsWith(x) && s.length() > x.length()) {
                s = s.substring(0, s.length() - x.length());
                break;
            }
        }
        return s;
    }
}
