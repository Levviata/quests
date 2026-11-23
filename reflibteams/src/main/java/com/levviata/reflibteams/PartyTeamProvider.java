/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibteams;

import java.util.Set;
import java.util.UUID;

import com.levviata.reflib.api.team.TeamProvider;

public final class PartyTeamProvider implements TeamProvider {

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
