package io.github.hiveko.keycaps.model;

/** The layers of the map: plain keys, and keys pressed together with Ctrl, Alt, Shift or the debug key (F3). */
public enum Layer {
    NORMAL("keycaps.layer.normal"),
    CTRL("keycaps.layer.ctrl"),
    ALT("keycaps.layer.alt"),
    SHIFT("keycaps.layer.shift"),
    F3("keycaps.layer.f3");

    public final String translationKey;

    Layer(String translationKey) {
        this.translationKey = translationKey;
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
}
