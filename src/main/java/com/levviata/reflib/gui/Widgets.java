/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

/** The few concrete widgets reflib ships: panel, label, button and a vertically scrolling panel. */
@SideOnly(Side.CLIENT)
public final class Widgets {

    private Widgets() {}

    /** Filled rectangle container. */
    public static class Panel extends Widget {
        private int color;

        public Panel(Rect bounds, int argbColor) {
            super(bounds);
            this.color = argbColor;
        }

        public void setColor(int argbColor) {
            this.color = argbColor;
        }

        @Override
        protected void drawSelf(int mouseX, int mouseY, float partialTicks) {
            if ((color >>> 24) != 0) {
                Gui.drawRect(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), color);
            }
        }
    }

    /** Single-line text. */
    public static class Label extends Widget {
        private String text;
        private int color;

        public Label(Rect bounds, String text, int rgbColor) {
            super(bounds);
            this.text = text;
            this.color = rgbColor;
        }

        public void setText(String text) {
            this.text = text;
        }

        @Override
        protected void drawSelf(int mouseX, int mouseY, float partialTicks) {
            Minecraft.getMinecraft().fontRenderer.drawString(text, bounds.x(), bounds.y(), color);
        }
    }

    /** Clickable button; the action runs on the client thread. */
    public static class Button extends Widget {
        private String text;
        private final Runnable action;

        public Button(Rect bounds, String text, Runnable action) {
            super(bounds);
            this.text = text;
            this.action = action;
        }

        public void setText(String text) {
            this.text = text;
        }

        @Override
        protected void drawSelf(int mouseX, int mouseY, float partialTicks) {
            boolean hover = bounds.contains(mouseX, mouseY);
            Gui.drawRect(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                    hover ? 0xFF6B6B8F : 0xFF4A4A68);
            var font = Minecraft.getMinecraft().fontRenderer;
            font.drawStringWithShadow(text, bounds.x() + (bounds.width() - font.getStringWidth(text)) / 2f,
                    bounds.y() + (bounds.height() - 8) / 2f, 0xFFFFFF);
        }

        @Override
        protected boolean onClick(int mouseX, int mouseY, int button) {
            if (button == 0) {
                action.run();
                return true;
            }
            return false;
        }
    }

    /**
     * Vertical scroll container. Children are positioned in content space (y = 0 at the top of the content)
     * and drawn translated by the scroll offset, clipped to the panel bounds. Limitation: only direct
     * children are translated, so they must be leaf widgets (no nested containers).
     */
    public static class ScrollPanel extends Widget {
        private final ScrollModel model;

        public ScrollPanel(Rect bounds, int contentHeight) {
            super(bounds);
            this.model = new ScrollModel(contentHeight, bounds.height());
        }

        public ScrollModel model() {
            return model;
        }

        @Override
        public void setBounds(Rect bounds) {
            super.setBounds(bounds);
            model.resize(model.maxOffset() + bounds.height(), bounds.height());
        }

        @Override
        public void draw(int mouseX, int mouseY, float partialTicks) {
            if (!visible) {
                return;
            }
            ScaledResolution res = new ScaledResolution(Minecraft.getMinecraft());
            int scale = res.getScaleFactor();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(bounds.x() * scale,
                    Minecraft.getMinecraft().displayHeight - (bounds.y() + bounds.height()) * scale,
                    bounds.width() * scale, bounds.height() * scale);
            for (Widget child : children()) {
                Rect original = child.bounds();
                child.setBounds(original.translate(bounds.x(), bounds.y() - model.offset()));
                child.draw(mouseX, mouseY, partialTicks);
                child.setBounds(original);
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (!visible || !bounds.contains(mouseX, mouseY)) {
                return false;
            }
            // translate the pointer into content space instead of moving the children
            int cx = mouseX - bounds.x();
            int cy = mouseY - bounds.y() + model.offset();
            for (int i = children().size() - 1; i >= 0; i--) {
                if (children().get(i).mouseClicked(cx, cy, button)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected boolean onScroll(int mouseX, int mouseY, int delta) {
            model.scrollBy(-Integer.signum(delta) * 12);
            return true;
        }
    }
}
