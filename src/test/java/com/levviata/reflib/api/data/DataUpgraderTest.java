/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DataUpgraderTest {

    private final DataUpgrader upgrader = new DataUpgrader(2)
            .step(0, c -> c.with("name", c.getString("title", "")).without("title"))
            .step(1, c -> c.with("size", c.getInt("size", 1) * 10));

    @Test
    void upgradesThroughAllSteps() {
        DataNode.Compound old = DataNode.Compound.empty().with("title", "x").with("size", 2);
        DataNode.Compound out = upgrader.upgrade(old);
        assertEquals("x", out.getString("name", null));
        assertFalse(out.has("title"));
        assertEquals(20, out.getInt("size", 0));
        assertEquals(2, out.getInt(DataUpgrader.VERSION_KEY, -1));
    }

    @Test
    void startsFromStoredVersion() {
        DataNode.Compound mid = DataNode.Compound.empty().with(DataUpgrader.VERSION_KEY, 1).with("size", 3);
        assertEquals(30, upgrader.upgrade(mid).getInt("size", 0));
    }

    @Test
    void currentDataIsUnchangedExceptVersion() {
        DataNode.Compound cur = DataNode.Compound.empty().with(DataUpgrader.VERSION_KEY, 2).with("a", 1);
        assertEquals(cur, upgrader.upgrade(cur));
    }

    @Test
    void refusesNewerData() {
        DataNode.Compound future = DataNode.Compound.empty().with(DataUpgrader.VERSION_KEY, 3);
        assertThrows(IllegalStateException.class, () -> upgrader.upgrade(future));
    }

    @Test
    void missingStepIsAnError() {
        DataUpgrader gap = new DataUpgrader(2).step(1, c -> c);
        assertThrows(IllegalStateException.class, () -> gap.upgrade(DataNode.Compound.empty()));
    }

    @Test
    void rejectsBadStepRegistration() {
        assertThrows(IllegalArgumentException.class, () -> new DataUpgrader(1).step(1, c -> c));
        assertThrows(IllegalStateException.class, () -> new DataUpgrader(2).step(0, c -> c).step(0, c -> c));
    }
}
