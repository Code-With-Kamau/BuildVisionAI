package com.buildvisionai.data.mapper;

import java.util.List;

/**
 * Enum &lt;-&gt; text helpers for the mappers. Enums are stored by name (never by ordinal) so reordering
 * or inserting a constant cannot change what is already on disk. Reading an unknown name does not
 * crash the screen: it falls back to a stated default and records a warning the UI can show.
 */
final class Codes {
    private Codes() {}

    static String of(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback, List<String> warnings, String what) {
        if (value != null) {
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException ignored) {
                // fall through to the fallback
            }
        }
        if (warnings != null) {
            warnings.add(what + ": unknown " + type.getSimpleName() + " \"" + value + "\", using " + fallback.name() + ".");
        }
        return fallback;
    }

    static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
