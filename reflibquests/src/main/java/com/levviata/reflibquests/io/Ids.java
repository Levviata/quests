/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import java.util.Locale;

/** Helpers for the id strings used in quest files. */
public final class Ids {

    private Ids() {}

    /**
     * Normalizes an id: trims, drops a leading {@code #}, and upper-cases hexadecimal ids. Empty, {@code -}
     * and null mean "no id" and return the empty string. Non-hex ids are returned unchanged (hand-written
     * files may use readable ids).
     */
    public static String normalize(String id) {
        if (id == null) {
            return "";
        }
        String s = id.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.isEmpty() || s.equals("-")) {
            return "";
        }
        return s.matches("[0-9a-fA-F]{1,16}") ? s.toUpperCase(Locale.ROOT) : s;
    }

    /** {@code ftbquests:item} style type names: drop the namespace of the mod's own types. */
    public static String stripNamespace(String type) {
        int colon = type.indexOf(':');
        return colon < 0 ? type : type.substring(colon + 1);
    }
}
