/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.IOException;
import java.util.function.Consumer;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;

import com.levviata.reflib.gui.Rect;

/**
 * FTB Quests' "quick string editor" for NBT: a wide box with the data as one line of SNBT, a check mark to accept and a cross to
 * cancel. Enter accepts, Escape cancels; text that is not valid NBT is refused with a message and the box stays open.
 */
@SideOnly(Side.CLIENT)
final class QuickNbtScreen extends GuiScreen {

    private final GuiScreen parent;
    private final Consumer<NBTTagCompound> accepted;
    private final TextEditor edit;
    private String error = "";

    QuickNbtScreen(GuiScreen parent, String initial, Consumer<NBTTagCompound> accepted) {
        this.parent = parent;
        this.accepted = accepted;
        this.edit = new TextEditor(initial, 4000);
        this.edit.selectAll();
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
        int w = Math.min(width - 20, (int) (width * 0.57));
        int x = Math.max(10, (int) (width * 0.15));
        int y = Math.min(height - 60, (int) (height * 0.68));
        return new Rect(x, y, w, 45);
    }

    private Rect ok(Rect b) {
        return new Rect(b.x() + b.width() - 18, b.y() + 3, 16, 16);
    }

    private Rect no(Rect b) {
        return new Rect(b.x() + b.width() - 18, b.y() + 21, 16, 16);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (parent != null) {
            parent.drawScreen(-1000, -1000, partialTicks);
        }
        Rect b = box();
        Gui.drawRect(b.x() - 1, b.y() - 1, b.x() + b.width() + 1, b.y() + b.height() + 1, 0xFF101010);
        Gui.drawRect(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(), 0xFF3B4252);
        Gui.drawRect(b.x() + 1, b.y() + 1, b.x() + b.width() - 20, b.y() + b.height() - 1, 0xFF353C4B);
        Rect line = new Rect(b.x() + 4, b.y() + 5, b.width() - 28, 12);
        RewardTablesScreen.drawFieldText(fontRenderer, line, edit);
        if (!error.isEmpty()) {
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(error, b.width() - 28), b.x() + 5, b.y() + b.height() - 12, 0xFF5555);
        }
        Rect ok = ok(b);
        Rect no = no(b);
        if (!Ui.icon(Ui.LIBRARY, "textures/icons/accept.png", ok.x(), ok.y(), 16, 16, ok.contains(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFDDDDDD)) {
            fontRenderer.drawStringWithShadow("\u2714", ok.x() + 4, ok.y() + 4, 0x98C600);
        }
        if (!Ui.icon(Ui.LIBRARY, "textures/icons/cancel.png", no.x(), no.y(), 16, 16, no.contains(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFDDDDDD)) {
            fontRenderer.drawStringWithShadow("\u2718", no.x() + 4, no.y() + 4, 0xFF3F27);
        }
        if (ok.contains(mouseX, mouseY)) {
            drawHoveringText("Accept", mouseX, mouseY);
        } else if (no.contains(mouseX, mouseY)) {
            drawHoveringText("Cancel", mouseX, mouseY);
        }
    }

    private void accept() {
        String text = edit.text().strip();
        try {
            NBTTagCompound tag = text.isEmpty() ? new NBTTagCompound() : JsonToNBT.getTagFromJson(text);
            mc.displayGuiScreen(parent);
            accepted.accept(tag);
        } catch (NBTException ex) {
            error = "Not valid NBT: " + ex.getMessage();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        Rect b = box();
        if (button == 0 && ok(b).contains(mouseX, mouseY)) {
            accept();
        } else if (button == 0 && no(b).contains(mouseX, mouseY) || !b.contains(mouseX, mouseY)) {
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
            error = "";
            RewardTablesScreen.typeInto(edit, typed, keyCode, false);
        }
    }
}
