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
 * {@link #buildRoot(int, int)} (called on init and resize) and open with
 * {@code Minecraft.getMinecraft().displayGuiScreen(new MyScreen())}.
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

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        if (root != null) {
            root.draw(mouseX, mouseY, partialTicks);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (root == null || !root.mouseClicked(mouseX, mouseY, mouseButton)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
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
