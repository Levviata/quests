/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;

import com.levviata.reflib.api.ReflibApi;
import com.levviata.reflib.api.assets.SuppliedAssets;
import com.levviata.reflib.impl.assets.AssetsClient;

/**
 * The reflib mod entry point. The library has no gameplay content; this class only announces itself so
 * that referencing projects can declare {@code required-after:reflib}.
 */
@Mod(modid = "@{MOD_ID}", name = "@{MOD_NAME}", version = "@{MOD_VERSION}")
public class ReflibMod {

    public static final Logger LOGGER = LogManager.getLogger("@{MOD_NAME}");

    /**
     * Scan the supplied-assets folder and, on the client, install its resource packs. This must happen at
     * construction: the first resource load comes before any mod's later events.
     */
    @Mod.EventHandler
    public void construct(FMLConstructionEvent event) {
        File configDir = Loader.instance().getConfigDir();
        File gameDir = configDir != null && configDir.getParentFile() != null ? configDir.getParentFile() : new File(".");
        SuppliedAssets.init(gameDir);
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            AssetsClient.install();
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Reflib {} loaded (API level {})", "@{MOD_VERSION}", ReflibApi.API_LEVEL);
    }
}
