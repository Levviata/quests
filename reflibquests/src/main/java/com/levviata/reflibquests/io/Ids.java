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

    /**
     * The id of the reward table a table reward points at. FTB Quests 26.1.2 writes it as a decimal number
     * ({@code table_id: 8688099075117598374}) while the table file is keyed by the same value in hexadecimal; older
     * files and ours use the hexadecimal string. Both spellings give the same hexadecimal id here.
     */
    public static String tableId(com.levviata.reflib.api.data.DataNode.Compound params) {
        com.levviata.reflib.api.data.DataNode n = params.get("table_id");
        if (n instanceof com.levviata.reflib.api.data.DataNode.Lng l) {
            return Long.toHexString(l.value()).toUpperCase(Locale.ROOT);
        }
        if (n instanceof com.levviata.reflib.api.data.DataNode.Int i) {
            return Integer.toHexString(i.value()).toUpperCase(Locale.ROOT);
        }
        if (n instanceof com.levviata.reflib.api.data.DataNode.Str s) {
            String t = s.value().trim();
            if (t.matches("-?[0-9]{17,20}")) { // a decimal written as text
                try {
                    return Long.toHexString(Long.parseLong(t)).toUpperCase(Locale.ROOT);
                } catch (NumberFormatException ex) {
                    return normalize(t);
                }
            }
            return normalize(t);
        }
        return "";
    }

    /** {@code ftbquests:item} style type names: drop the namespace of the mod's own types. */
    public static String stripNamespace(String type) {
        int colon = type.indexOf(':');
        return colon < 0 ? type : type.substring(colon + 1);
    }
}
