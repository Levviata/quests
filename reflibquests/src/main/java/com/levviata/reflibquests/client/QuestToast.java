/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import javax.annotation.Nullable;

import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** A corner pop-up ("Quest completed!", "Reward Collected!") using the game's own toast system. */
@SideOnly(Side.CLIENT)
final class QuestToast implements IToast {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/toasts.png");
    private static final long SHOW_MS = 5000L;

    private final String title;
    private final String subtitle;
    @Nullable
    private final ItemStack icon;

    QuestToast(String title, String subtitle, @Nullable ItemStack icon) {
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
    }

    @Override
    public Visibility draw(GuiToast gui, long delta) {
        gui.getMinecraft().getTextureManager().bindTexture(TEXTURE);
        GlStateManager.color(1f, 1f, 1f, 1f);
        gui.drawTexturedModalRect(0, 0, 0, 0, 160, 32);
        Ui.drawText(gui.getMinecraft().fontRenderer, title, 30, 7, 0xFFFFFF55, 124);
        Ui.drawText(gui.getMinecraft().fontRenderer, subtitle, 30, 18, 0xFF55FF55, 124);
        if (icon != null && !icon.isEmpty()) {
            RenderHelper.enableGUIStandardItemLighting();
            gui.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(icon, 8, 8);
            RenderHelper.disableStandardItemLighting();
        }
        return delta >= SHOW_MS ? Visibility.HIDE : Visibility.SHOW;
    }
}
