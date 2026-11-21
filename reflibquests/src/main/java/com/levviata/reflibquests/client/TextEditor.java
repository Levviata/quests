/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The editing model of a multi-line text box: caret, selection, undo and redo, word-wrapped layout. It knows nothing
 * about drawing; the screen asks it for the wrapped rows and where the caret is.
 */
@SideOnly(Side.CLIENT)
final class TextEditor {

    /** One wrapped row of text: characters {@code start} (inclusive) to {@code end} (exclusive). */
    record Row(int start, int end) {}

    private record Snap(String text, int caret) {}

    private static final int MAX_UNDO = 300;

    private String text;
    private int caret;
    private int anchor; // equals caret when nothing is selected
    private final ArrayDeque<Snap> undo = new ArrayDeque<>();
    private final ArrayDeque<Snap> redo = new ArrayDeque<>();
    private long lastTypedAt;
    private final int maxLength;
    private final String original;

    TextEditor(String initial, int maxLength) {
        this.text = initial;
        this.original = initial;
        this.caret = initial.length();
        this.anchor = caret;
        this.maxLength = maxLength;
    }

    String text() {
        return text;
    }

    String original() {
        return original;
    }

    boolean changed() {
        return !text.equals(original);
    }

    int caret() {
        return caret;
    }

    boolean hasSelection() {
        return anchor != caret;
    }

    int selStart() {
        return Math.min(anchor, caret);
    }

    int selEnd() {
        return Math.max(anchor, caret);
    }

    String selected() {
        return text.substring(selStart(), selEnd());
    }

    boolean canUndo() {
        return !undo.isEmpty();
    }

    boolean canRedo() {
        return !redo.isEmpty();
    }

    // ---- editing --------------------------------------------------------------------------------------------------------

    private void remember(boolean typing) {
        long now = System.currentTimeMillis();
        if (typing && now - lastTypedAt < 700 && !undo.isEmpty() && !hasSelection()) {
            lastTypedAt = now; // typing in one go is one undo step
            return;
        }
        lastTypedAt = typing ? now : 0;
        undo.push(new Snap(text, caret));
        while (undo.size() > MAX_UNDO) {
            undo.removeLast();
        }
        redo.clear();
    }

    /** Replace the selection (or insert at the caret). */
    void insert(String s) {
        insert(s, s.length() <= 1);
    }

    void insert(String s, boolean typing) {
        String clean = s.replace("\r", "");
        if (text.length() - (selEnd() - selStart()) + clean.length() > maxLength) {
            return;
        }
        remember(typing);
        text = text.substring(0, selStart()) + clean + text.substring(selEnd());
        caret = selStart() + clean.length();
        anchor = caret;
    }

    void backspace() {
        if (hasSelection()) {
            deleteSelection();
        } else if (caret > 0) {
            remember(true);
            text = text.substring(0, caret - 1) + text.substring(caret);
            caret--;
            anchor = caret;
        }
    }

    void delete() {
        if (hasSelection()) {
            deleteSelection();
        } else if (caret < text.length()) {
            remember(true);
            text = text.substring(0, caret) + text.substring(caret + 1);
        }
    }

    void deleteSelection() {
        if (hasSelection()) {
            remember(false);
            text = text.substring(0, selStart()) + text.substring(selEnd());
            caret = selStart();
            anchor = caret;
        }
    }

    /** Wrap the selection in {@code before} / {@code after}; with no selection, insert both and put the caret between. */
    void surround(String before, String after) {
        remember(false);
        int s = selStart();
        int e = selEnd();
        text = text.substring(0, s) + before + text.substring(s, e) + after + text.substring(e);
        caret = hasSelection() ? e + before.length() + after.length() : s + before.length();
        anchor = caret;
    }

    /** Replace the selected text (or all text when nothing is selected) with {@code replacement}. */
    void replaceSelectionOrAll(java.util.function.UnaryOperator<String> f) {
        remember(false);
        if (hasSelection()) {
            String r = f.apply(selected());
            int s = selStart();
            text = text.substring(0, s) + r + text.substring(selEnd());
            caret = s + r.length();
        } else {
            text = f.apply(text);
            caret = Math.min(caret, text.length());
        }
        anchor = caret;
    }

    /** Replaces characters {@code from} to {@code to} (exclusive) with new text, as one undo step. */
    void replaceRange(int from, int to, String replacement) {
        remember(false);
        text = text.substring(0, from) + replacement + text.substring(to);
        caret = from + replacement.length();
        anchor = caret;
    }

    /** Replaces the whole text, as one undo step. */
    void replaceAllText(String replacement) {
        remember(false);
        text = replacement;
        caret = Math.min(caret, text.length());
        anchor = caret;
    }

    void undo() {
        if (!undo.isEmpty()) {
            redo.push(new Snap(text, caret));
            Snap s = undo.pop();
            text = s.text();
            caret = Math.min(s.caret(), text.length());
            anchor = caret;
            lastTypedAt = 0;
        }
    }

    void redo() {
        if (!redo.isEmpty()) {
            undo.push(new Snap(text, caret));
            Snap s = redo.pop();
            text = s.text();
            caret = Math.min(s.caret(), text.length());
            anchor = caret;
            lastTypedAt = 0;
        }
    }

    // ---- moving ---------------------------------------------------------------------------------------------------------

    void setCaret(int index, boolean extend) {
        caret = Math.max(0, Math.min(text.length(), index));
        if (!extend) {
            anchor = caret;
        }
    }

    void selectAll() {
        anchor = 0;
        caret = text.length();
    }

    void left(boolean extend) {
        if (!extend && hasSelection()) {
            setCaret(selStart(), false);
        } else {
            setCaret(caret - 1, extend);
        }
    }

    void right(boolean extend) {
        if (!extend && hasSelection()) {
            setCaret(selEnd(), false);
        } else {
            setCaret(caret + 1, extend);
        }
    }

    // ---- layout ---------------------------------------------------------------------------------------------------------

    /** Splits the text into rows no wider than {@code width}: on newlines first, then at spaces (or anywhere for long words). */
    List<Row> layout(FontRenderer fr, int width) {
        List<Row> rows = new ArrayList<>();
        int start = 0;
        while (start <= text.length()) {
            int nl = text.indexOf('\n', start);
            int paraEnd = nl < 0 ? text.length() : nl;
            int p = start;
            while (true) {
                String rest = text.substring(p, paraEnd);
                if (fr.getStringWidth(rest) <= width) {
                    rows.add(new Row(p, paraEnd));
                    break;
                }
                int fit = fr.trimStringToWidth(rest, width).length();
                int cut = fit;
                int space = rest.lastIndexOf(' ', fit);
                if (space > 0) {
                    cut = space + 1; // break after the space
                }
                cut = Math.max(1, cut);
                rows.add(new Row(p, p + cut));
                p += cut;
            }
            if (nl < 0) {
                break;
            }
            start = nl + 1;
        }
        return rows;
    }

    /** Index of the row the caret is in (a caret at the end of a wrapped row belongs to the next row). */
    int rowOf(List<Row> rows, int index) {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (index < r.end()) {
                return i;
            }
            if (index == r.end() && !(i + 1 < rows.size() && rows.get(i + 1).start() == r.end())) {
                return i;
            }
        }
        return rows.size() - 1;
    }

    /** Caret index for a click at pixel offset {@code x} inside row {@code row}. */
    int indexAt(FontRenderer fr, List<Row> rows, int row, int x) {
        Row r = rows.get(Math.max(0, Math.min(rows.size() - 1, row)));
        String seg = text.substring(r.start(), r.end());
        if (seg.endsWith("\n")) {
            seg = seg.substring(0, seg.length() - 1);
        }
        return r.start() + fr.trimStringToWidth(seg, Math.max(0, x)).length();
    }

    /** Moves the caret one row up or down, keeping its horizontal position. */
    void vertical(FontRenderer fr, List<Row> rows, int delta, boolean extend) {
        int row = rowOf(rows, caret);
        int target = row + delta;
        if (target < 0) {
            setCaret(0, extend);
            return;
        }
        if (target >= rows.size()) {
            setCaret(text.length(), extend);
            return;
        }
        Row cur = rows.get(row);
        int x = fr.getStringWidth(text.substring(cur.start(), Math.min(caret, cur.end())));
        setCaret(indexAt(fr, rows, target, x), extend);
    }

    void home(List<Row> rows, boolean extend) {
        setCaret(rows.get(rowOf(rows, caret)).start(), extend);
    }

    void end(List<Row> rows, boolean extend) {
        Row r = rows.get(rowOf(rows, caret));
        int e = r.end();
        if (e > r.start() && e <= text.length() && e > 0 && text.charAt(e - 1) == ' ' && e < text.length()) {
            e--; // stay before the space a wrapped row ends with
        }
        setCaret(e, extend);
    }
}
