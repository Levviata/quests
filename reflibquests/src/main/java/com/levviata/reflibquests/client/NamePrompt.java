/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.IOException;
import java.util.function.Consumer;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;

import com.levviata.reflib.gui.Rect;

@SideOnly(Side.CLIENT)
final class NamePrompt extends GuiScreen {

    private final GuiScreen parent;
    private final int anchorX;
    private final int anchorY;
    private final TextEditor edit;
    private final Consumer<String> accepted;

    private final String title;

    NamePrompt(GuiScreen parent, int anchorX, int anchorY, String initial, int maxLength, Consumer<String> accepted) {
        this(parent, anchorX, anchorY, "", initial, maxLength, accepted);
    }

    NamePrompt(GuiScreen parent, int anchorX, int anchorY, String title, String initial, int maxLength, Consumer<String> accepted) {
        this.title = title;
        this.parent = parent;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.edit = new TextEditor(initial, maxLength);
        this.edit.selectAll();
        this.accepted = accepted;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    private Rect box() {
        int w = Math.min(width - 8, title.isEmpty() ? 190 : 151);
        int h = title.isEmpty() ? 36 : 31;
        int x = Math.max(4, Math.min(anchorX, width - w - 4));
        int y = Math.max(4, Math.min(anchorY, height - h - 4));
        return new Rect(x, y, w, h);
    }

    private Rect acceptRect(Rect b) {
        return new Rect(b.x() + b.width() - (title.isEmpty() ? 40 : 34), b.y() + (title.isEmpty() ? b.height() - 18 : 14), 16, 16);
    }

    private Rect cancelRect(Rect b) {
        return new Rect(b.x() + b.width() - (title.isEmpty() ? 20 : 16), b.y() + (title.isEmpty() ? b.height() - 18 : 14), 16, 16);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (parent != null) {
            parent.drawScreen(-1000, -1000, partialTicks);
        }
        drawDefaultBackground();
        Rect b = box();
        Gui.drawRect(b.x() - 1, b.y() - 1, b.x() + b.width() + 1, b.y() + b.height() + 1, 0xFF0E0F12);
        Gui.drawRect(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(), 0xFF262B35);
        if (title.isEmpty()) {
            RewardTablesScreen.drawFieldText(fontRenderer, new Rect(b.x() + 3, b.y() + 3, b.width() - 6, 12), edit);
        } else {
            fontRenderer.drawStringWithShadow(title, b.x() + 3, b.y() + 2, 0xFFFFFF);
            RewardTablesScreen.drawFieldText(fontRenderer, new Rect(b.x() + 3, b.y() + 16, b.width() - 54, 12), edit);
        }
        Rect ok = acceptRect(b);
        Rect no = cancelRect(b);
        if (ok.contains(mouseX, mouseY)) {
            Gui.drawRect(ok.x(), ok.y(), ok.x() + ok.width(), ok.y() + ok.height(), 0x30FFFFFF);
        }
        if (no.contains(mouseX, mouseY)) {
            Gui.drawRect(no.x(), no.y(), no.x() + no.width(), no.y() + no.height(), 0x30FFFFFF);
        }
        Ui.icon(Ui.LIBRARY, "textures/icons/accept.png", ok.x(), ok.y(), 16, 16, 0xFFFFFFFF);
        Ui.icon(Ui.LIBRARY, "textures/icons/cancel.png", no.x(), no.y(), 16, 16, 0xFFFFFFFF);
    }

    private void accept() {
        String text = edit.text().strip();
        mc.displayGuiScreen(parent);
        if (!text.isEmpty()) {
            accepted.accept(text);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        Rect b = box();
        if (button == 0 && acceptRect(b).contains(mouseX, mouseY)) {
            accept();
        } else if (button == 0 && cancelRect(b).contains(mouseX, mouseY) || !b.contains(mouseX, mouseY)) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char typed, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            accept();
        } else {
            RewardTablesScreen.typeInto(edit, typed, keyCode, false);
        }
    }
}
