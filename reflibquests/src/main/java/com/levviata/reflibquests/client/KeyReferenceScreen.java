/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.levviata.reflib.gui.Rect;

/**
 * "Key Reference", laid out and worded as in FTB Quests 26.1.2: headings in underlined yellow, the keys right-aligned in white
 * and what they do beside them in grey, long actions wrapping, a draggable scroll bar and a boxed close button beside the window.
 */
@SideOnly(Side.CLIENT)
final class KeyReferenceScreen extends GuiScreen {

    private static final int ROW = 10;
    private static final int KEY_COLOR = 0xFFD8DEE9;
    private static final int ACTION_COLOR = 0xFFA8ACAF;

    private final GuiScreen parent;
    private int scroll;
    private boolean dragging;
    private int contentHeight;

    KeyReferenceScreen(GuiScreen parent) {
        this.parent = parent;
    }

    /** {heading}, {key, action} or {} for an empty line. The wording and the order are the original's. */
    private static String[][] lines() {
        return new String[][] {
            {"General Controls"},
            {Keyboard.getKeyName(Keyboard.KEY_MINUS), "Zoom Out"},
            {Keyboard.getKeyName(Keyboard.KEY_EQUALS), "Zoom In"},
            {"0", "Reset Zoom"},
            {"Tab", "Next Chapter"},
            {"SHIFT + Tab", "Previous Chapter"},
            {"Space", "Recenter GUI"},
            {"CTRL + F", "Search"},
            {"F1", "See Extended Quest Info"},
            {"Up Arrow", "Move Selection / Scroll Up"},
            {"Down Arrow", "Move Selection / Scroll Down"},
            {"Left Arrow", "Move Selection / Scroll Left"},
            {"Right Arrow", "Move Selection / Scroll Right"},
            {"Left Mouse", "View Quest Details"},
            {"Mouse Wheel", "Scroll Up/Down"},
            {"SHIFT + Mouse Wheel", "Scroll Left/Right"},
            {"CTRL + Mouse Wheel", "Zoom In/Out"},
            {"Backspace", "While viewing quest, go to last-viewed quest"},
            {"CTRL + P", "Player Preferences"},
            {},
            {"Editor Mode"},
            {"CTRL + Left Mouse", "Toggle Selection"},
            {"LEFT ALT + Left Mouse", "Properties Editor"},
            {"RIGHT ALT + Left Mouse", "Copy Quest"},
            {"Middle Mouse", "Move Selected Quest(s)"},
            {"Right Mouse", "Context Menu"},
            {"Middle Mouse: Drag", "Select Quests in Drag Box"},
            {"CTRL + Middle Mouse: Drag", "Toggle Selection in Box"},
            {"CTRL + A", "Select All"},
            {"CTRL + D", "Select None"},
            {"Delete", "Delete Hovered / Selected"},
            {"SHIFT + Delete", "Force-delete Hovered / Selected"},
            {"CTRL + R", "Toggle Crosshairs"},
            {"CTRL + C", "Copy"},
            {"CTRL + V", "Paste"},
            {"+ SHIFT", "Paste Quest (no dependencies)"},
            {"+ ALT", "Paste as Quest Link"},
            {"ALT + C", "Force-complete Hovered"},
            {"ALT + R", "Force-reset Hovered"},
            {"CTRL + S", "Save Quests Now"},
            {},
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

    private int keyWidest() {
        int w = 0;
        for (String[] row : lines()) {
            if (row.length == 2) {
                w = Math.max(w, fontRenderer.getStringWidth(row[0]));
            }
        }
        return w;
    }

    private int actionWidest() {
        int w = 0;
        for (String[] row : lines()) {
            if (row.length == 2) {
                w = Math.max(w, Math.min(fontRenderer.getStringWidth(row[1]), 150));
            }
        }
        return w;
    }

    /** Wide enough for the widest key and action side by side; tall, like the original's. */
    private Rect window() {
        int w = Math.min(width - 50, keyWidest() + actionWidest() + 52);
        int h = Math.min(height - 40, 330);
        return new Rect((width - w) / 2, (height - h) / 2 + 6, w, h);
    }

    private Rect list(Rect win) {
        return new Rect(win.x() + 4, win.y() + 4, win.width() - 8, win.height() - 8);
    }

    private Rect track(Rect list) {
        return new Rect(list.x() + list.width() - 11, list.y(), 11, list.height());
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private Rect closeBox(Rect win) {
        return new Rect(win.x() + win.width() + 4, win.y() + 2, 18, 18);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Rect win = window();
        fontRenderer.drawStringWithShadow("Key Reference", win.x() + win.width() / 2 - fontRenderer.getStringWidth("Key Reference") / 2, win.y() - 11, 0x55FFFF);
        Gui.drawRect(win.x() - 1, win.y() - 1, win.x() + win.width() + 1, win.y() + win.height() + 1, 0xFF0F1016);
        Gui.drawRect(win.x(), win.y(), win.x() + win.width(), win.y() + win.height(), 0xFF4B5365);
        Gui.drawRect(win.x() + 1, win.y() + 1, win.x() + win.width() - 1, win.y() + win.height() - 1, 0xFF2D343F);

        Rect close = closeBox(win);
        Gui.drawRect(close.x(), close.y(), close.x() + close.width(), close.y() + close.height(), 0xFF000000);
        Gui.drawRect(close.x() + 1, close.y() + 1, close.x() + close.width() - 1, close.y() + close.height() - 1,
                close.contains(mouseX, mouseY) ? 0xFF4A5164 : 0xFF3B4252);
        if (!Ui.questsGui("close", close.x() + 3, close.y() + 3, 12, 12, 0xFFFFFFFF)) {
            fontRenderer.drawStringWithShadow("x", close.x() + 6, close.y() + 5, 0xFF5555);
        }

        Rect list = list(win);
        String[][] rows = lines();
        int textRight = list.x() + list.width() - 14;
        int keyEnd = list.x() + (int) ((textRight - list.x()) * 0.40) + 6;
        int actionCol = keyEnd + 8;
        // lay the rows out first: a long action wraps onto more lines
        List<Object[]> laid = new ArrayList<>(); // {type, key/heading, action lines}
        int total = 0;
        for (String[] row : rows) {
            if (row.length == 2) {
                List<String> wrapped = fontRenderer.listFormattedStringToWidth(row[1], Math.max(40, textRight - actionCol));
                laid.add(new Object[] {2, row[0], wrapped});
                total += wrapped.size() * ROW;
            } else if (row.length == 1) {
                laid.add(new Object[] {1, row[0], null});
                total += ROW + 1;
            } else {
                laid.add(new Object[] {0, null, null});
                total += ROW;
            }
        }
        contentHeight = total + 6;
        scroll = Math.max(0, Math.min(Math.max(0, contentHeight - list.height()), scroll));
        scissor(list);
        int y = list.y() + 4 - scroll;
        for (Object[] row : laid) {
            int type = (Integer) row[0];
            if (type == 1) {
                String heading = (String) row[1];
                int hw = fontRenderer.getStringWidth(heading);
                fontRenderer.drawStringWithShadow("§e§n" + heading, list.x() + (int) ((textRight - list.x()) * 0.48) - hw / 2, y + 1, 0xFFFF55);
                y += ROW + 1;
            } else if (type == 2) {
                String key = (String) row[1];
                fontRenderer.drawStringWithShadow(key, keyEnd - fontRenderer.getStringWidth(key), y + 1, KEY_COLOR);
                @SuppressWarnings("unchecked")
                List<String> wrapped = (List<String>) row[2];
                for (String piece : wrapped) {
                    fontRenderer.drawStringWithShadow(piece, actionCol, y + 1, ACTION_COLOR);
                    y += ROW;
                }
            } else {
                y += ROW;
            }
        }
        unscissor();

        // the scroll bar, like the other windows': a dark framed track and a bevelled thumb
        Rect t = track(list);
        int max = Math.max(0, contentHeight - list.height());
        Gui.drawRect(t.x(), t.y(), t.x() + t.width(), t.y() + t.height(), 0xFF0F1016);
        Gui.drawRect(t.x() + 1, t.y() + 1, t.x() + t.width() - 1, t.y() + t.height() - 1, 0xFF2E333F);
        if (max > 0) {
            int thumb = Math.max(7, Math.min(t.height() - 4, (t.height() - 4) * list.height() / Math.max(1, contentHeight)));
            int ty = t.y() + 2 + (t.height() - 4 - thumb) * scroll / max;
            Gui.drawRect(t.x() + 2, ty, t.x() + t.width() - 2, ty + thumb, 0xFF444C5E);
            Gui.drawRect(t.x() + 2, ty, t.x() + t.width() - 2, ty + 1, 0xFF5F6981);
        }
    }

    private void scissor(Rect r) {
        double f = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (r.x() * f), (int) (mc.displayHeight - (r.y() + r.height()) * f), (int) Math.max(0, r.width() * f), (int) Math.max(0, r.height() * f));
    }

    private void unscissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void dragTo(int mouseY) {
        Rect list = list(window());
        Rect t = track(list);
        int max = Math.max(0, contentHeight - list.height());
        int thumb = Math.max(7, Math.min(t.height() - 4, (t.height() - 4) * list.height() / Math.max(1, contentHeight)));
        double f = (mouseY - t.y() - 2 - thumb / 2.0) / Math.max(1, t.height() - 4 - thumb);
        scroll = (int) Math.round(Math.max(0, Math.min(1, f)) * max);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        Rect win = window();
        if (button == 0 && track(list(win)).contains(mouseX, mouseY)) {
            dragging = true;
            dragTo(mouseY);
        } else if (closeBox(win).contains(mouseX, mouseY) || !win.contains(mouseX, mouseY)) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (dragging) {
            dragTo(mouseY);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
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
            scroll -= Integer.signum(wheel) * 20;
        }
    }
}
