/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflibquests.text.RichText;

/** The small "insert something" dialogs of the description editor. */
@SideOnly(Side.CLIENT)
final class InsertDialogs {

    private InsertDialogs() {}

    /** "Add Image": location, alignment, fit and size; Accept gives the finished image tag. */
    @SideOnly(Side.CLIENT)
    static final class ImageDialog extends SettingsScreen {
        private String texture = "";
        private String align = "Center";
        private boolean fit;
        private int w = 100;
        private int h = 100;
        private final Consumer<String> done;

        ImageDialog(GuiScreen parent, Consumer<String> done) {
            super(parent, "Add Image");
            this.done = done;
            section("Image");
            text("Image Location", () -> texture, v -> texture = v).tip("A texture such as modid:textures/gui/picture.png").defaults("none");
            choice("Alignment", List.of("Left", "Center", "Right"), () -> align, v -> align = v).defaults("Center");
            bool("Fit to Panel Width?", () -> fit, v -> fit = v).tip("Scale the picture to the width of the quest window.").defaults("False");
            number("Image Height", true, () -> String.valueOf(h), v -> h = Math.max(1, Math.min(4096, Integer.parseInt(v)))).defaults("100");
            number("Image Width", true, () -> String.valueOf(w), v -> w = Math.max(1, Math.min(4096, Integer.parseInt(v)))).defaults("100");
        }

        @Override
        protected void accepted() {
            if (!texture.isBlank()) {
                done.accept(RichText.imageTag(texture.trim(), w, h, RichText.Align.valueOf(align.toUpperCase(java.util.Locale.ROOT)), fit));
            }
        }

        @Override
        protected void cancelled() {
            // nothing inserted
        }
    }

    /** "Insert URL/Docs Link": address and the words shown; Accept gives the finished JSON line. */
    @SideOnly(Side.CLIENT)
    static final class LinkDialog extends SettingsScreen {
        private String url = "";
        private String label = "";
        private final Consumer<String> done;

        LinkDialog(GuiScreen parent, Consumer<String> done) {
            super(parent, "Insert URL/Docs Link");
            this.done = done;
            section("Link");
            text("URL or 'docs:' link", () -> url, v -> url = v).tip("https://... for a web page. Players are asked before it opens.");
            text("Text shown", () -> label, v -> label = v).tip("The words the player clicks. Empty: the address itself.");
        }

        @Override
        protected void accepted() {
            if (!url.isBlank()) {
                done.accept(RichText.linkJson(label.isBlank() ? url.trim() : label.trim(), "open_url", url.trim()));
            }
        }

        @Override
        protected void cancelled() {
            // nothing inserted
        }
    }
}
