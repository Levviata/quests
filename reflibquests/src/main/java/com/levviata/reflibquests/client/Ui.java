/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.assets.SuppliedAssets;
import com.levviata.reflib.gui.Rect;

/**
 * Small drawing helpers and the names of the optional icons taken from the player's supplied jars. Nothing here
 * is bundled; every helper returns false (so the caller can draw a plain fallback) when the art is missing.
 */
@SideOnly(Side.CLIENT)
final class Ui {

    static final String QUESTS = "ftbquests";
    static final String LIBRARY = "ftblibrary";

    private static final Map<String, Boolean> EXISTS = new HashMap<>();

    private Ui() {}

    static boolean has(String domain, String path) {
        return QuestArt.available() && EXISTS.computeIfAbsent(domain + ":" + path, k -> SuppliedAssets.read(domain, path) != null);
    }

    /** Draws a supplied texture stretched over the box; returns false if the texture is not available. */
    static boolean icon(String domain, String path, double x, double y, double w, double h, int argb) {
        if (!has(domain, path)) {
            return false;
        }
        QuestArt.quad(new ResourceLocation(domain, path), x, y, w, h, argb);
        return true;
    }

    static boolean questsGui(String name, double x, double y, double w, double h, int argb) {
        return icon(QUESTS, "textures/gui/" + name + ".png", x, y, w, h, argb);
    }

    // ---- the "completed" tick: the shape from the supplied checkmark.svg (viewBox 40 40 640 510)
    private static final double[][] TICK = {
        {158.5, 218.5}, {272.5, 332.5}, {550, 55}, {650, 155}, {272.5, 532.5}, {58.5, 318.5}};
    private static final double[][] TICK_INNER = inset(TICK, 18);
    private static final int TICK_BORDER = 0xFF468100;
    private static final int TICK_FILL = 0xFF9BC600;

    /** The completed-tick badge (quests, tasks, rewards): lime body with a dark green border, as in the SVG. */
    static boolean check(double x, double y, double size, int argb) {
        double k = size / 640.0;
        double ox = x - 40 * k;
        double oy = y + (size - 510 * k) / 2 - 40 * k;
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(0, 0, 400); // in front of item pictures
        tick(TICK, ox, oy, k, TICK_BORDER);
        tick(TICK_INNER, ox, oy, k, TICK_FILL);
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        return true;
    }

    /** Two convex halves of the tick polygon, filled flat. */
    private static void tick(double[][] p, double ox, double oy, double k, int argb) {
        quad(p, new int[] {5, 0, 1, 4}, ox, oy, k, argb);
        quad(p, new int[] {1, 2, 3, 4}, ox, oy, k, argb);
    }

    private static void quad(double[][] p, int[] idx, double ox, double oy, double k, int argb) {
        net.minecraft.client.renderer.GlStateManager.disableCull();
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24 & 255) / 255f);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder bb = tess.getBuffer();
        bb.begin(org.lwjgl.opengl.GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        for (int i : idx) {
            bb.pos(ox + p[i][0] * k, oy + p[i][1] * k, 0).endVertex();
        }
        tess.draw();
        net.minecraft.client.renderer.GlStateManager.color(1f, 1f, 1f, 1f);
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.enableCull();
    }

    /** Moves every edge of a polygon inwards by d (mitred corners); used to make the tick's inner border. */
    private static double[][] inset(double[][] p, double d) {
        int n = p.length;
        double area = 0;
        for (int i = 0; i < n; i++) {
            double[] a = p[i];
            double[] b = p[(i + 1) % n];
            area += a[0] * b[1] - b[0] * a[1];
        }
        double sign = area > 0 ? 1 : -1;
        double[][] out = new double[n][2];
        for (int i = 0; i < n; i++) {
            double[] prev = p[(i + n - 1) % n];
            double[] cur = p[i];
            double[] next = p[(i + 1) % n];
            double[] n1 = normal(prev, cur, sign);
            double[] n2 = normal(cur, next, sign);
            double dot = n1[0] * n2[0] + n1[1] * n2[1];
            out[i][0] = cur[0] + d * (n1[0] + n2[0]) / (1 + dot);
            out[i][1] = cur[1] + d * (n1[1] + n2[1]) / (1 + dot);
        }
        return out;
    }

    private static double[] normal(double[] a, double[] b, double sign) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double len = Math.sqrt(dx * dx + dy * dy);
        return new double[] {-dy / len * sign, dx / len * sign};
    }

    // ---- the checkmark TASK icon: the pixel-art check from the supplied jar, grey until done
    private static final Map<String, ResourceLocation> GRAY = new HashMap<>();

    static boolean taskCheck(double x, double y, double size, boolean done) {
        if (done) {
            return icon(LIBRARY, "textures/icons/check.png", x, y, size, size, 0xFFFFFFFF);
        }
        return grayIcon(LIBRARY, "textures/icons/check.png", x, y, size, size, 0xFFFFFFFF);
    }

    /** The picture in greys, made once in memory from the supplied file (never saved). */
    static boolean grayIcon(String domain, String path, double x, double y, double w, double h, int argb) {
        if (!has(domain, path)) {
            return false;
        }
        ResourceLocation loc = GRAY.get(domain + ":" + path);
        if (loc == null) {
            try {
                java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(SuppliedAssets.read(domain, path)));
                java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(src.getWidth(), src.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int py = 0; py < src.getHeight(); py++) {
                    for (int px = 0; px < src.getWidth(); px++) {
                        int c = src.getRGB(px, py);
                        int lum = (int) (0.30 * (c >> 16 & 255) + 0.59 * (c >> 8 & 255) + 0.11 * (c & 255));
                        out.setRGB(px, py, (c & 0xFF000000) | lum << 16 | lum << 8 | lum);
                    }
                }
                loc = net.minecraft.client.Minecraft.getMinecraft().getTextureManager()
                        .getDynamicTextureLocation("reflibquests_gray", new net.minecraft.client.renderer.texture.DynamicTexture(out));
                GRAY.put(domain + ":" + path, loc);
            } catch (java.io.IOException | RuntimeException ex) {
                return false;
            }
        }
        QuestArt.quad(loc, x, y, w, h, argb);
        return true;
    }

    static boolean alert(double x, double y, double size) {
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(0, 0, 400); // in front of item pictures
        boolean drawn = questsGui("alert", x, y, size, size, 0xFFFFFFFF);
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        return drawn;
    }

    /** The "missing item" picture for items that do not exist in this game version. */
    static boolean missingItem(double x, double y, double size) {
        return icon(LIBRARY, "textures/item/missing_item.png", x, y, size, size, 0xFFFFFFFF);
    }

    /** A small filled triangle pointing right (the "open chapters" handle). */
    static void triangleRight(double x, double y, double h, int argb) {
        net.minecraft.client.renderer.GlStateManager.disableCull();
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24 & 255) / 255f);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder bb = tess.getBuffer();
        bb.begin(org.lwjgl.opengl.GL11.GL_TRIANGLES, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
        bb.pos(x, y, 0).endVertex();
        bb.pos(x, y + h, 0).endVertex();
        bb.pos(x + h * 0.7, y + h / 2, 0).endVertex();
        tess.draw();
        net.minecraft.client.renderer.GlStateManager.color(1f, 1f, 1f, 1f);
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.enableCull();
    }

    private static final Map<String, ResourceLocation> OPAQUE = new HashMap<>();

    /**
     * A shape's background picture with every visible pixel made fully opaque (the radial shading is kept as brightness),
     * made once in memory from the supplied file and never saved. Null when the shape art is missing.
     */
    static ResourceLocation opaqueShape(String shape) {
        String path = "textures/shapes/" + shape + "/background.png";
        if (!has(QUESTS, path)) {
            return null;
        }
        ResourceLocation cached = OPAQUE.get(shape);
        if (cached != null) {
            return cached;
        }
        try {
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(SuppliedAssets.read(QUESTS, path)));
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(src.getWidth(), src.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int py = 0; py < src.getHeight(); py++) {
                for (int px = 0; px < src.getWidth(); px++) {
                    int a = src.getRGB(px, py) >>> 24;
                    if (a == 0) {
                        continue;
                    }
                    // the upstream picture fades from about half opacity at the rim to full in the middle: keep that as a
                    // clear radial shading (dark rim, light middle) while the node itself stays fully solid
                    double t = Math.max(0, Math.min(1, (a - 120) / 135.0));
                    int v = (int) (255 * (0.58 + 0.42 * t));
                    out.setRGB(px, py, 0xFF000000 | v << 16 | v << 8 | v);
                }
            }
            ResourceLocation loc = net.minecraft.client.Minecraft.getMinecraft().getTextureManager()
                    .getDynamicTextureLocation("reflibquests_shape_" + shape, new net.minecraft.client.renderer.texture.DynamicTexture(out));
            OPAQUE.put(shape, loc);
            return loc;
        } catch (java.io.IOException | RuntimeException ex) {
            return null;
        }
    }

    private static final ResourceLocation RECIPE_BOOK = new ResourceLocation("textures/gui/recipe_book.png");

    /**
     * The game's own "next page" arrow from the recipe book (11 x 17 pixels at (1, 208); the lit version is at (1, 226)),
     * drawn with the given opacity from 0 to 1.
     */
    static void vanillaPageArrow(int x, int y, boolean hover, float alpha) {
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(RECIPE_BOOK);
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(
                net.minecraft.client.renderer.GlStateManager.SourceFactor.SRC_ALPHA,
                net.minecraft.client.renderer.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                net.minecraft.client.renderer.GlStateManager.SourceFactor.ONE, net.minecraft.client.renderer.GlStateManager.DestFactor.ZERO);
        net.minecraft.client.renderer.GlStateManager.color(1f, 1f, 1f, alpha);
        net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(x, y, 1, hover ? 226 : 208, 11, 17, 256, 256);
        net.minecraft.client.renderer.GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static final net.minecraft.client.gui.Gui GUI = new net.minecraft.client.gui.Gui();
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");

    /** A button drawn with the game's own button texture (the same picture as every vanilla button). */
    static void vanillaButton(Rect r, boolean hover) {
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(WIDGETS);
        net.minecraft.client.renderer.GlStateManager.color(1f, 1f, 1f, 1f);
        int v = 46 + (hover ? 2 : 1) * 20;
        int half = r.width() / 2;
        GUI.drawTexturedModalRect(r.x(), r.y(), 0, v, half, r.height());
        GUI.drawTexturedModalRect(r.x() + half, r.y(), 200 - (r.width() - half), v, r.width() - half, r.height());
    }

    /** Width of a display string, not counting rainbow markers. */
    static int width(net.minecraft.client.gui.FontRenderer fr, String text) {
        return fr.getStringWidth(text.replace(String.valueOf(com.levviata.reflibquests.text.RichText.RAINBOW_MARK), ""));
    }

    /** A colour that drifts through the rainbow along the text and over time. */
    static int rainbow(int index) {
        float hue = ((System.currentTimeMillis() % 3000L) / 3000f + index * 0.045f) % 1f;
        return java.awt.Color.HSBtoRGB(hue, 0.75f, 1f) & 0xFFFFFF;
    }

    /**
     * Draws text with shadow, cut to {@code maxWidth}; after a rainbow marker every letter gets its own moving colour.
     * Formatting codes before and inside the rainbow part still apply.
     */
    static void drawText(net.minecraft.client.gui.FontRenderer fr, String text, int x, int y, int color, int maxWidth) {
        drawText(fr, text, x, y, color, maxWidth, true);
    }

    static void drawText(net.minecraft.client.gui.FontRenderer fr, String text, int x, int y, int color, int maxWidth, boolean shadow) {
        char mark = com.levviata.reflibquests.text.RichText.RAINBOW_MARK;
        int at = text.indexOf(mark);
        if (at < 0) {
            fr.drawString(fr.trimStringToWidth(text, maxWidth), x, y, color, shadow);
            return;
        }
        String before = fr.trimStringToWidth(text.substring(0, at), maxWidth);
        fr.drawString(before, x, y, color, shadow);
        int cx = x + fr.getStringWidth(before);
        String fmt = "";
        int letter = 0;
        String rest = text.substring(at + 1).replace(String.valueOf(mark), "");
        for (int i = 0; i < rest.length() && cx - x < maxWidth; i++) {
            char c = rest.charAt(i);
            if (c == '\u00a7' && i + 1 < rest.length()) {
                char code = Character.toLowerCase(rest.charAt(i + 1));
                fmt = code == 'r' ? "" : "0123456789abcdef".indexOf(code) >= 0 ? "" : fmt + "\u00a7" + code;
                i++;
                continue;
            }
            String glyph = fmt + c;
            fr.drawString(glyph, cx, y, 0xFF000000 | rainbow(letter++), shadow);
            cx += fr.getStringWidth(glyph);
        }
    }

    /** The game's tooltip look, but every line may contain a rainbow marker. */
    static void drawTooltip(net.minecraft.client.gui.FontRenderer fr, java.util.List<String> lines, int mouseX, int mouseY, int screenW, int screenH) {
        if (lines.isEmpty()) {
            return;
        }
        int w = 0;
        for (String l : lines) {
            w = Math.max(w, width(fr, l));
        }
        int h = 8 + (lines.size() - 1) * 11 + (lines.size() > 1 ? 2 : 0); // a little looser than vanilla so an underline never touches the next line
        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + w + 4 > screenW) {
            x = mouseX - 16 - w;
        }
        x = Math.max(4, x);
        if (y + h + 6 > screenH) {
            y = screenH - h - 6;
        }
        y = Math.max(4, y);
        net.minecraft.client.renderer.GlStateManager.disableLighting();
        net.minecraft.client.renderer.GlStateManager.disableDepth();
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(0, 0, 300);
        Gui.drawRect(x - 3, y - 4, x + w + 3, y + h + 4, 0xF0100010);
        Gui.drawRect(x - 3, y - 3, x + w + 3, y - 2, 0x505000FF);
        Gui.drawRect(x - 3, y + h + 2, x + w + 3, y + h + 3, 0x5028007F);
        Gui.drawRect(x - 3, y - 3, x - 2, y + h + 3, 0x505000FF);
        Gui.drawRect(x + w + 2, y - 3, x + w + 3, y + h + 3, 0x5028007F);
        int ly = y;
        for (int i = 0; i < lines.size(); i++) {
            drawText(fr, lines.get(i), x, ly, 0xFFFFFFFF, Integer.MAX_VALUE, true);
            ly += i == 0 ? 12 : 11;
        }
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        net.minecraft.client.renderer.GlStateManager.enableDepth();
    }

    /** Flat widget box: one-pixel dark border around a fill. */
    static void box(Rect r, int fill) {
        Gui.drawRect(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), 0xFF1B1D1E);
        Gui.drawRect(r.x() + 1, r.y() + 1, r.x() + r.width() - 1, r.y() + r.height() - 1, fill);
    }

    /** One-pixel outline only. */
    static void outline(Rect r, int argb) {
        Gui.drawRect(r.x(), r.y(), r.x() + r.width(), r.y() + 1, argb);
        Gui.drawRect(r.x(), r.y() + r.height() - 1, r.x() + r.width(), r.y() + r.height(), argb);
        Gui.drawRect(r.x(), r.y(), r.x() + 1, r.y() + r.height(), argb);
        Gui.drawRect(r.x() + r.width() - 1, r.y(), r.x() + r.width(), r.y() + r.height(), argb);
    }
}
