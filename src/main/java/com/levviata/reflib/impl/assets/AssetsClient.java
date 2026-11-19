/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.impl.assets;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.IResourcePack;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.levviata.reflib.api.assets.SuppliedAssets;

/** Internal client half of {@link SuppliedAssets}: installs the resource packs and shows the missing-jar notice. */
@SideOnly(Side.CLIENT)
public final class AssetsClient {

    private static final Logger LOGGER = LogManager.getLogger("reflib/assets");
    private boolean warned;

    private AssetsClient() {}

    /** Call at mod construction, before the first resource load. */
    @SuppressWarnings("unchecked")
    public static void install() {
        List<IResourcePack> packs;
        try {
            packs = (List<IResourcePack>) ReflectionHelper.getPrivateValue(
                    Minecraft.class, Minecraft.getMinecraft(), "defaultResourcePacks", "field_110449_ao");
        } catch (RuntimeException ex) {
            LOGGER.error("Could not install supplied-asset resource packs", ex);
            return;
        }
        for (ZipAssetIndex idx : SuppliedAssets.sources()) {
            packs.add(new SuppliedJarPack(idx));
            LOGGER.info("Resource pack installed for {} ({} files)", idx.file().getName(), idx.size());
        }
        MinecraftForge.EVENT_BUS.register(new AssetsClient());
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (warned || !(event.getGui() instanceof GuiMainMenu)) {
            return;
        }
        warned = true;
        if (!SuppliedAssets.missing().isEmpty()) {
            event.setGui(new GuiMissingAssets(event.getGui()));
        }
    }
}
