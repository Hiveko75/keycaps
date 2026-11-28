package io.github.hiveko.keycaps.model;

import net.minecraft.network.chat.Component;

import java.util.Set;

/**
 * One action on one key in one layer.
 *
 * @param layer  the layer it is shown in
 * @param key    Minecraft's name of the key it is on ({@code key.keyboard.q}, {@code key.mouse.left})
 * @param id     the action's id: a key mapping name, a Multi Key Bindings action or {@code <mod>:<hotkey>}
 * @param name   the action's name as Controls shows it
 * @param source where it comes from (the key mapping's category or the mod's name)
 * @param group  the colour group ({@link Groups})
 * @param note   extra text for the hover box (for example "Hold H, press C"), or null
 * @param when   when it can fire, for the smart conflict check ({@link Situations})
 * @param side   for the Ctrl, Alt and Shift layers: the exact modifier key the action needs
 *               ({@code key.keyboard.right.alt}), or null when either side works
 */
public record Entry(Layer layer, String key, String id, Component name, Component source, String group, Component note,
                    Set<String> when, String side) {
    public Entry(Layer layer, String key, String id, Component name, Component source, String group, Component note,
                 Set<String> when) {
        this(layer, key, id, name, source, group, note, when, null);
    }

    /** True when this action can fire with the given modifier key held (null: any side). */
    public boolean worksWith(String heldModifier) {
        return side == null || heldModifier == null || side.equals(heldModifier);
    }
}
