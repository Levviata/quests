/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.data;

import java.util.Map;

public final class Json5 {

    private Json5() {}

    public static String print(DataNode node) {
        StringBuilder sb = new StringBuilder();
        write(sb, node, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, DataNode node, int depth) {
        if (node instanceof DataNode.Str v) {
            quote(sb, v.value());
        } else if (node instanceof DataNode.Int v) {
            sb.append(v.value());
        } else if (node instanceof DataNode.Lng v) {
            sb.append(v.value());
        } else if (node instanceof DataNode.Dbl v) {
            double d = v.value();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("NaN and infinity cannot be written");
            }
            sb.append(d);
        } else if (node instanceof DataNode.Bool v) {
            sb.append(v.value());
        } else if (node instanceof DataNode.Lst v) {
            if (v.items().isEmpty()) {
                sb.append("[]");
                return;
            }
            boolean scalars = v.items().stream().noneMatch(n -> n instanceof DataNode.Lst || n instanceof DataNode.Compound);
            sb.append('[');
            for (int i = 0; i < v.items().size(); i++) {
                if (scalars) {
                    sb.append(i > 0 ? ", " : "");
                } else {
                    sb.append(i > 0 ? "," : "").append('\n').append("  ".repeat(depth + 1));
                }
                write(sb, v.items().get(i), depth + 1);
            }
            if (!scalars) {
                sb.append('\n').append("  ".repeat(depth));
            }
            sb.append(']');
        } else if (node instanceof DataNode.Compound c) {
            if (c.entries().isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, DataNode> e : c.entries().entrySet()) {
                sb.append(first ? "" : ",").append('\n').append("  ".repeat(depth + 1));
                first = false;
                quote(sb, e.getKey());
                sb.append(": ");
                write(sb, e.getValue(), depth + 1);
            }
            sb.append('\n').append("  ".repeat(depth)).append('}');
        }
    }

    private static void quote(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
