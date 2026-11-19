/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.gui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TextModelTest {

    @Test
    void typingInsertsAtTheCaret() {
        TextModel m = new TextModel("", 10);
        for (char c : "abc".toCharArray()) {
            assertTrue(m.insert(c));
        }
        m.left();
        m.insert('X');
        assertEquals("abXc", m.text());
        assertEquals(3, m.caret());
    }

    @Test
    void backspaceAndDelete() {
        TextModel m = new TextModel("abcd", 10);
        m.backspace();
        assertEquals("abc", m.text());
        m.home();
        m.backspace();
        assertEquals("abc", m.text(), "nothing before the caret");
        m.delete();
        assertEquals("bc", m.text());
        m.end();
        m.delete();
        assertEquals("bc", m.text(), "nothing after the caret");
    }

    @Test
    void lengthLimitAndUnprintableCharacters() {
        TextModel m = new TextModel("abc", 4);
        assertTrue(m.insert('d'));
        assertFalse(m.insert('e'), "full");
        assertFalse(new TextModel("", 5).insert('\n'));
        assertFalse(new TextModel("", 5).insert('§'), "formatting code character is refused");
        assertEquals("ab", new TextModel("a\nb§", 10).text(), "unprintable characters are dropped from the initial text");
        assertEquals("abcd", new TextModel("abcdefgh", 4).text());
    }

    @Test
    void caretMovementIsClamped() {
        TextModel m = new TextModel("ab", 10);
        m.right();
        assertEquals(2, m.caret());
        m.home();
        m.left();
        assertEquals(0, m.caret());
        m.end();
        assertEquals(2, m.caret());
    }

    @Test
    void caretAtPixelOffsetPicksTheNearestBoundaryAtOrBefore() {
        TextModel m = new TextModel("abcd", 10);
        m.caretAtOffset(7, i -> i * 3); // each character is 3 px wide
        assertEquals(2, m.caret());
        m.caretAtOffset(0, i -> i * 3);
        assertEquals(0, m.caret());
        m.caretAtOffset(999, i -> i * 3);
        assertEquals(4, m.caret());
    }
}
