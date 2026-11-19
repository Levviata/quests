/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.levviata.reflib.api.ReflibApi;

/**
 * The reflib mod entry point. The library has no gameplay content; this class only announces itself so
 * that referencing projects can declare {@code required-after:reflib}.
 */
@Mod(modid = "@{MOD_ID}", name = "@{MOD_NAME}", version = "@{MOD_VERSION}")
public class ReflibMod {

    public static final Logger LOGGER = LogManager.getLogger("@{MOD_NAME}");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Reflib {} loaded (API level {})", "@{MOD_VERSION}", ReflibApi.API_LEVEL);
    }
}
