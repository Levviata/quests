/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.team;

import java.util.Set;
import java.util.UUID;

/**
 * Source of team membership for referencing projects that track progress per team. Reflib only defines the
 * contract and a trivial default ({@link SoloTeamProvider}); real party teams come from a project or an
 * adapter for another team mod.
 */
public interface TeamProvider {

    /** @return the id of the team the player belongs to; never null (a player is at least in their own team) */
    UUID teamOf(UUID player);

    /** @return the members of a team, or an empty set if the team is unknown */
    Set<UUID> membersOf(UUID team);
}
