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

import org.lwjgl.input.Keyboard;
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

    /**
     * Single-line text input. Clicking focuses it; while focused it takes printable keys, Backspace, Delete,
     * the arrow keys, Home and End. Enter, Tab and Escape are left to the screen (return false) so it can
     * confirm, switch field or cancel.
     */
    public static class TextField extends Widget {
        private final TextModel model;
        private final String label;
        private boolean focused;

        public TextField(Rect bounds, String label, String initial, int maxLength) {
            super(bounds);
            this.label = label;
            this.model = new TextModel(initial, maxLength);
        }

        public String text() {
            return model.text();
        }

        public void setText(String value) {
            model.set(value);
        }

        public boolean isFocused() {
            return focused;
        }

        public void setFocused(boolean focused) {
            this.focused = focused;
        }

        @Override
        protected void drawSelf(int mouseX, int mouseY, float partialTicks) {
            var fr = Minecraft.getMinecraft().fontRenderer;
            Gui.drawRect(bounds.x() - 1, bounds.y() - 1, bounds.x() + bounds.width() + 1, bounds.y() + bounds.height() + 1,
                    focused ? 0xFFB0B0FF : 0xFF606070);
            Gui.drawRect(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), 0xFF050508);
            String shown = model.text();
            int textX = bounds.x() + 4;
            int textY = bounds.y() + (bounds.height() - 8) / 2;
            if (shown.isEmpty() && !focused) {
                fr.drawStringWithShadow(label, textX, textY, 0x666677);
                return;
            }
            // keep the caret in view by dropping characters from the left when the text is too wide
            int start = 0;
            while (start < model.caret() && fr.getStringWidth(shown.substring(start, model.caret())) > bounds.width() - 10) {
                start++;
            }
            String visible = fr.trimStringToWidth(shown.substring(start), bounds.width() - 8);
            fr.drawStringWithShadow(visible, textX, textY, 0xFFFFFF);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = textX + fr.getStringWidth(shown.substring(start, model.caret()));
                Gui.drawRect(cx, textY - 1, cx + 1, textY + 9, 0xFFFFFFFF);
            }
        }

        @Override
        protected boolean onClick(int mouseX, int mouseY, int button) {
            focused = true;
            var fr = Minecraft.getMinecraft().fontRenderer;
            model.caretAtOffset(mouseX - bounds.x() - 4, i -> fr.getStringWidth(model.text().substring(0, i)));
            return true;
        }

        @Override
        protected boolean onKey(char typed, int keyCode) {
            if (!focused) {
                return false;
            }
            switch (keyCode) {
                case Keyboard.KEY_RETURN:
                case Keyboard.KEY_NUMPADENTER:
                case Keyboard.KEY_TAB:
                case Keyboard.KEY_ESCAPE:
                    return false;
                case Keyboard.KEY_BACK:
                    model.backspace();
                    return true;
                case Keyboard.KEY_DELETE:
                    model.delete();
                    return true;
                case Keyboard.KEY_LEFT:
                    model.left();
                    return true;
                case Keyboard.KEY_RIGHT:
                    model.right();
                    return true;
                case Keyboard.KEY_HOME:
                    model.home();
                    return true;
                case Keyboard.KEY_END:
                    model.end();
                    return true;
                default:
                    return model.insert(typed);
            }
        }
    }
}
