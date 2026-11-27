package io.github.hiveko.keycaps.model;

import io.github.hiveko.keycaps.Keycaps;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The options an action needs (a situation rule's {@code "needs"}), read live: an action whose option is
 * switched off does nothing, so it is never part of a conflict. Tweakeroo's flexible placement keys only work
 * while the Flexible Block Placement tweak is on, Litematica's tool keys only while its tool item is enabled,
 * and so on.
 *
 * <p>An option is named {@code <class>#<static field>}: a malilib option (anything with
 * {@code IConfigBoolean.getBooleanValue()}, such as a Tweakeroo tweak or a {@code ConfigBoolean}) or a plain
 * {@code boolean} field, also inside the object in a static field ({@code ScrollWalk#config.modEnabled}).
 * Classes are found by name, so the mods stay optional; an option that can't be read
 * counts as on, so the action is treated as before.
 */
public final class Needs {
    private static final Map<String, Optional<Object>> OPTIONS = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static Method getBooleanValue, getPrettyName;
    private static boolean malilibTried;

    private Needs() {}

    /** The names (as the mod shows them) of the rule's options that are switched off now; empty when all are on. */
    public static List<String> switchedOff(Situations.Rule rule) {
        if (rule == null || rule.needs().isEmpty()) return List.of();
        List<String> off = new ArrayList<>();
        for (String ref : rule.needs()) {
            Optional<Object> option = OPTIONS.computeIfAbsent(ref, Needs::find);
            if (option.isEmpty()) continue;
            Boolean on = value(ref, option.get());
            if (on != null && !on) off.add(name(ref, option.get()));
        }
        return off;
    }

    private static Optional<Object> find(String ref) {
        int hash = ref.indexOf('#');
        if (hash <= 0 || hash == ref.length() - 1) {
            warnOnce(ref, "is not <class>#<field>", null);
            return Optional.empty();
        }
        String className = ref.substring(0, hash);
        String[] path = ref.substring(hash + 1).split("\\.");
        try {
            Class<?> c = Class.forName(className, true, Needs.class.getClassLoader());
            Field f = field(c, path[0]);
            if (!Modifier.isStatic(f.getModifiers())) {
                warnOnce(ref, "is not a static field", null);
                return Optional.empty();
            }
            if (path.length == 1) {
                if (f.getType() == boolean.class) return Optional.of(new Path(null, f)); // read each time
                return Optional.ofNullable(f.get(null));
            }
            // a field of the object in a static field (Scroll Walk: ScrollWalk.config.modEnabled), read each time
            return Optional.of(new Path(f, null, java.util.Arrays.copyOfRange(path, 1, path.length)));
        } catch (ClassNotFoundException e) {
            return Optional.empty(); // the mod is not installed
        } catch (Throwable t) {
            warnOnce(ref, "could not be read", t);
            return Optional.empty();
        }
    }

    /** A static field, optionally followed by fields of the object in it ({@code config.modEnabled}). */
    private record Path(Field root, Field plain, String... rest) {
        Path(Field root, Field plain) {
            this(root, plain, new String[0]);
        }

        Object read() throws ReflectiveOperationException {
            if (plain != null) return plain.getBoolean(null);
            Object o = root.get(null);
            for (String name : rest) {
                if (o == null) return null;
                o = field(o.getClass(), name).get(o);
            }
            return o;
        }
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // look in the superclass
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** True / false for the option, null when it can't be read. */
    private static Boolean value(String ref, Object option) {
        try {
            if (option instanceof Path p) option = p.read();
            if (option == null) return null;
            if (option instanceof Boolean b) return b;
            Method m = malilib(true);
            if (m != null && m.getDeclaringClass().isInstance(option)) return (Boolean) m.invoke(option);
            warnOnce(ref, "is not an on/off option", null);
        } catch (Throwable t) {
            warnOnce(ref, "could not be read", t);
        }
        return null;
    }

    /** The option's name as its mod shows it (malilib's pretty name), else the field's name. */
    private static String name(String ref, Object option) {
        try {
            Method m = malilib(false);
            if (m != null && m.getDeclaringClass().isInstance(option)) {
                Object pretty = m.invoke(option);
                if (pretty instanceof String s && !s.isBlank()) return s;
            }
        } catch (Throwable ignored) {
            // fall back to the field name
        }
        String field = ref.substring(ref.lastIndexOf('.') > ref.indexOf('#') ? ref.lastIndexOf('.') + 1 : ref.indexOf('#') + 1);
        if (field.equals(field.toUpperCase(java.util.Locale.ROOT))) return field; // CONSTANT_NAME
        // modEnabled -> "Mod enabled"
        String words = field.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(java.util.Locale.ROOT);
        return words.isEmpty() ? field : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** malilib's IConfigBoolean.getBooleanValue (value = true) or IConfigBase.getPrettyName, or null. */
    private static Method malilib(boolean value) {
        if (!malilibTried) {
            malilibTried = true;
            try {
                getBooleanValue = Class.forName("fi.dy.masa.malilib.config.IConfigBoolean").getMethod("getBooleanValue");
                getPrettyName = Class.forName("fi.dy.masa.malilib.config.IConfigBase").getMethod("getPrettyName");
            } catch (Throwable t) {
                // no malilib: only plain boolean fields can be read
            }
        }
        return value ? getBooleanValue : getPrettyName;
    }

    private static void warnOnce(String ref, String what, Throwable t) {
        if (!WARNED.add(ref)) return;
        if (t == null) Keycaps.LOGGER.warn("Keycaps: the option {} in situations.json {}; ignoring it", ref, what);
        else Keycaps.LOGGER.warn("Keycaps: the option {} in situations.json {}; ignoring it", ref, what, t);
    }
}
