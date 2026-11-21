/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.gui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ScrollModelTest {

    @Test
    void clampsToContent() {
        ScrollModel m = new ScrollModel(500, 200);
        assertEquals(300, m.maxOffset());
        m.scrollBy(1000);
        assertEquals(300, m.offset());
        m.scrollBy(-1000);
        assertEquals(0, m.offset());
    }

    @Test
    void shrinkingContentReclamps() {
        ScrollModel m = new ScrollModel(500, 200);
        m.setOffset(300);
        m.resize(300, 200);
        assertEquals(100, m.offset());
    }

    @Test
    void shortContentNeverScrolls() {
        ScrollModel m = new ScrollModel(100, 200);
        m.scrollBy(50);
        assertEquals(0, m.offset());
        assertEquals(1.0, m.visibleFraction());
    }

    @Test
    void rectContainsIsHalfOpen() {
        Rect r = new Rect(10, 10, 5, 5);
        assertTrue(r.contains(10, 10));
        assertTrue(r.contains(14, 14));
        assertFalse(r.contains(15, 10));
    }
}
