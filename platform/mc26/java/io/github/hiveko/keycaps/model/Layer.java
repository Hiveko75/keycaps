package io.github.hiveko.keycaps.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The layers of the map: plain keys; keys pressed together with Ctrl, Alt, Shift or the debug key (F3);
 * and "hold" layers for malilib key sequences that start with an ordinary key (Litematica's B + …,
 * Tweakeroo's Z + …), one per first key. Layers are shared instances, so == compares them.
 */
public final class Layer {
    public static final Layer NORMAL = new Layer("normal", "keycaps.layer.normal", null);
    public static final Layer CTRL = new Layer("ctrl", "keycaps.layer.ctrl", null);
    public static final Layer ALT = new Layer("alt", "keycaps.layer.alt", null);
    public static final Layer SHIFT = new Layer("shift", "keycaps.layer.shift", null);
    public static final Layer F3 = new Layer("f3", "keycaps.layer.f3", null);
    private static final Map<String, Layer> HOLD = new ConcurrentHashMap<>();

    public final String id;
    public final String translationKey;
    /** For a hold layer: Minecraft's name of the key held first ({@code key.keyboard.b}); null otherwise. */
    public final String holdKey;

    private Layer(String id, String translationKey, String holdKey) {
        this.id = id;
        this.translationKey = translationKey;
        this.holdKey = holdKey;
    }

    /** The layer of key sequences that start with this (ordinary) key. */
    public static Layer hold(String keyName) {
        return HOLD.computeIfAbsent(keyName, k -> new Layer("hold:" + k, "keycaps.layer.hold", k));
    }

    public boolean isHold() {
        return holdKey != null;
    }

    /** The layer a held modifier key stands for, or null when the key is not a layer modifier. */
    public static Layer ofModifier(String keyName) {
        return switch (keyName) {
            case "key.keyboard.left.control", "key.keyboard.right.control" -> CTRL;
            case "key.keyboard.left.alt", "key.keyboard.right.alt" -> ALT;
            case "key.keyboard.left.shift", "key.keyboard.right.shift" -> SHIFT;
            default -> null;
        };
    }

    @Override
    public String toString() {
        return id;
    }
}
