/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.IOException;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.levviata.reflib.gui.Rect;
import com.levviata.reflibquests.net.QuestNet;

/** "Valid items for ...": the items a collection task accepts, with Back and Submit. Click an item to see how it is made. */
@SideOnly(Side.CLIENT)
final class ValidItemsScreen extends GuiScreen {

    // measured from the upstream panel at the same GUI size (hotbar slot = 20 units): 157 x 213, cells 36, icons 24, buttons 20 tall
    private static final int CELL = 36;
    private static final int COLUMNS = 4;
    private static final int PAD = 5;
    private static final int TITLE = 21;
    private static final int BUTTON_H = 20;

    private final GuiScreen parent;
    private final String title;
    private final List<ItemStack> stacks;
    private final String taskId;
    private final boolean canSubmit;
    /** Set when the panel is used to choose an item to copy: clicking an item picks it, and there is no Submit. */
    private final java.util.function.IntConsumer picker;
    private int scroll;

    ValidItemsScreen(GuiScreen parent, String title, List<ItemStack> stacks, String taskId, boolean canSubmit) {
        this(parent, title, stacks, taskId, canSubmit, null);
    }

    /** "Grab Copy of Item" on a collection: the same panel, where clicking an item takes a copy of it. */
    static ValidItemsScreen forCopying(GuiScreen parent, String title, List<ItemStack> stacks, java.util.function.IntConsumer picker) {
        return new ValidItemsScreen(parent, title, stacks, "", false, picker);
    }

    private ValidItemsScreen(GuiScreen parent, String title, List<ItemStack> stacks, String taskId, boolean canSubmit,
            java.util.function.IntConsumer picker) {
        this.parent = parent;
        this.title = title;
        this.stacks = stacks;
        this.taskId = taskId;
        this.canSubmit = canSubmit;
        this.picker = picker;
    }

    private Rect window() {
        int w = COLUMNS * CELL + 2 * PAD + 2;
        int h = Math.min(height - 20, 213);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    private Rect grid(Rect win) {
        return new Rect(win.x() + PAD, win.y() + TITLE, win.width() - 2 * PAD, win.height() - TITLE - PAD - BUTTON_H - 4);
    }

    private Rect back(Rect win) {
        int bw = picker != null ? win.width() - 2 * PAD : (win.width() - 2 * PAD - 4) / 2;
        return new Rect(win.x() + PAD, win.y() + win.height() - PAD - BUTTON_H, bw, BUTTON_H);
    }

    private Rect submit(Rect win) {
        Rect b = back(win);
        return new Rect(b.x() + b.width() + 4, b.y(), b.width(), BUTTON_H);
    }

    private int cellX(Rect grid, int col) {
        return grid.x() + (grid.width() - COLUMNS * CELL) / 2 + col * CELL;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (parent != null) {
            parent.drawScreen(-1000, -1000, partialTicks); // the screen underneath stays visible
        }
        drawDefaultBackground();
        Rect win = window();
        QuestArt.tile(QuestArt.DIRT, win.x(), win.y(), win.width(), win.height(), 32, 0xFF505560);
        Ui.outline(win, 0xFF0E0F12);
        String head = picker != null ? "Copy which item? " + title : "Valid items for " + title;
        int headWidth = Math.min(Ui.width(fontRenderer, head), win.width() - 10);
        Ui.drawText(fontRenderer, head, win.x() + win.width() / 2 - headWidth / 2, win.y() + 7, 0xFFFFFFFF, win.width() - 10);
        Rect grid = grid(win);
        Gui.drawRect(grid.x(), grid.y(), grid.x() + grid.width(), grid.y() + grid.height(), 0xFF22252C);
        int rows = (stacks.size() + COLUMNS - 1) / COLUMNS;
        int maxScroll = Math.max(0, rows * CELL - grid.height());
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        ItemStack hovered = ItemStack.EMPTY;
        ScaledScissor.on(grid);
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < stacks.size(); i++) {
            int x = cellX(grid, i % COLUMNS);
            int y = grid.y() + (i / COLUMNS) * CELL - scroll;
            if (y + CELL < grid.y() || y > grid.y() + grid.height()) {
                continue;
            }
            boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL && grid.contains(mouseX, mouseY);
            if (over) {
                Gui.drawRect(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x40FFFFFF);
                hovered = stacks.get(i);
            }
            // the icon is drawn a little larger than a normal slot, centred in its cell
            GlStateManager.pushMatrix();
            float s = 2.0f;
            GlStateManager.translate(x + (CELL - 16 * s) / 2f, y + (CELL - 16 * s) / 2f, 0);
            GlStateManager.scale(s, s, 1f);
            itemRender.renderItemAndEffectIntoGUI(stacks.get(i), 0, 0);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
        ScaledScissor.off();
        if (maxScroll > 0) {
            int knob = Math.max(10, grid.height() * grid.height() / (rows * CELL));
            int ky = grid.y() + (grid.height() - knob) * scroll / maxScroll;
            Gui.drawRect(grid.x() + grid.width() - 2, ky, grid.x() + grid.width(), ky + knob, 0xFF808080);
        }
        // buttons
        Rect back = back(win);
        Rect submit = submit(win);
        Ui.vanillaButton(back, back.contains(mouseX, mouseY));
        fontRenderer.drawStringWithShadow("Back", back.x() + back.width() / 2 - fontRenderer.getStringWidth("Back") / 2, back.y() + 4, 0xFFFFFF);
        if (picker == null) {
            Ui.vanillaButton(submit, canSubmit && submit.contains(mouseX, mouseY));
            fontRenderer.drawStringWithShadow("Submit", submit.x() + submit.width() / 2 - fontRenderer.getStringWidth("Submit") / 2,
                    submit.y() + 4, canSubmit ? 0xFFFFFF : 0x777777);
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
        if (!hovered.isEmpty()) {
            renderToolTip(hovered, mouseX, mouseY);
        } else if (picker == null && !canSubmit && submit.contains(mouseX, mouseY)) {
            drawHoveringText("Items will be auto-detected", mouseX, mouseY);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button != 0) {
            return;
        }
        Rect win = window();
        Rect grid = grid(win);
        if (back(win).contains(mouseX, mouseY)) {
            mc.displayGuiScreen(parent);
        } else if (picker == null && submit(win).contains(mouseX, mouseY) && canSubmit) {
            QuestNet.submit(taskId);
            mc.displayGuiScreen(parent);
        } else if (grid.contains(mouseX, mouseY)) {
            int col = (mouseX - cellX(grid, 0)) / CELL;
            int row = (mouseY - grid.y() + scroll) / CELL;
            int index = row * COLUMNS + col;
            if (col >= 0 && col < COLUMNS && index >= 0 && index < stacks.size()) {
                if (picker != null) {
                    mc.displayGuiScreen(parent);
                    picker.accept(index); // the chosen item is copied
                } else {
                    JeiBridge.showRecipes(stacks.get(index)); // JEI returns to this screen when it is closed
                }
            }
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
            scroll -= Integer.signum(wheel) * 14;
        }
    }

    /** Minimal scissor helper in GUI coordinates. */
    private static final class ScaledScissor {
        static void on(Rect r) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            int f = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(r.x() * f, mc.displayHeight - (r.y() + r.height()) * f, Math.max(0, r.width() * f), Math.max(0, r.height() * f));
        }

        static void off() {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }
}
