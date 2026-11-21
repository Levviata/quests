/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reader and writer for reflib's own human-editable text format.
 *
 * <pre>{@code
 * {
 *   title: "First steps"        // line comment (also '#')
 *   size: 3                     // int; 3L = long; 1.5 or 1.5d = double
 *   hidden: false
 *   tags: ["a", "b"]
 *   nested: { x: 1, y: 2 }      // commas are optional everywhere
 * }
 * }</pre>
 *
 * Strings must be quoted (single or double quotes; escapes: backslash followed by one of
 * {@code " ' \ n r t}, or by {@code u} and four hex digits);
 * keys may be bare ({@code [A-Za-z0-9_.+-]+}) or quoted. This is an independent format. When reading it also
 * accepts, so that JSON5 and older SNBT-style files load: block comments, {@code null} (the entry is treated
 * as absent), hexadecimal integers, and typed suffixes ({@code b} and {@code s} for small integers, {@code f}
 * for a float).
 */
public final class Snbt {

    private Snbt() {}

    // ------------------------------------------------------------------ parsing

    public static DataNode.Compound parse(String text) {
        return parse(text, false);
    }

    /**
     * Like {@link #parse} but a key that appears twice is not an error: the last value wins. Real-world files
     * written by other programs sometimes contain such duplicates.
     */
    public static DataNode.Compound parseLenient(String text) {
        return parse(text, true);
    }

    private static DataNode.Compound parse(String text, boolean lenient) {
        Parser p = new Parser(text);
        p.lenient = lenient;
        p.skipSpace();
        DataNode.Compound result = p.readCompound();
        p.skipSpace();
        if (!p.atEnd()) {
            throw p.error("Unexpected trailing content");
        }
        return result;
    }

    private static final class Parser {
        private final String s;
        private int pos;
        private boolean lenient;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        SnbtException error(String message) {
            int line = 1;
            int col = 1;
            for (int i = 0; i < Math.min(pos, s.length()); i++) {
                if (s.charAt(i) == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
            }
            return new SnbtException(message, line, col);
        }

        void skipSpace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isWhitespace(c)) {
                    pos++;
                } else if (c == '#' || (c == '/' && pos + 1 < s.length() && s.charAt(pos + 1) == '/')) {
                    while (pos < s.length() && s.charAt(pos) != '\n') {
                        pos++;
                    }
                } else if (c == '/' && pos + 1 < s.length() && s.charAt(pos + 1) == '*') {
                    int end = s.indexOf("*/", pos + 2);
                    if (end < 0) {
                        throw error("Unterminated block comment");
                    }
                    pos = end + 2;
                } else {
                    break;
                }
            }
        }

        void expect(char c) {
            if (atEnd() || s.charAt(pos) != c) {
                throw error("Expected '" + c + "'" + (atEnd() ? " but reached end of input" : " but found '" + s.charAt(pos) + "'"));
            }
            pos++;
        }

        void skipComma() {
            skipSpace();
            if (!atEnd() && s.charAt(pos) == ',') {
                pos++;
            }
        }

        DataNode.Compound readCompound() {
            expect('{');
            Map<String, DataNode> map = new LinkedHashMap<>();
            while (true) {
                skipSpace();
                if (atEnd()) {
                    throw error("Unterminated compound, expected '}'");
                }
                if (s.charAt(pos) == '}') {
                    pos++;
                    return new DataNode.Compound(map);
                }
                String key = readKey();
                skipSpace();
                expect(':');
                skipSpace();
                DataNode value = readValue();
                if (value == null) {
                    if (map.containsKey(key) && !lenient) {
                        throw error("Duplicate key '" + key + "'");
                    }
                    skipComma();
                    continue; // null means "absent"
                }
                if (map.put(key, value) != null && !lenient) {
                    throw error("Duplicate key '" + key + "'");
                }
                skipComma();
            }
        }

        DataNode.Lst readList() {
            expect('[');
            skipTypedArrayPrefix();
            List<DataNode> items = new ArrayList<>();
            while (true) {
                skipSpace();
                if (atEnd()) {
                    throw error("Unterminated list, expected ']'");
                }
                if (s.charAt(pos) == ']') {
                    pos++;
                    return new DataNode.Lst(items);
                }
                DataNode item = readValue();
                if (item != null) {
                    items.add(item);
                }
                skipComma();
            }
        }

        /** SNBT typed arrays start with a type letter and a semicolon: [I; 1, 2], [L; 1L], [B; 0b]. The letter is dropped. */
        private void skipTypedArrayPrefix() {
            skipSpace();
            if (pos < s.length() && "ILBilb".indexOf(s.charAt(pos)) >= 0) {
                int q = pos + 1;
                while (q < s.length() && Character.isWhitespace(s.charAt(q))) {
                    q++;
                }
                if (q < s.length() && s.charAt(q) == ';') {
                    pos = q + 1;
                }
            }
        }

        String readKey() {
            if (atEnd()) {
                throw error("Expected a key");
            }
            char c = s.charAt(pos);
            if (c == '"' || c == '\'') {
                return readString();
            }
            int start = pos;
            while (pos < s.length() && isBareChar(s.charAt(pos))) {
                pos++;
            }
            if (start == pos) {
                throw error("Expected a key but found '" + c + "'");
            }
            return s.substring(start, pos);
        }

        DataNode readValue() {
            if (atEnd()) {
                throw error("Expected a value but reached end of input");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{':
                    return readCompound();
                case '[':
                    return readList();
                case '"':
                case '\'':
                    return new DataNode.Str(readString());
                default:
                    return readScalar();
            }
        }

        String readString() {
            char quote = s.charAt(pos++);
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("Unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == quote) {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw error("Unterminated escape");
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"', '\'', '\\' -> sb.append(e);
                    case 'u' -> {
                        if (pos + 4 > s.length()) {
                            throw error("Truncated \\u escape");
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("Invalid \\u escape");
                        }
                        pos += 4;
                    }
                    default -> {
                        if (!lenient) {
                            throw error("Unknown escape '\\" + e + "'");
                        }
                        sb.append(e);
                    }
                }
            }
        }

        DataNode readScalar() {
            int start = pos;
            while (pos < s.length() && isBareChar(s.charAt(pos))) {
                pos++;
            }
            if (start == pos) {
                throw error("Unexpected character '" + s.charAt(pos) + "'");
            }
            String t = s.substring(start, pos);
            if (t.equals("true")) {
                return new DataNode.Bool(true);
            }
            if (t.equals("false")) {
                return new DataNode.Bool(false);
            }
            if (t.equals("null")) {
                return null;
            }
            try {
                // hexadecimal (JSON5) and legacy typed suffixes: b/s = small integer, f = float
                if (t.matches("[+-]?0[xX][0-9a-fA-F]+")) {
                    boolean neg = t.startsWith("-");
                    long v = Long.parseLong(t.replaceFirst("^[+-]?0[xX]", ""), 16);
                    v = neg ? -v : v;
                    return v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE ? new DataNode.Int((int) v) : new DataNode.Lng(v);
                }
                if (t.matches("[+-]?\\d+[bBsS]")) {
                    return new DataNode.Int(Integer.parseInt(t.substring(0, t.length() - 1).replace("+", "")));
                }
                if (t.matches("[+-]?\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fF]")) {
                    return new DataNode.Dbl(Double.parseDouble(t.substring(0, t.length() - 1)));
                }
                if (t.matches("[+-]?\\d+")) {
                    long v = Long.parseLong(t.startsWith("+") ? t.substring(1) : t);
                    return v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE
                            ? new DataNode.Int((int) v) : new DataNode.Lng(v);
                }
                if (t.matches("[+-]?\\d+[lL]")) {
                    String digits = t.substring(0, t.length() - 1);
                    return new DataNode.Lng(Long.parseLong(digits.startsWith("+") ? digits.substring(1) : digits));
                }
                if (t.matches("[+-]?\\d+[dD]")) {
                    return new DataNode.Dbl(Double.parseDouble(t.substring(0, t.length() - 1)));
                }
                if (t.matches("[+-]?\\d+(\\.\\d+)?([eE][+-]?\\d+)?[dD]?") && (t.contains(".") || t.contains("e") || t.contains("E"))) {
                    return new DataNode.Dbl(Double.parseDouble(t.replaceAll("[dD]$", "")));
                }
            } catch (NumberFormatException ex) {
                pos = start;
                throw error("Number out of range: " + t);
            }
            pos = start;
            throw error("Unquoted text '" + t + "' is not a value; quote strings, or use true/false/numbers");
        }

        private static boolean isBareChar(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '+' || c == '-';
        }
    }

    // ------------------------------------------------------------------ printing

    /** Pretty-print with two-space indentation. */
    public static String print(DataNode node) {
        StringBuilder sb = new StringBuilder();
        write(sb, node, 0, true);
        return sb.toString();
    }

    /** Single-line form. Parses back to an equal tree. */
    public static String printCompact(DataNode node) {
        StringBuilder sb = new StringBuilder();
        write(sb, node, 0, false);
        return sb.toString();
    }

    private static void write(StringBuilder sb, DataNode node, int depth, boolean pretty) {
        if (node instanceof DataNode.Str v) {
            quote(sb, v.value());
        } else if (node instanceof DataNode.Int v) {
            sb.append(v.value());
        } else if (node instanceof DataNode.Lng v) {
            sb.append(v.value()).append('L');
        } else if (node instanceof DataNode.Dbl v) {
            double d = v.value();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("NaN and infinity cannot be written");
            }
            String t = Double.toString(d);
            sb.append(t);
        } else if (node instanceof DataNode.Bool v) {
            sb.append(v.value());
        } else if (node instanceof DataNode.Lst v) {
            writeList(sb, v, depth, pretty);
        } else if (node instanceof DataNode.Compound v) {
            writeCompound(sb, v, depth, pretty);
        }
    }

    private static void writeCompound(StringBuilder sb, DataNode.Compound c, int depth, boolean pretty) {
        if (c.entries().isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, DataNode> e : c.entries().entrySet()) {
            if (pretty) {
                sb.append('\n').append("  ".repeat(depth + 1));
            } else if (!first) {
                sb.append(", ");
            }
            first = false;
            writeKey(sb, e.getKey());
            sb.append(pretty ? ": " : ":");
            write(sb, e.getValue(), depth + 1, pretty);
        }
        if (pretty) {
            sb.append('\n').append("  ".repeat(depth));
        }
        sb.append('}');
    }

    private static void writeList(StringBuilder sb, DataNode.Lst l, int depth, boolean pretty) {
        if (l.items().isEmpty()) {
            sb.append("[]");
            return;
        }
        boolean scalarOnly = l.items().stream().noneMatch(n -> n instanceof DataNode.Lst || n instanceof DataNode.Compound);
        sb.append('[');
        boolean multiline = pretty && !scalarOnly;
        for (int i = 0; i < l.items().size(); i++) {
            if (multiline) {
                sb.append('\n').append("  ".repeat(depth + 1));
            } else if (i > 0) {
                sb.append(", ");
            }
            write(sb, l.items().get(i), depth + 1, pretty);
        }
        if (multiline) {
            sb.append('\n').append("  ".repeat(depth));
        }
        sb.append(']');
    }

    private static void writeKey(StringBuilder sb, String key) {
        boolean bare = !key.isEmpty();
        for (int i = 0; i < key.length() && bare; i++) {
            bare = Parser.isBareChar(key.charAt(i));
        }
        if (bare) {
            sb.append(key);
        } else {
            quote(sb, key);
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
