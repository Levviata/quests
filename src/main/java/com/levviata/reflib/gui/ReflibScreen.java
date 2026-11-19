/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.gui;

import java.io.IOException;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Mouse;

/**
 * A {@link GuiScreen} that hosts a {@link Widget} tree and routes input to it. Subclass, build the tree in
 * {@link #buildRoot(int, int)} and open with
 * {@code Minecraft.getMinecraft().displayGuiScreen(new MyScreen())}.
 *
 * <p>Important: the whole tree is rebuilt every time the window is resized (vanilla calls {@code initGui} again).
 * Keep state in fields of your screen and build widgets from it; widget-internal state (scroll offset, text) is
 * lost on resize.
 */
@SideOnly(Side.CLIENT)
public abstract class ReflibScreen extends GuiScreen {

    private Widget root;

    /** Build the widget tree for the given screen size. */
    protected abstract Widget buildRoot(int screenWidth, int screenHeight);

    @Override
    public void initGui() {
        root = buildRoot(width, height);
    }

    private int lastDragX;
    private int lastDragY;

    /** Override to draw a different backdrop; the default dims the world like vanilla screens. */
    protected void drawBackdrop() {
        drawDefaultBackground();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackdrop();
        if (root != null) {
            root.draw(mouseX, mouseY, partialTicks);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (root != null) {
            java.util.List<String> tip = root.tooltipAt(mouseX, mouseY);
            if (tip != null && !tip.isEmpty()) {
                drawHoveringText(tip, mouseX, mouseY);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        lastDragX = mouseX;
        lastDragY = mouseY;
        if (root == null || !root.mouseClicked(mouseX, mouseY, mouseButton)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (root != null) {
            root.mouseDragged(mouseX, mouseY, clickedMouseButton, mouseX - lastDragX, mouseY - lastDragY);
        }
        lastDragX = mouseX;
        lastDragY = mouseY;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (root == null || !root.mouseReleased(mouseX, mouseY, state)) {
            super.mouseReleased(mouseX, mouseY, state);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (root == null || !root.keyTyped(typedChar, keyCode)) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && root != null) {
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            root.mouseScrolled(mouseX, mouseY, wheel);
        }
    }
}
