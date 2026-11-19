/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.sync;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class DirtyTrackerTest {

    @Test
    void drainReturnsDeduplicatedKeysInOrderAndClears() {
        DirtyTracker<String, Long> t = new DirtyTracker<>();
        t.mark("p1", 5L);
        t.mark("p1", 3L);
        t.mark("p1", 5L);
        assertTrue(t.hasDirty("p1"));
        assertEquals(List.of(5L, 3L), t.drain("p1"));
        assertFalse(t.hasDirty("p1"));
        assertEquals(List.of(), t.drain("p1"));
    }

    @Test
    void recipientsAreIndependentAndRemovable() {
        DirtyTracker<String, Long> t = new DirtyTracker<>();
        t.markForAll(List.of("a", "b"), 1L);
        t.mark("a", 2L);
        assertEquals(List.of(1L, 2L), t.drain("a"));
        t.remove("b");
        assertEquals(List.of(), t.drain("b"));
    }
}
