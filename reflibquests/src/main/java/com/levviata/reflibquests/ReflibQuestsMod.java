/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.levviata.reflib.api.project.ReflibProjects;

/**
 * Reflibquests: an independent quest mod for 1.12.2 built on reflib. Not affiliated with any other quest mod.
 */
@Mod(modid = ReflibQuestsMod.ID, name = "Reflib Quests", version = ReflibQuestsMod.VERSION,
        dependencies = "required-after:reflib")
public class ReflibQuestsMod {

    public static final String ID = "reflibquests";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LogManager.getLogger("reflibquests");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ReflibProjects.register(ID, VERSION, 2);
        LOGGER.info("Reflib Quests {} loaded", VERSION);
    }
}
