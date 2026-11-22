/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflib.api.data.Snbt;

/**
 * Pure-Java model of quest description text (see docs/spec-description-editor.md). No game classes, so it is
 * unit-testable. A description is a list of entries; {@link #parse(List)} turns it into pages of visual lines made
 * of styled spans and images. Parsing never throws: anything it does not understand is kept as literal text.
 */
public final class RichText {

    public static final String PAGE_BREAK = "{@pagebreak}";
    public static final int NO_COLOR = -1;
    private static final int MAX_IMAGE = 4096;

    private RichText() {}

    public enum Align { LEFT, CENTER, RIGHT }

    /** A click action attached to a span; {@code action} is e.g. {@code open_url} or {@code change_page}. */
    public record Click(String action, String value) {}

    public sealed interface Element permits Span, Image {}

    public record Span(String text, int rgb, boolean bold, boolean italic, boolean underline, boolean strike,
            boolean obfuscated, Click click, boolean rainbow, String hover) implements Element {}

    public record Image(String texture, int width, int height, Align align, boolean fit) implements Element {}

    /** One visual row: spans and images laid out together (an image row holds a single {@link Image}). */
    public record Line(List<Element> elements) {
        public boolean blank() {
            return elements.isEmpty();
        }
    }

    public record Page(List<Line> lines) {}

    /** Marker put into display strings where a rainbow-coloured stretch starts (see {@link #toRainbowAware}). */
    public static final char RAINBOW_MARK = '\uE000';

    private record Style(int rgb, boolean bold, boolean italic, boolean underline, boolean strike, boolean obf, Click click, boolean rainbow, String hover) {
        static final Style PLAIN = new Style(NO_COLOR, false, false, false, false, false, null, false, "");

        Style withColor(int c) {
            return new Style(c, false, false, false, false, false, click, false, hover); // a colour code resets formatting
        }
        Style withBold(boolean v) { return new Style(rgb, v, italic, underline, strike, obf, click, rainbow, hover); }
        Style withItalic(boolean v) { return new Style(rgb, bold, v, underline, strike, obf, click, rainbow, hover); }
        Style withUnderline(boolean v) { return new Style(rgb, bold, italic, v, strike, obf, click, rainbow, hover); }
        Style withStrike(boolean v) { return new Style(rgb, bold, italic, underline, v, obf, click, rainbow, hover); }
        Style withObf(boolean v) { return new Style(rgb, bold, italic, underline, strike, v, click, rainbow, hover); }
        Style withClick(Click c) { return new Style(rgb, bold, italic, underline, strike, obf, c, rainbow, hover); }
        Style withHover(String h) { return new Style(rgb, bold, italic, underline, strike, obf, click, rainbow, h); }
        Style withRainbow(boolean v) { return new Style(rgb, bold, italic, underline, strike, obf, click, v, hover); }

        Span span(String text) { return new Span(text, rgb, bold, italic, underline, strike, obf, click, rainbow, hover); }
    }

    private static final int[] PALETTE = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF };
    private static final String[] COLOR_NAMES = {
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
        "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white" };

    // ---- pages ----------------------------------------------------------------------------------------------

    public static boolean isPageBreak(String entry) {
        return entry != null && entry.trim().equals(PAGE_BREAK);
    }

    /** Parses all entries into pages (a page break starts a new page; there is always at least one page). */
    public static List<Page> parse(List<String> entries) {
        List<Page> pages = new ArrayList<>();
        List<Line> current = new ArrayList<>();
        if (entries != null) {
            for (String entry : entries) {
                if (isPageBreak(entry)) {
                    pages.add(new Page(List.copyOf(current)));
                    current = new ArrayList<>();
                } else {
                    current.addAll(parseEntry(entry));
                }
            }
        }
        pages.add(new Page(List.copyOf(current)));
        return List.copyOf(pages);
    }

    // ---- one entry -------------------------------------------------------------------------------------------

    /** Parses one entry into one or more visual lines. A page-break entry yields no lines. */
    public static List<Line> parseEntry(String entry) {
        if (entry == null || isPageBreak(entry)) {
            return List.of();
        }
        String t = entry.trim();
        if (t.startsWith("{image:") && t.endsWith("}")) {
            Image image = parseImage(t);
            if (image != null) {
                return List.of(new Line(List.of(image)));
            }
        } else if (looksLikeJson(t)) {
            List<Line> json = parseJson(t);
            if (json != null) {
                return json;
            }
        }
        List<Line> rows = new ArrayList<>();
        Style style = Style.PLAIN;
        for (String row : splitNewlines(entry)) {
            List<Element> elements = new ArrayList<>();
            style = parseCodes(row, style, elements);
            rows.add(new Line(List.copyOf(elements)));
        }
        return rows;
    }

    /** Splits on the two-character sequence backslash + n (the pack's paragraph marker). */
    private static List<String> splitNewlines(String s) {
        List<String> out = new ArrayList<>();
        int from = 0;
        while (true) {
            int i = s.indexOf("\\n", from);
            if (i < 0) {
                out.add(s.substring(from));
                return out;
            }
            out.add(s.substring(from, i));
            from = i + 2;
        }
    }

    private static Style parseCodes(String row, Style start, List<Element> out) {
        Style style = start;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (c == '\\' && i + 1 < row.length() && row.charAt(i + 1) == '&') {
                text.append('&'); // "\&" is a literal ampersand
                i++;
                continue;
            }
            if ((c == '&' || c == '§') && i + 1 < row.length()) { // legacy files write the section sign, as the game does
                char n = Character.toLowerCase(row.charAt(i + 1));
                if (n == '&') {
                    text.append('&');
                    i++;
                    continue;
                }
                Style next = applyCode(style, n);
                if (next != null) {
                    flush(text, style, out);
                    style = next;
                    i++;
                    continue;
                }
            }
            text.append(c);
        }
        flush(text, style, out);
        return style;
    }

    private static Style applyCode(Style s, char code) {
        int idx = "0123456789abcdef".indexOf(code);
        if (idx >= 0) {
            return s.withColor(PALETTE[idx]);
        }
        return switch (code) {
            case 'l' -> s.withBold(true);
            case 'o' -> s.withItalic(true);
            case 'n' -> s.withUnderline(true);
            case 'm' -> s.withStrike(true);
            case 'k' -> s.withObf(true);
            case 'z' -> s.withRainbow(true);
            case 'r' -> Style.PLAIN.withClick(s.click());
            default -> null;
        };
    }

    private static void flush(StringBuilder text, Style style, List<Element> out) {
        if (text.length() > 0) {
            out.add(style.span(text.toString()));
            text.setLength(0);
        }
    }

    // ---- images ----------------------------------------------------------------------------------------------

    /** Parses {@code {image:<texture> width:n height:n align:x fit:b}}; returns null when malformed. */
    static Image parseImage(String tag) {
        String body = tag.substring("{image:".length(), tag.length() - 1).trim();
        if (body.isEmpty()) {
            return null;
        }
        String[] parts = body.split("\\s+");
        String texture = parts[0];
        if (texture.isEmpty() || texture.indexOf(':') == 0) {
            return null;
        }
        int width = 100;
        int height = 100;
        Align align = Align.CENTER;
        boolean fit = false;
        for (int i = 1; i < parts.length; i++) {
            int colon = parts[i].indexOf(':');
            if (colon <= 0) {
                return null;
            }
            String key = parts[i].substring(0, colon).toLowerCase(Locale.ROOT);
            String value = parts[i].substring(colon + 1);
            try {
                switch (key) {
                    case "width" -> width = clampSize(Integer.parseInt(value));
                    case "height" -> height = clampSize(Integer.parseInt(value));
                    case "align" -> align = Align.valueOf(value.toUpperCase(Locale.ROOT));
                    case "fit" -> {
                        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                            return null;
                        }
                        fit = Boolean.parseBoolean(value);
                    }
                    default -> { /* unknown attributes are ignored */ }
                }
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return new Image(texture, width, height, align, fit);
    }

    private static int clampSize(int v) {
        return Math.max(1, Math.min(MAX_IMAGE, v));
    }

    /** Builds an image tag (used by the editor's Image dialog). */
    public static String imageTag(String texture, int width, int height, Align align, boolean fit) {
        return "{image:" + texture + " width:" + clampSize(width) + " height:" + clampSize(height)
                + " align:" + align.name().toLowerCase(Locale.ROOT) + " fit:" + fit + "}";
    }

    // ---- JSON components -------------------------------------------------------------------------------------

    /** Only treat text as JSON when it opens like JSON, so notes such as "[Page Up]" stay literal. */
    private static boolean looksLikeJson(String t) {
        if (t.length() < 2 || (t.charAt(0) != '{' && t.charAt(0) != '[')) {
            return false;
        }
        int i = 1;
        while (i < t.length() && Character.isWhitespace(t.charAt(i))) {
            i++;
        }
        return i < t.length() && "\"{[".indexOf(t.charAt(i)) >= 0;
    }

    /** Returns the visual lines for a JSON text component (or list), or null if the text is not valid JSON. */
    private static List<Line> parseJson(String text) {
        DataNode root;
        try {
            root = Snbt.parseLenient("{v:" + text + "}").get("v");
        } catch (RuntimeException ex) {
            return null;
        }
        if (root == null) {
            return null;
        }
        List<Element> elements = new ArrayList<>();
        if (!collect(root, Style.PLAIN, elements, 0)) {
            return null;
        }
        return List.of(new Line(List.copyOf(elements)));
    }

    private static boolean collect(DataNode node, Style inherited, List<Element> out, int depth) {
        if (depth > 16) {
            return true;
        }
        if (node instanceof DataNode.Str s) {
            if (!s.value().isEmpty()) {
                out.add(inherited.span(s.value()));
            }
            return true;
        }
        if (node instanceof DataNode.Lst list) {
            for (DataNode child : list.items()) {
                if (!collect(child, inherited, out, depth + 1)) {
                    return false;
                }
            }
            return true;
        }
        if (node instanceof DataNode.Compound c) {
            Style style = inherited;
            if (c.has("color")) {
                style = style.withColor(parseColor(c.getString("color", "")));
            }
            if (c.has("bold")) style = style.withBold(c.getBool("bold", false));
            if (c.has("italic")) style = style.withItalic(c.getBool("italic", false));
            if (c.has("underlined")) style = style.withUnderline(c.getBool("underlined", false));
            if (c.has("strikethrough")) style = style.withStrike(c.getBool("strikethrough", false));
            if (c.has("obfuscated")) style = style.withObf(c.getBool("obfuscated", false));
            // the 1.21.5 spelling (click_event / hover_event, as FTB Quests 26.1.2 writes it) and the older one
            DataNode.Compound ce = c.has("click_event") ? c.getCompound("click_event") : c.getCompound("clickEvent");
            String action = ce.getString("action", "");
            if (!action.isEmpty()) {
                String value = ce.getString("value", ce.getString("url", ce.getString("command", ce.getString("page", ""))));
                if (action.equals("custom") && ce.getString("id", "").equals("ftbquests:link")) {
                    // FTB 26.1.2 "Insert Quest Link": {"id":"ftbquests:link","payload":{"quest_id":"...","page":1},"action":"custom"}
                    style = style.withClick(new Click("change_page", ce.getCompound("payload").getString("quest_id", "")));
                } else {
                    style = style.withClick(new Click(action, value));
                }
            }
            DataNode.Compound he = c.has("hover_event") ? c.getCompound("hover_event") : c.getCompound("hoverEvent");
            if (he.getString("action", "").equals("show_text")) {
                DataNode shown = he.has("value") ? he.get("value") : he.get("contents");
                StringBuilder tip = new StringBuilder();
                plain(shown, tip, 0);
                style = style.withHover(tip.toString());
            }
            String own = c.getString("text", "");
            if (!own.isEmpty()) {
                out.add(style.span(own));
            }
            if (c.has("extra")) {
                for (DataNode child : c.getList("extra")) {
                    if (!collect(child, style, out, depth + 1)) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    /** The plain text of a text component, for a hover tooltip: literal text, a translate key shown as written, extras. */
    private static void plain(DataNode node, StringBuilder out, int depth) {
        if (node == null || depth > 16) {
            return;
        }
        if (node instanceof DataNode.Str s) {
            out.append(s.value());
        } else if (node instanceof DataNode.Lst l) {
            for (DataNode child : l.items()) {
                plain(child, out, depth + 1);
            }
        } else if (node instanceof DataNode.Compound c) {
            out.append(c.has("text") ? c.getString("text", "") : c.getString("translate", ""));
            for (DataNode child : c.getList("extra")) {
                plain(child, out, depth + 1);
            }
        }
    }

    private static int parseColor(String s) {
        String name = s.toLowerCase(Locale.ROOT);
        for (int i = 0; i < COLOR_NAMES.length; i++) {
            if (COLOR_NAMES[i].equals(name)) {
                return PALETTE[i];
            }
        }
        if (name.length() == 7 && name.charAt(0) == '#') {
            try {
                return Integer.parseInt(name.substring(1), 16);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return NO_COLOR;
    }

    // ---- JSON text lines (the editor's "convert line to JSON" and link buttons) --------------------------------------------------

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        final char backslash = (char) 92;
        for (char c : s.toCharArray()) {
            if (c == '"' || c == backslash) {
                sb.append(backslash).append(c);
            } else if (c == (char) 10) {
                sb.append(backslash).append('n');
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** A one-link JSON line, for example an {@code open_url} or {@code change_page} link. */
    public static String linkJson(String text, String action, String value) {
        return "[{\"text\":" + jsonString(text) + ",\"underlined\":true,\"clickEvent\":{\"action\":" + jsonString(action)
                + ",\"value\":" + jsonString(value) + "}}]";
    }

    /** The same text as a JSON component list, so styles can be edited as data; null if the entry is not plain formatted text. */
    public static String toJsonLine(String entry) {
        List<Line> rows = parseEntry(entry);
        if (rows.size() != 1 || isPageBreak(entry)) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Element e : rows.get(0).elements()) {
            if (!(e instanceof Span s)) {
                return null;
            }
            StringBuilder sb = new StringBuilder("{\"text\":").append(jsonString(s.text()));
            if (s.rgb() != NO_COLOR) {
                sb.append(String.format(",\"color\":\"#%06X\"", s.rgb() & 0xFFFFFF));
            }
            if (s.bold()) sb.append(",\"bold\":true");
            if (s.italic()) sb.append(",\"italic\":true");
            if (s.underline()) sb.append(",\"underlined\":true");
            if (s.strike()) sb.append(",\"strikethrough\":true");
            if (s.obfuscated()) sb.append(",\"obfuscated\":true");
            parts.add(sb.append('}').toString());
        }
        return parts.isEmpty() ? null : "[" + String.join(",", parts) + "]";
    }

    // ---- string helpers (titles, tooltips, editor) ------------------------------------------------------------

    /** Removes {@code &x} formatting codes ({@code &&} becomes a literal {@code &}). Used for searching. */
    public static String stripCodes(String s) {
        return convert(s, false, false);
    }

    /** Converts {@code &x} codes to the game's section-sign codes for direct drawing; {@code &&} becomes {@code &}. */
    public static String toSectionCodes(String s) {
        return convert(s, true, false);
    }

    /** Like {@link #toSectionCodes}, but {@code &z} becomes {@link #RAINBOW_MARK} instead of being dropped. */
    public static String toRainbowAware(String s) {
        return convert(s, true, true);
    }

    /** Alias of {@link #stripCodes}: what the editor's Clear Formatting action does to a selection. */
    public static String clearFormatting(String s) {
        return stripCodes(s);
    }

    private static String convert(String s, boolean keep, boolean rainbow) {
        if (s == null || s.indexOf('&') < 0) {
            return s == null ? "" : s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && s.charAt(i + 1) == '&') {
                sb.append('&');
                i++;
                continue;
            }
            if (c == '&' && i + 1 < s.length()) {
                char n = Character.toLowerCase(s.charAt(i + 1));
                if (n == '&') {
                    sb.append('&');
                    i++;
                    continue;
                }
                if ("0123456789abcdeflmnokrz".indexOf(n) >= 0) {
                    if (n == 'z') {
                        if (keep && rainbow) {
                            sb.append(RAINBOW_MARK);
                        }
                    } else if (keep) {
                        sb.append('§').append(n);
                    }
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
