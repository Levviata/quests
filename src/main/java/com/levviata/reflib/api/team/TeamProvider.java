/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.team;

import java.util.Set;
import java.util.UUID;

public interface TeamProvider {

    UUID teamOf(UUID player);

    Set<UUID> membersOf(UUID team);
}
