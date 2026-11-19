/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.team;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeamsTest {

    @BeforeEach
    void reset() {
        Teams.resetForTests();
    }

    @Test
    void defaultIsSolo() {
        UUID p = UUID.randomUUID();
        assertEquals(p, Teams.get().teamOf(p));
        assertEquals(Set.of(p), Teams.get().membersOf(p));
    }

    @Test
    void secondReplacementIsRefused() {
        TeamProvider custom = new SoloTeamProvider();
        Teams.install(custom);
        assertSame(custom, Teams.get());
        assertThrows(IllegalStateException.class, () -> Teams.install(new SoloTeamProvider()));
    }
}
