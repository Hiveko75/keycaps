package io.github.hiveko.keycaps.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A tenkeyless keyboard (ANSI or ISO) in key units, with the three mouse buttons in the empty spot under
 * Delete / End / Page Down. Keys are named by Minecraft's key names ({@code key.keyboard.q}), which stand
 * for physical positions; the letter printed on each key comes from the player's keyboard layout.
 */
public final class KeyboardShape {
    /** One rectangle of a key; ISO Enter is two rectangles with the same name. */
    public record Cap(String key, float x, float y, float w, float h) {}

    public static final float WIDTH = 18.25f;
    public static final float HEIGHT = 6.5f;

    /** The extra ISO key next to left Shift (SDL "non-US backslash"); Minecraft 26.3 names it world.1. */
    public static final String ISO_EXTRA = "key.keyboard.world.1";

    private KeyboardShape() {}

    public static List<Cap> caps(boolean iso) {
        List<Cap> c = new ArrayList<>();
        // function row
        add(c, "escape", 0, 0, 1);
        for (int i = 1; i <= 4; i++) add(c, "f" + i, 1 + i, 0, 1);
        for (int i = 5; i <= 8; i++) add(c, "f" + i, 1.5f + i, 0, 1);
        for (int i = 9; i <= 12; i++) add(c, "f" + i, 2f + i, 0, 1);
        add(c, "print.screen", 15.25f, 0, 1);
        add(c, "scroll.lock", 16.25f, 0, 1);
        add(c, "pause", 17.25f, 0, 1);
        // number row
        float y = 1.5f;
        add(c, "grave.accent", 0, y, 1);
        String[] num = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "minus", "equal"};
        for (int i = 0; i < num.length; i++) add(c, num[i], 1 + i, y, 1);
        add(c, "backspace", 13, y, 2);
        add(c, "insert", 15.25f, y, 1);
        add(c, "home", 16.25f, y, 1);
        add(c, "page.up", 17.25f, y, 1);
        // top letter row
        y = 2.5f;
        add(c, "tab", 0, y, 1.5f);
        String[] top = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "left.bracket", "right.bracket"};
        for (int i = 0; i < top.length; i++) add(c, top[i], 1.5f + i, y, 1);
        if (iso) {
            c.add(new Cap("key.keyboard.enter", 13.5f, y, 1.5f, 1));
        } else {
            add(c, "backslash", 13.5f, y, 1.5f);
        }
        add(c, "delete", 15.25f, y, 1);
        add(c, "end", 16.25f, y, 1);
        add(c, "page.down", 17.25f, y, 1);
        // home row
        y = 3.5f;
        add(c, "caps.lock", 0, y, 1.75f);
        String[] home = {"a", "s", "d", "f", "g", "h", "j", "k", "l", "semicolon", "apostrophe"};
        for (int i = 0; i < home.length; i++) add(c, home[i], 1.75f + i, y, 1);
        if (iso) {
            add(c, "backslash", 12.75f, y, 1); // the ISO # key reports as backslash
            c.add(new Cap("key.keyboard.enter", 13.75f, y, 1.25f, 1));
        } else {
            add(c, "enter", 12.75f, y, 2.25f);
        }
        c.add(new Cap("key.mouse.left", 15.25f, y, 1, 1));
        c.add(new Cap("key.mouse.middle", 16.25f, y, 1, 1));
        c.add(new Cap("key.mouse.right", 17.25f, y, 1, 1));
        // bottom letter row
        y = 4.5f;
        if (iso) {
            add(c, "left.shift", 0, y, 1.25f);
            c.add(new Cap(ISO_EXTRA, 1.25f, y, 1, 1));
        } else {
            add(c, "left.shift", 0, y, 2.25f);
        }
        String[] bottom = {"z", "x", "c", "v", "b", "n", "m", "comma", "period", "slash"};
        for (int i = 0; i < bottom.length; i++) add(c, bottom[i], 2.25f + i, y, 1);
        add(c, "right.shift", 12.25f, y, 2.75f);
        add(c, "up", 16.25f, y, 1);
        // space row
        y = 5.5f;
        add(c, "left.control", 0, y, 1.25f);
        add(c, "left.win", 1.25f, y, 1.25f);
        add(c, "left.alt", 2.5f, y, 1.25f);
        add(c, "space", 3.75f, y, 6.25f);
        add(c, "right.alt", 10f, y, 1.25f);
        add(c, "right.win", 11.25f, y, 1.25f);
        add(c, "menu", 12.5f, y, 1.25f);
        add(c, "right.control", 13.75f, y, 1.25f);
        add(c, "left", 15.25f, y, 1);
        add(c, "down", 16.25f, y, 1);
        add(c, "right", 17.25f, y, 1);
        return c;
    }

    private static void add(List<Cap> c, String name, float x, float y, float w) {
        c.add(new Cap("key.keyboard." + name, x, y, w, 1));
    }
}
