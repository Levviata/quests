/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.team;

import java.util.Set;
import java.util.UUID;

/** Default provider: every player is the only member of a team whose id equals the player's id. */
public final class SoloTeamProvider implements TeamProvider {

    @Override
    public UUID teamOf(UUID player) {
        return player;
    }

    @Override
    public Set<UUID> membersOf(UUID team) {
        return Set.of(team);
    }
}
