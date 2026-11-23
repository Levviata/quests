/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.gui;

public final class TextModel {

    private final StringBuilder text = new StringBuilder();
    private final int maxLength;
    private int caret;

    public TextModel(String initial, int maxLength) {
        this.maxLength = maxLength;
        set(initial);
    }

    public String text() {
        return text.toString();
    }

    public int caret() {
        return caret;
    }

    public void set(String value) {
        text.setLength(0);
        for (int i = 0; i < value.length() && text.length() < maxLength; i++) {
            if (printable(value.charAt(i))) {
                text.append(value.charAt(i));
            }
        }
        caret = text.length();
    }

    public static boolean printable(char c) {
        return c >= 32 && c != 127 && c != '§';
    }

    public boolean insert(char c) {
        if (!printable(c) || text.length() >= maxLength) {
            return false;
        }
        text.insert(caret++, c);
        return true;
    }

    public void backspace() {
        if (caret > 0) {
            text.deleteCharAt(--caret);
        }
    }

    public void delete() {
        if (caret < text.length()) {
            text.deleteCharAt(caret);
        }
    }

    public void left() {
        caret = Math.max(0, caret - 1);
    }

    public void right() {
        caret = Math.min(text.length(), caret + 1);
    }

    public void home() {
        caret = 0;
    }

    public void end() {
        caret = text.length();
    }

    public void caretAtOffset(int pixels, java.util.function.IntUnaryOperator widthOfPrefix) {
        int best = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (widthOfPrefix.applyAsInt(i) <= pixels) {
                best = i;
            }
        }
        caret = best;
    }
}
