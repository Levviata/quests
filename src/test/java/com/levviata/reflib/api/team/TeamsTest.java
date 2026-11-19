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
    void listenersAreNotifiedAndCanBeRemovedAndOneFailureDoesNotStopOthers() {
        java.util.List<UUID> seen = new java.util.ArrayList<>();
        Teams.ChangeListener failing = p -> {
            throw new IllegalStateException("boom");
        };
        Teams.ChangeListener recording = seen::add;
        Teams.addListener(failing);
        Teams.addListener(recording);
        UUID p = UUID.randomUUID();
        Teams.notifyChanged(p);
        assertEquals(java.util.List.of(p), seen);
        Teams.removeListener(recording);
        Teams.notifyChanged(UUID.randomUUID());
        assertEquals(1, seen.size());
    }

    @Test
    void secondReplacementIsRefused() {
        TeamProvider custom = new SoloTeamProvider();
        Teams.install(custom);
        assertSame(custom, Teams.get());
        assertThrows(IllegalStateException.class, () -> Teams.install(new SoloTeamProvider()));
    }
}
