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
    private static final long TITLE_MS = 2000L;
    private static final long FADE_MS = 300L;
    /** Toasts of ours on screen or waiting: the sound plays once for a batch, not once per toast. */
    static int live;

    private final String title;
    private final String subtitle;
    @Nullable
    private final ItemStack icon;

    /** A dark notice with the blue "i" instead of an item: yellow title, white text (like FTB's "Copied object:"). */
    private final boolean info;
    /** The colour of the first line (yellow; pink for a finished chapter). */
    int titleColor = 0xFFFF55;
    /** A refusal: a dark box with a red "no" sign and one yellow line. */
    private boolean blocked;

    static QuestToast blocked(String text) {
        QuestToast t = new QuestToast("", text, null, true);
        t.blocked = true;
        return t;
    }

    QuestToast(String title, String subtitle, @Nullable ItemStack icon) {
        this(title, subtitle, icon, false);
    }

    QuestToast(String title, String subtitle, @Nullable ItemStack icon, boolean info) {
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
        this.info = info;
    }

    @Override
    public Visibility draw(GuiToast gui, long delta) {
        if (blocked) {
            net.minecraft.client.gui.Gui.drawRect(0, 0, 160, 32, 0xF0151515);
            net.minecraft.client.gui.Gui.drawRect(0, 0, 160, 1, 0xFF3A3A3A);
            net.minecraft.client.gui.Gui.drawRect(0, 31, 160, 32, 0xFF3A3A3A);
            net.minecraft.client.gui.Gui.drawRect(0, 0, 1, 32, 0xFF3A3A3A);
            net.minecraft.client.gui.Gui.drawRect(159, 0, 160, 32, 0xFF3A3A3A);
            drawNoSign(17, 16, 8);
            Ui.drawText(gui.getMinecraft().fontRenderer, subtitle, 32, 12, 0xFFFFFF55, 124);
            return delta >= SHOW_MS ? Visibility.HIDE : Visibility.SHOW;
        }
        if (info) {
            net.minecraft.client.gui.Gui.drawRect(0, 0, 160, 32, 0xF0151515);
            net.minecraft.client.gui.Gui.drawRect(0, 0, 160, 1, 0xFF3A3A3A);
            net.minecraft.client.gui.Gui.drawRect(0, 31, 160, 32, 0xFF3A3A3A);
            net.minecraft.client.gui.Gui.drawRect(0, 0, 1, 32, 0xFF3A3A3A);
            net.minecraft.client.gui.Gui.drawRect(159, 0, 160, 32, 0xFF3A3A3A);
            GlStateManager.color(1f, 1f, 1f, 1f);
            Ui.questsGui("info", 9, 8, 16, 16, 0xFF4DA6FF);
            Ui.drawText(gui.getMinecraft().fontRenderer, title, 30, 7, 0xFFFFFF55, 124);
            Ui.drawText(gui.getMinecraft().fontRenderer, subtitle, 30, 18, 0xFFFFFFFF, 124);
            return delta >= SHOW_MS ? Visibility.HIDE : Visibility.SHOW;
        }
        net.minecraft.client.gui.FontRenderer fr = gui.getMinecraft().fontRenderer;
        // a name that fits shows under the title at once; a long one (it wraps) comes after the title, fading across
        java.util.List<String> nameLines = fr.listFormattedStringToWidth(subtitle, 118);
        boolean sequential = nameLines.size() > 1;
        boolean namePhase = sequential && delta >= TITLE_MS;
        java.util.List<String> lines = sequential
                ? (namePhase ? nameLines : fr.listFormattedStringToWidth(title, 118))
                : new java.util.ArrayList<>();
        if (!sequential) {
            lines.add(title);
            if (!subtitle.isEmpty()) {
                lines.add(subtitle);
            }
        }
        int h = Math.max(32, lines.size() * 10 + 12);
        net.minecraft.client.gui.Gui.drawRect(0, 0, 160, h, 0xFF0E0E0E);
        net.minecraft.client.gui.Gui.drawRect(1, 1, 159, h - 1, 0xFF4A4A4A);
        net.minecraft.client.gui.Gui.drawRect(3, 3, 157, h - 3, 0xFF1C1C1C);
        float fade = 1f;
        if (sequential) {
            fade = delta < TITLE_MS - FADE_MS ? 1f
                    : delta < TITLE_MS ? (TITLE_MS - delta) / (float) FADE_MS
                    : delta < TITLE_MS + FADE_MS ? (delta - TITLE_MS) / (float) FADE_MS : 1f;
        }
        int alpha = Math.max(4, Math.min(255, Math.round(fade * 255)));
        GlStateManager.enableBlend();
        int ty = (h - lines.size() * 10 + 1) / 2;
        for (int i = 0; i < lines.size(); i++) {
            boolean isTitle = !sequential ? i == 0 : !namePhase;
            int rgb = isTitle ? titleColor : 0xFFFFFF;
            fr.drawStringWithShadow(lines.get(i), 30, ty, alpha << 24 | rgb);
            ty += 10;
        }
        if (icon != null && !icon.isEmpty()) {
            RenderHelper.enableGUIStandardItemLighting();
            gui.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(icon, 8, (h - 16) / 2);
            RenderHelper.disableStandardItemLighting();
        }
        if (delta >= SHOW_MS + (sequential ? TITLE_MS : 0)) {
            live = Math.max(0, live - 1);
            return Visibility.HIDE;
        }
        return Visibility.SHOW;
    }

    /** A red circle with a bar across it, centred on (cx, cy). */
    private static void drawNoSign(double cx, double cy, double r) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 0.15f, 0.1f, 1f);
        org.lwjgl.opengl.GL11.glLineWidth(2.2f);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder bb = tess.getBuffer();
        bb.begin(org.lwjgl.opengl.GL11.GL_LINE_LOOP, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        for (int i = 0; i < 28; i++) {
            double a = i / 28.0 * Math.PI * 2;
            bb.pos(cx + Math.cos(a) * r, cy + Math.sin(a) * r, 0).endVertex();
        }
        tess.draw();
        bb.begin(org.lwjgl.opengl.GL11.GL_LINES, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        bb.pos(cx - r * 0.7, cy + r * 0.7, 0).endVertex();
        bb.pos(cx + r * 0.7, cy - r * 0.7, 0).endVertex();
        tess.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
    }
}
