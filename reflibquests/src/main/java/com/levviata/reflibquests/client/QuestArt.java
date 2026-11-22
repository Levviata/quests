/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

import com.levviata.reflib.api.assets.SuppliedAssets;
import com.levviata.reflibquests.ReflibQuestsMod;
import com.levviata.reflibquests.model.Definitions.Quest;

/**
 * Optional art for the quest map taken from a jar the player supplies (see reflib's {@code SuppliedAssets}):
 * quest shapes ({@code ftbquests:textures/shapes/<name>/background.png} and {@code outline.png}) and the
 * "locked" icon. Nothing is bundled with this mod; when the jar is absent, or lacks a texture, the caller draws
 * its plain coloured squares instead. Shape and icon files are used as they are, tinted by quest state.
 */
@SideOnly(Side.CLIENT)
final class QuestArt {

    static final String DOMAIN = "ftbquests";

    private static final Map<String, Boolean> EXISTS = new HashMap<>();

    private QuestArt() {}

    static boolean available() {
        return SuppliedAssets.isPresent(ReflibQuestsMod.ID);
    }

    /** True if the supplied jar has this file (looked up once per path, then remembered). */
    static boolean has(String path) {
        return EXISTS.computeIfAbsent(path, p -> SuppliedAssets.read(DOMAIN, p) != null);
    }

    /** The shape a quest is drawn with: its own, else its chapter's default, else the book's default, else a circle. */
    static String shapeName(com.levviata.reflibquests.model.QuestBook book,
                            com.levviata.reflibquests.model.Definitions.Chapter chapter, Quest q) {
        String s = q.shape == null ? "" : q.shape.toLowerCase(Locale.ROOT);
        String preset = com.levviata.reflibquests.model.Presets.shape(book, q).toLowerCase(Locale.ROOT);
        if (!preset.isEmpty()) {
            s = preset; // a preset wins over the quest's own shape (measured in FTB 26.1.2)
        }
        if (s.isEmpty() || s.equals("default")) {
            s = chapter == null || chapter.defaultQuestShape == null ? "" : chapter.defaultQuestShape.toLowerCase(Locale.ROOT);
        }
        if (s.isEmpty() || s.equals("default")) {
            s = book.settings.getString("default_quest_shape", "").toLowerCase(Locale.ROOT);
        }
        return s.isEmpty() || s.equals("default") ? "circle" : s;
    }

    /** The shape folder name for a quest: its own, else the default shape (a circle). */
    static String shapeName(Quest q) {
        String s = q.shape == null ? "" : q.shape.toLowerCase(Locale.ROOT);
        return s.isEmpty() || s.equals("default") ? "circle" : s;
    }

    /** @return true if background and outline of this shape are available */
    static boolean hasShape(String name) {
        return available() && has("textures/shapes/" + name + "/background.png") && has("textures/shapes/" + name + "/outline.png");
    }

    static ResourceLocation shapeTexture(String name, String layer) {
        return new ResourceLocation(DOMAIN, "textures/shapes/" + name + "/" + layer + ".png");
    }

    /** @return true if the player's quests jar carries this picture of its gui folder */
    static boolean hasGui(String name) {
        return available() && has("textures/gui/" + name + ".png");
    }

    static ResourceLocation guiTexture(String name) {
        return new ResourceLocation(DOMAIN, "textures/gui/" + name + ".png");
    }

    private static final ResourceLocation TILES = new ResourceLocation("ftblibrary", "textures/gui/background_squares.png");
    static final ResourceLocation DIRT = new ResourceLocation("textures/gui/options_background.png");
    static final ResourceLocation STONE = new ResourceLocation("textures/blocks/stone.png");
    static final ResourceLocation GRASS = new ResourceLocation("textures/blocks/grass_top.png");
    static final ResourceLocation DIRT_BLOCK = new ResourceLocation("textures/blocks/dirt.png");
    private static Boolean tilesAvailable;

    /**
     * The questbook backdrop: the upstream tiled squares texture when the player's jars provide it (the library
     * jar, or any jar that carries it), else a flat dark colour.
     */
    static void background(com.levviata.reflib.gui.Rect r) {
        if (tilesAvailable == null) {
            tilesAvailable = available() && SuppliedAssets.read("ftblibrary", "textures/gui/background_squares.png") != null;
        }
        // stone, darkened, behind the map
        tile(STONE, r.x(), r.y(), r.width(), r.height(), 32, 0xFF57616E);
    }

    /** Repeats a texture over a rectangle; {@code tileSize} is the on-screen size of one repeat. */
    static void tile(ResourceLocation texture, double x, double y, double w, double h, int tileSize, int argb) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24 & 255) / 255f);
        double u = w / tileSize;
        double v = h / tileSize;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        bb.pos(x, y + h, 0).tex(0, v).endVertex();
        bb.pos(x + w, y + h, 0).tex(u, v).endVertex();
        bb.pos(x + w, y, 0).tex(u, 0).endVertex();
        bb.pos(x, y, 0).tex(0, 0).endVertex();
        tess.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** Draw a whole texture stretched over the rectangle, multiplied by an ARGB colour. */
    static void quad(ResourceLocation texture, double x, double y, double w, double h, int argb) {
        quad(texture, x, y, w, h, argb, false);
    }

    /** As {@link #quad(ResourceLocation, double, double, double, double, int)}; {@code smooth} filters the texture linearly (upstream icons are drawn "blurred", not pixelated). */
    static void quad(ResourceLocation texture, double x, double y, double w, double h, int argb, boolean smooth) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        if (smooth) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24 & 255) / 255f);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        bb.pos(x, y + h, 0).tex(0, 1).endVertex();
        bb.pos(x + w, y + h, 0).tex(1, 1).endVertex();
        bb.pos(x + w, y, 0).tex(1, 0).endVertex();
        bb.pos(x, y, 0).tex(0, 0).endVertex();
        tess.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
