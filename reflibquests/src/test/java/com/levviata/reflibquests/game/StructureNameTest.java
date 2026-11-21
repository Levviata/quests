/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StructureNameTest {

    @Test
    void vanillaStructuresMapToTheirOldNames() {
        assertEquals("Stronghold", TaskTracker.structureName("minecraft:stronghold"));
        assertEquals("Temple", TaskTracker.structureName("minecraft:desert_pyramid"));
        assertEquals("Temple", TaskTracker.structureName("minecraft:igloo"));
        assertEquals("Fortress", TaskTracker.structureName("minecraft:fortress"));
        assertEquals("Mansion", TaskTracker.structureName("minecraft:mansion"));
        assertEquals("EndCity", TaskTracker.structureName("minecraft:end_city"));
        assertEquals("Village", TaskTracker.structureName("village"));
    }

    @Test
    void otherStructuresAreNotKnown() {
        assertEquals("", TaskTracker.structureName("aether:bronze_dungeon"));
        assertEquals("", TaskTracker.structureName("minecraft:ancient_city"));
    }
}
