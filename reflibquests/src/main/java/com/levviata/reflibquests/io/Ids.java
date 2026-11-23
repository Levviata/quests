/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import java.util.Locale;

public final class Ids {

    private Ids() {}

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
            if (t.matches("-?[0-9]{17,20}")) {
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

    public static String stripNamespace(String type) {
        int colon = type.indexOf(':');
        return colon < 0 ? type : type.substring(colon + 1);
    }
}
