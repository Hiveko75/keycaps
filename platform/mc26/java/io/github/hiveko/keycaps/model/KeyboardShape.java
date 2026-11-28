package io.github.hiveko.keycaps.model;

import io.github.hiveko.keycaps.Compat;

import java.util.ArrayList;
import java.util.List;

/**
 * A full-size keyboard (ANSI or ISO) in key units, the numeric keypad optional. The mouse sits above the keypad
 * (left, middle and wheel as one double button, right; a frame around them for mouse movement); without the
 * keypad its three buttons go to the empty spot under Delete / End / Page Down. Keys are named by Minecraft's key names
 * ({@code key.keyboard.q}), which stand for physical positions; the letter printed on each key comes from
 * the player's keyboard layout.
 */
public final class KeyboardShape {
    /** One rectangle of a key; ISO Enter is two rectangles with the same name. */
    public record Cap(String key, float x, float y, float w, float h) {}

    /** Width without the numeric keypad (tenkeyless) and with it. */
    public static final float WIDTH = 18.25f;
    public static final float WIDTH_NUMPAD = 22.5f;
    public static final float HEIGHT = 6.5f;

    /**
     * Not keys: the mouse wheel (shown joined to the middle button) and mouse movement (a frame around the mouse
     * buttons). Their actions come from {@code situations.json} ("fixed" entries), as no key binding names them.
     */
    public static final String WHEEL = "keycaps.mouse.wheel";
    public static final String MOVE = "keycaps.mouse.move";

    public static boolean isMouseExtra(String key) {
        return WHEEL.equals(key) || MOVE.equals(key);
    }

    /** The extra ISO key next to left Shift (SDL "non-US backslash"); Minecraft 26.3 names it world.1. */
    public static final String ISO_EXTRA = Compat.ISO_EXTRA;

    private KeyboardShape() {}

    public static float width(boolean numpad) {
        return numpad ? WIDTH_NUMPAD : WIDTH;
    }

    public static List<Cap> caps(boolean iso, boolean numpad) {
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
        if (!numpad) {
            // no room above the keypad: the buttons under Delete / End / Page Down, wheel and movement "elsewhere"
            c.add(new Cap("key.mouse.left", 15.25f, y, 1, 1));
            c.add(new Cap("key.mouse.middle", 16.25f, y, 1, 1));
            c.add(new Cap("key.mouse.right", 17.25f, y, 1, 1));
        }
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
        if (numpad) numpad(c);
        return c;
    }

    /** The numeric keypad, right of the arrow keys, with the mouse above it in the function row. */
    private static void numpad(List<Cap> c) {
        float x = 18.5f;
        // the mouse: left, middle + wheel (one double button), right; the movement frame goes around them
        c.add(new Cap("key.mouse.left", x, 0, 1, 1));
        c.add(new Cap("key.mouse.middle", x + 1, 0, 1, 1));
        c.add(new Cap(WHEEL, x + 2, 0, 1, 1));
        c.add(new Cap("key.mouse.right", x + 3, 0, 1, 1));
        add(c, "num.lock", x, 1.5f, 1);
        add(c, "keypad.divide", x + 1, 1.5f, 1);
        add(c, "keypad.multiply", x + 2, 1.5f, 1);
        add(c, "keypad.subtract", x + 3, 1.5f, 1);
        String[][] rows = {{"7", "8", "9"}, {"4", "5", "6"}, {"1", "2", "3"}};
        for (int r = 0; r < rows.length; r++) {
            for (int i = 0; i < 3; i++) add(c, "keypad." + rows[r][i], x + i, 2.5f + r, 1);
        }
        c.add(new Cap("key.keyboard.keypad.add", x + 3, 2.5f, 1, 2));
        c.add(new Cap("key.keyboard.keypad.enter", x + 3, 4.5f, 1, 2));
        add(c, "keypad.0", x, 5.5f, 2);
        add(c, "keypad.period", x + 2, 5.5f, 1);
    }

    private static void add(List<Cap> c, String name, float x, float y, float w) {
        c.add(new Cap("key.keyboard." + name, x, y, w, 1));
    }
}
