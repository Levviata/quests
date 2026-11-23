/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import com.levviata.reflibquests.text.RichText;
import com.levviata.reflibquests.text.RichText.Click;
import com.levviata.reflibquests.text.RichText.Element;
import com.levviata.reflibquests.text.RichText.Image;
import com.levviata.reflibquests.text.RichText.Line;
import com.levviata.reflibquests.text.RichText.Page;
import com.levviata.reflibquests.text.RichText.Span;

/** Lays out and draws one page of a quest description (colours, wrapping, images, clickable links). */
final class DescriptionView {

    static final int LINE = 10;
    private static final int LINK_COLOR = 0x7FB2FF;
    private static final int TEXT_COLOR = 0xFFFFFF;

    /** {@code owner} is the span the word came from: all words of one link light up together. */
    record Op(String text, int x, int y, int width, int color, Click click, boolean rainbow, String hover, Object owner) {}

    /** Receives every span that reacts to the mouse: its screen box, its click (or null) and its hover text (or empty). */
    interface LinkSink {
        void accept(int[] box, Click click, String hover);
    }

    record Img(String texture, int x, int y, int w, int h) {}

    record Layout(List<Op> ops, List<Img> images, int height, int pages) {}

    private static List<String> cachedEntries;
    private static int cachedPage = -1;
    private static int cachedWidth = -1;
    private static Layout cached;
    private static final Map<String, Boolean> TEXTURE_EXISTS = new HashMap<>();

    private DescriptionView() {}

    /** Layout for page {@code page} (clamped) of the description; cached while the inputs do not change. */
    static Layout layout(FontRenderer fr, List<String> entries, int page, int width) {
        if (cached != null && entries == cachedEntries && page == cachedPage && width == cachedWidth) {
            return cached;
        }
        List<Page> pages = RichText.parse(entries);
        int index = Math.max(0, Math.min(pages.size() - 1, page));
        List<Op> ops = new ArrayList<>();
        List<Img> images = new ArrayList<>();
        int y = 0;
        List<Line> pageLines = pages.get(index).lines();
        int lastLine = pageLines.size() - 1;
        while (lastLine >= 0 && pageLines.get(lastLine).blank()) {
            lastLine--; // empty rows at the end of a page are not shown
        }
        for (Line line : pageLines.subList(0, lastLine + 1)) {
            if (line.blank()) {
                y += LINE;
                continue;
            }
            if (line.elements().get(0) instanceof Image image) {
                int[] size = imageSize(image, width);
                int x = switch (image.align()) {
                    case LEFT -> 0;
                    case RIGHT -> width - size[0];
                    case CENTER -> (width - size[0]) / 2;
                };
                images.add(new Img(image.texture(), x, y, size[0], size[1]));
                y += size[1] + 4;
                continue;
            }
            int x = 0;
            for (Element element : line.elements()) {
                if (!(element instanceof Span span)) {
                    continue;
                }
                String prefix = prefix(span);
                int color = span.rgb() != RichText.NO_COLOR ? span.rgb() : TEXT_COLOR;
                for (String word : words(span.text())) {
                    String shown = word;
                    int ww = fr.getStringWidth(prefix + shown);
                    if (x > 0 && x + ww > width) {
                        y += LINE;
                        x = 0;
                        shown = stripLeading(word);
                        ww = fr.getStringWidth(prefix + shown);
                    }
                    if (!shown.isEmpty()) {
                        ops.add(new Op(prefix + shown, x, y, ww, color, span.click(), span.rainbow(), span.hover(), span));
                        x += ww;
                    }
                }
            }
            y += LINE;
        }
        cachedEntries = entries;
        cachedPage = page;
        cachedWidth = width;
        cached = new Layout(List.copyOf(ops), List.copyOf(images), y, pages.size());
        return cached;
    }

    /** Draws a layout at (x, y); every clickable span is reported through {@code links} as (screen rect, click). */
    static void draw(FontRenderer fr, Layout layout, int x, int y, int mouseX, int mouseY, LinkSink links) {
        for (Img img : layout.images()) {
            drawImage(fr, img, x + img.x(), y + img.y());
        }
        Object hoveredLink = null;
        for (Op op : layout.ops()) {
            if (op.click() != null && mouseX >= x + op.x() && mouseX < x + op.x() + op.width()
                    && mouseY >= y + op.y() && mouseY < y + op.y() + LINE) {
                hoveredLink = op.owner();
            }
        }
        for (Op op : layout.ops()) {
            boolean glint = op.click() != null && hoveredLink != null && op.owner() == hoveredLink;
            if (op.rainbow()) {
                Ui.drawText(fr, com.levviata.reflibquests.text.RichText.RAINBOW_MARK + op.text(), x + op.x(), y + op.y(), 0xFFFFFFFF, Integer.MAX_VALUE);
            } else {
                fr.drawString(op.text(), x + op.x(), y + op.y(), 0xFF000000 | (glint ? 0xFFFF6B : op.color()));
            }
            if (op.click() != null || !op.hover().isEmpty()) {
                links.accept(new int[] {x + op.x(), y + op.y(), op.width(), LINE}, op.click(), op.hover());
            }
        }
    }

    private static void drawImage(FontRenderer fr, Img img, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        ResourceLocation loc;
        try {
            loc = new ResourceLocation(img.texture());
        } catch (RuntimeException ex) {
            loc = null;
        }
        if (loc != null && textureExists(mc, loc)) {
            mc.getTextureManager().bindTexture(loc);
            GlStateManager.enableBlend();
            GlStateManager.color(1f, 1f, 1f, 1f);
            Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, 1, 1, img.w(), img.h(), 1, 1);
            GlStateManager.disableBlend();
        } else {
            Gui.drawRect(x, y, x + img.w(), y + img.h(), 0x66000000);
            Gui.drawRect(x, y, x + img.w(), y + 1, 0xFF555566);
            Gui.drawRect(x, y + img.h() - 1, x + img.w(), y + img.h(), 0xFF555566);
            Gui.drawRect(x, y, x + 1, y + img.h(), 0xFF555566);
            Gui.drawRect(x + img.w() - 1, y, x + img.w(), y + img.h(), 0xFF555566);
            String name = img.texture().substring(img.texture().lastIndexOf('/') + 1);
            fr.drawString(fr.trimStringToWidth(name, img.w() - 6), x + 3, y + img.h() / 2 - 4, 0x888899);
        }
    }

    static boolean textureExists(Minecraft mc, ResourceLocation loc) {
        return TEXTURE_EXISTS.computeIfAbsent(loc.toString(), k -> {
            try (java.io.Closeable ignored = mc.getResourceManager().getResource(loc).getInputStream()) {
                return true;
            } catch (java.io.IOException | RuntimeException ex) {
                return false;
            }
        });
    }

    /** Forget which textures were missing, e.g. after the supplied-asset pack changed. */
    static void clearCaches() {
        TEXTURE_EXISTS.clear();
        cached = null;
    }

    private static int[] imageSize(Image image, int panelWidth) {
        double w = image.width();
        double h = image.height();
        if (image.fit() || w > panelWidth) {
            h = h * panelWidth / w;
            w = panelWidth;
        }
        return new int[] {Math.max(1, (int) Math.round(w)), Math.max(1, (int) Math.round(h))};
    }

    static String prefix(Span s) {
        StringBuilder sb = new StringBuilder();
        if (s.bold()) sb.append("§l");
        if (s.italic()) sb.append("§o");
        if (s.underline() || s.click() != null) sb.append("§n");
        if (s.strike()) sb.append("§m");
        if (s.obfuscated()) sb.append("§k");
        return sb.toString();
    }

    /** Splits into words that keep their trailing spaces, so wrapping can break between them. */
    private static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ') {
                int end = i + 1;
                while (end < text.length() && text.charAt(end) == ' ') {
                    end++;
                }
                out.add(text.substring(start, end));
                start = end;
                i = end - 1;
            }
        }
        if (start < text.length()) {
            out.add(text.substring(start));
        }
        return out;
    }

    private static String stripLeading(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') {
            i++;
        }
        return s.substring(i);
    }
}
