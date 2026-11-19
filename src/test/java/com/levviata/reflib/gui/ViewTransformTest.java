/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.gui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ViewTransformTest {

    @Test
    void panAndRoundTrip() {
        ViewTransform v = new ViewTransform(0.25, 4);
        v.panBy(10, -5);
        assertEquals(10, v.screenX(0), 1e-9);
        assertEquals(-5, v.screenY(0), 1e-9);
        assertEquals(7, v.worldX(v.screenX(7)), 1e-9);
    }

    @Test
    void zoomKeepsPointUnderPointerFixed() {
        ViewTransform v = new ViewTransform(0.25, 4);
        v.panBy(30, 40);
        double wx = v.worldX(100);
        double wy = v.worldY(80);
        v.zoomAt(100, 80, 2);
        assertEquals(2.0, v.zoom(), 1e-9);
        assertEquals(100, v.screenX(wx), 1e-9);
        assertEquals(80, v.screenY(wy), 1e-9);
    }

    @Test
    void zoomIsClamped() {
        ViewTransform v = new ViewTransform(0.5, 2);
        v.zoomAt(0, 0, 100);
        assertEquals(2.0, v.zoom());
        v.zoomAt(0, 0, 0.0001);
        assertEquals(0.5, v.zoom());
    }

    @Test
    void centerOnPlacesWorldPoint() {
        ViewTransform v = new ViewTransform(0.5, 2);
        v.zoomAt(0, 0, 2);
        v.centerOn(10, 20, 300, 200);
        assertEquals(300, v.screenX(10), 1e-9);
        assertEquals(200, v.screenY(20), 1e-9);
    }

    @Test
    void rejectsBadRange() {
        assertThrows(IllegalArgumentException.class, () -> new ViewTransform(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ViewTransform(2, 1));
    }
}
