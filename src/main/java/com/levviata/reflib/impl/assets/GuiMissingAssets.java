/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.impl.assets;

import java.io.IOException;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.assets.SuppliedAssets;

/** One-time notice at the main menu listing the supplied jars that were not found. Informational only. */
@SideOnly(Side.CLIENT)
final class GuiMissingAssets extends GuiScreen {

    private final GuiScreen parent;

    GuiMissingAssets(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 100, height - 40, 200, 20, "Continue without them"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        mc.displayGuiScreen(parent);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int y = 24;
        drawCenteredString(fontRenderer, "Some mods can use files from other mods that you supply yourself", width / 2, y, 0xFFFFFF);
        y += 14;
        drawCenteredString(fontRenderer, "Put the files into: " + SuppliedAssets.folder().getAbsolutePath(), width / 2, y, 0xAAAAAA);
        y += 20;
        List<SuppliedAssets.Requirement> missing = SuppliedAssets.missing();
        for (SuppliedAssets.Requirement r : missing) {
            if (y > height - 60) {
                drawCenteredString(fontRenderer, "...", width / 2, y, 0xFFFFFF);
                break;
            }
            drawCenteredString(fontRenderer, r.displayName() + " (needed by " + r.projectId() + ")", width / 2, y, 0xFFFF55);
            y += 11;
            drawCenteredString(fontRenderer, r.whereToGet(), width / 2, y, 0x55AAFF);
            y += 16;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
