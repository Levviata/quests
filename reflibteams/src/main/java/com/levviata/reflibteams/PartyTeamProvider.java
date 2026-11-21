/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibteams;

import java.util.Set;
import java.util.UUID;

import com.levviata.reflib.api.team.TeamProvider;

/** Reflib team provider backed by the current world's {@link PartyManager}. */
public final class PartyTeamProvider implements TeamProvider {

    /** Replaced with the world's saved parties when a server starts. */
    static volatile PartyManager manager = new PartyManager();

    @Override
    public UUID teamOf(UUID player) {
        return manager.teamOf(player);
    }

    @Override
    public Set<UUID> membersOf(UUID team) {
        return manager.membersOf(team);
    }
}
