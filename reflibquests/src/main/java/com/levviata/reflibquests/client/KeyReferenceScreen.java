/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.IOException;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.levviata.reflib.gui.Rect;

/** "Key Reference": every key and mouse action of the quest book, as it is today. */
@SideOnly(Side.CLIENT)
final class KeyReferenceScreen extends GuiScreen {

    private static final int ROW = 11;

    private final GuiScreen parent;
    private int scroll;

    KeyReferenceScreen(GuiScreen parent) {
        this.parent = parent;
    }

    /** {heading} or {key, action}. */
    private static String[][] lines() {
        return new String[][] {
            {"General Controls"},
            {ClientBootstrap.openKeyName(), "Open Quests"},
            {"Tab", "Next Chapter"},
            {"SHIFT + Tab", "Previous Chapter"},
            {"Space", "Recenter GUI"},
            {"CTRL + F", "Search"},
            {"F1 (hold)", "See Extended Quest Info"},
            {"Arrow Keys", "Scroll the map one grid step"},
            {"Left Mouse", "View Quest Details"},
            {"Left Mouse: Drag", "Move the map"},
            {"Mouse Wheel", "Zoom In / Out"},
            {"Backspace", "While viewing quest, go to last-viewed quest"},
            {"CTRL + P", "Player Preferences"},
            {"Editor Mode"},
            {"CTRL + Left Mouse", "Toggle Selection"},
            {"LEFT ALT + Left Mouse", "Properties Editor"},
            {"RIGHT ALT + Left Mouse", "Copy Quest"},
            {"ALT + Left Mouse: Drag", "Select Quests in Drag Box (from free space)"},
            {"Middle Mouse", "Move Selected Quest(s)"},
            {"Right Mouse", "Context Menu"},
            {"Middle Mouse: Drag", "Select Quests in Drag Box"},
            {"CTRL + Middle Mouse: Drag", "Toggle Selection in Box"},
            {"CTRL + A", "Select All"},
            {"CTRL + D / Escape", "Select None"},
            {"CTRL + Arrow Keys", "Move Selected one grid step"},
            {"Delete", "Delete Hovered / Selected"},
            {"SHIFT + Delete", "Force-delete Hovered / Selected"},
            {"CTRL + R", "Toggle Crosshairs"},
            {"CTRL + C", "Copy"},
            {"CTRL + V", "Paste"},
            {"+ SHIFT", "Paste Quest (no dependencies)"},
            {"ALT + C", "Force-complete Hovered"},
            {"ALT + R", "Force-reset Hovered"},
            {"CTRL + S", "Save Quests Now"},
            {"CTRL + Z", "Undo"},
            {"CTRL + Y", "Redo"},
            {"Escape or Right Mouse", "Cancel moving the quest"},
            {"Quest View Panel"},
            {"T", "Edit Title"},
            {"S", "Edit Subtitle"},
            {"D", "Edit Description"},
            {"P", "Insert Page Break"},
            {"L", "Insert Line"},
            {"I", "Insert Image"},
            {"Q", "Edit Quest Properties"},
        };
    }

    private Rect window() {
        int w = Math.min(400, width - 20);
        int h = Math.min(height - 20, 320);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Rect win = window();
        QuestArt.tile(QuestArt.DIRT, win.x(), win.y(), win.width(), win.height(), 32, 0xFF505560);
        Ui.outline(win, 0xFF0E0F12);
        fontRenderer.drawStringWithShadow("Key Reference", win.x() + win.width() / 2 - fontRenderer.getStringWidth("Key Reference") / 2, win.y() + 7, 0x55FFFF);
        Rect close = new Rect(win.x() + win.width() - 16, win.y() + 6, 10, 10);
        if (!Ui.questsGui("close", close.x(), close.y(), 10, 10, 0xFFFFFFFF)) {
            fontRenderer.drawStringWithShadow("x", close.x() + 2, close.y() + 1, 0xFF5555);
        }
        Rect list = new Rect(win.x() + 6, win.y() + 22, win.width() - 12, win.height() - 28);
        Gui.drawRect(list.x(), list.y(), list.x() + list.width(), list.y() + list.height(), 0xFF22252C);
        String[][] rows = lines();
        int content = rows.length * ROW + 4;
        scroll = Math.max(0, Math.min(Math.max(0, content - list.height()), scroll));
        ScaledScissor.on(list);
        int y = list.y() + 3 - scroll;
        int keyCol = list.x() + 6;
        int actionCol = list.x() + list.width() / 2 - 20;
        for (String[] row : rows) {
            if (row.length == 1) {
                fontRenderer.drawStringWithShadow("§e§n" + row[0], list.x() + list.width() / 2 - fontRenderer.getStringWidth(row[0]) / 2, y + 1, 0xFFFF55);
            } else {
                fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(row[0], actionCol - keyCol - 6), keyCol, y + 1, 0xFFFFFF);
                fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(row[1], list.x() + list.width() - actionCol - 4), actionCol, y + 1, 0xCCCCCC);
            }
            y += ROW;
        }
        ScaledScissor.off();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        Rect win = window();
        if (new Rect(win.x() + win.width() - 18, win.y() + 4, 14, 14).contains(mouseX, mouseY) || !win.contains(mouseX, mouseY)) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char typed, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll -= Integer.signum(wheel) * 16;
        }
    }

    private static final class ScaledScissor {
        static void on(Rect r) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            int f = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            org.lwjgl.opengl.GL11.glScissor(r.x() * f, mc.displayHeight - (r.y() + r.height()) * f, Math.max(0, r.width() * f), Math.max(0, r.height() * f));
        }

        static void off() {
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        }
    }
}
