/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.impl.net;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ReassemblerTest {

    private Reassembler<String> r() {
        return new Reassembler<>(10, 100);
    }

    @Test
    void singleFragmentPassesThrough() {
        byte[] data = {1, 2, 3};
        assertSame(data, r().accept("a", 0, 0, 1, data, 0));
    }

    @Test
    void reassemblesOutOfOrder() {
        Reassembler<String> r = r();
        assertNull(r.accept("a", 7, 2, 3, new byte[] {5}, 0));
        assertNull(r.accept("a", 7, 0, 3, new byte[] {1, 2}, 0));
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, r.accept("a", 7, 1, 3, new byte[] {3, 4}, 0));
    }

    @Test
    void sendersAreIndependent() {
        Reassembler<String> r = r();
        assertNull(r.accept("a", 1, 0, 2, new byte[] {1}, 0));
        assertNull(r.accept("b", 1, 0, 2, new byte[] {9}, 0));
        assertArrayEquals(new byte[] {1, 2}, r.accept("a", 1, 1, 2, new byte[] {2}, 0));
        assertArrayEquals(new byte[] {9, 8}, r.accept("b", 1, 1, 2, new byte[] {8}, 0));
    }

    @Test
    void rejectsBadHeaders() {
        Reassembler<String> r = r();
        assertThrows(IllegalArgumentException.class, () -> r.accept("a", 0, 0, 0, new byte[0], 0));
        assertThrows(IllegalArgumentException.class, () -> r.accept("a", 0, 0, 11, new byte[0], 0));
        assertThrows(IllegalArgumentException.class, () -> r.accept("a", 0, 2, 2, new byte[0], 0));
        assertThrows(IllegalArgumentException.class, () -> r.accept("a", 0, -1, 2, new byte[0], 0));
    }

    @Test
    void rejectsDuplicatesAndChangedCounts() {
        Reassembler<String> r = r();
        r.accept("a", 1, 0, 3, new byte[] {1}, 0);
        assertThrows(IllegalArgumentException.class, () -> r.accept("a", 1, 0, 3, new byte[] {1}, 0));
        r.accept("a", 2, 0, 3, new byte[] {1}, 0);
        assertThrows(IllegalArgumentException.class, () -> r.accept("a", 2, 1, 4, new byte[] {1}, 0));
    }

    @Test
    void enforcesSizeAndPendingLimits() {
        Reassembler<String> r = r();
        assertThrows(IllegalArgumentException.class, () -> r.accept("a", 0, 0, 1, new byte[101], 0));
        r.accept("a", 1, 0, 2, new byte[60], 0);
        assertThrows(IllegalArgumentException.class, () -> r.accept("a", 1, 1, 2, new byte[60], 0));
        for (int t = 10; t < 10 + Reassembler.MAX_PENDING_PER_SENDER; t++) {
            r.accept("b", t, 0, 2, new byte[1], 0);
        }
        assertThrows(IllegalArgumentException.class, () -> r.accept("b", 99, 0, 2, new byte[1], 0));
    }

    @Test
    void staleTransfersExpireAndClearWorks() {
        Reassembler<String> r = r();
        for (int t = 0; t < Reassembler.MAX_PENDING_PER_SENDER; t++) {
            r.accept("a", t, 0, 2, new byte[1], 0);
        }
        // after the timeout the old ones are discarded, freeing capacity
        assertNull(r.accept("a", 50, 0, 2, new byte[1], Reassembler.TIMEOUT_MILLIS + 1));
        r.clear("a");
        assertNull(r.accept("a", 60, 0, 2, new byte[1], 0));
    }
}
