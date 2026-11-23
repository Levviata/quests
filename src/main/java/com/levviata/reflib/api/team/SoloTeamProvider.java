/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.team;

import java.util.Set;
import java.util.UUID;

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
