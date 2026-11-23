/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.IOException;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;

import com.levviata.reflib.gui.Rect;

/** A small list that opens where the mouse was (a right click) over the screen it came from; a click on a row runs it. */
@SideOnly(Side.CLIENT)
final class PopupMenu extends GuiScreen {

    /** {@code icon}: {@code item:<name>}, {@code lib:<FTB Library icon>} or {@code quests:<FTB Quests gui picture>}; empty for none. */
    record Item(String label, String icon, Runnable action) {}

    private static final int ROW = 13;

    private final GuiScreen parent;
    private final int anchorX;
    private final int anchorY;
    private final List<Item> items;

    PopupMenu(GuiScreen parent, int x, int y, List<Item> items) {
        this.parent = parent;
        this.anchorX = x;
        this.anchorY = y;
        this.items = items;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private Rect box() {
        int w = 40;
        for (Item i : items) {
            w = Math.max(w, fontRenderer.getStringWidth(i.label()) + 26);
        }
        int h = items.size() * ROW + 4;
        return new Rect(Math.min(anchorX, width - w - 2), Math.min(anchorY, height - h - 2), w, h);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (parent != null) {
            parent.drawScreen(-1000, -1000, partialTicks);
        }
        Rect b = box();
        Ui.shadow(b);
        Ui.box(b, 0xFF2F343B);
        int y = b.y() + 2;
        for (Item item : items) {
            Rect row = new Rect(b.x() + 1, y, b.width() - 2, ROW);
            int tx = b.x() + 6;
            String icon = item.icon();
            if (icon.startsWith("item:")) {
                net.minecraft.item.Item it = net.minecraft.item.Item.getByNameOrId(icon.substring(5));
                if (it != null) {
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(tx, y + 1, 0);
                    GlStateManager.scale(0.75f, 0.75f, 1f);
                    RenderHelper.enableGUIStandardItemLighting();
                    itemRender.renderItemAndEffectIntoGUI(new ItemStack(it), 0, 0);
                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.popMatrix();
                    GlStateManager.disableLighting();
                }
            } else if (icon.startsWith("lib:")) {
                Ui.icon(Ui.LIBRARY, "textures/icons/" + icon.substring(4) + ".png", tx, y + 1, 12, 12, 0xFFFFFFFF);
            } else if (icon.startsWith("quests:")) {
                Ui.questsGui(icon.substring(7), tx, y + 1, 12, 12, 0xFFFFFFFF);
            }
            fontRenderer.drawStringWithShadow(item.label(), tx + 16, y + 3, row.contains(mouseX, mouseY) ? 0xFFFF55 : 0xFFFFFF);
            y += ROW;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        Rect b = box();
        if (b.contains(mouseX, mouseY)) {
            int i = (mouseY - b.y() - 2) / ROW;
            if (i >= 0 && i < items.size()) {
                mc.displayGuiScreen(parent);
                items.get(i).action().run();
                return;
            }
        }
        mc.displayGuiScreen(parent);
    }

    @Override
    protected void keyTyped(char typed, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        }
    }
}
