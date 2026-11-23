/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibteams;

import net.minecraftforge.common.config.Config;

@Config(modid = "reflibteams")
public class TeamsConfig {

    @Config.Comment("Largest number of players in one party (including the owner).")
    @Config.RangeInt(min = 2, max = 64)
    public static int maxMembers = 8;
}
