/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.levviata.reflib.gui.Rect;

/**
 * "Select Item": a search box over a grid of every item of this game (as the creative search tab lists them); clicking
 * one picks it. FTB Quests opens its picker when an item task or reward is created.
 */
@SideOnly(Side.CLIENT)
final class ItemPickerScreen extends GuiScreen {

    private static final int CELL = 18;
    private static final int MAX_ITEMS = 12000;
    private static List<ItemStack> all;

    private final GuiScreen parent;
    private final java.util.function.BiConsumer<ItemStack, Integer> picked;
    /** FTB Quests' picker has a count box (a number with - and +) when a task or reward is being made. */
    private final boolean askCount;
    private int count = 1;
    private final TextEditor search = new TextEditor("", 60);
    private final RewardTablesScreen.Scroller scroller = new RewardTablesScreen.Scroller();
    private final List<Object[]> hits = new ArrayList<>();
    private ItemStack hovered = ItemStack.EMPTY;
    private Rect countBox;

    ItemPickerScreen(GuiScreen parent, Consumer<ItemStack> picked) {
        this(parent, false, (stack, n) -> picked.accept(stack));
    }

    ItemPickerScreen(GuiScreen parent, boolean askCount, java.util.function.BiConsumer<ItemStack, Integer> picked) {
        this.parent = parent;
        this.askCount = askCount;
        this.picked = picked;
    }

    /** Every item and its variants, once. */
    private static synchronized List<ItemStack> everything() {
        if (all == null) {
            List<ItemStack> out = new ArrayList<>();
            for (Item item : ForgeRegistries.ITEMS) {
                NonNullList<ItemStack> subs = NonNullList.create();
                try {
                    item.getSubItems(CreativeTabs.SEARCH, subs);
                } catch (RuntimeException ex) {
                    subs.clear(); // an item that cannot list its variants is offered as itself below
                }
                if (subs.isEmpty() && item.getCreativeTab() != null) {
                    subs.add(new ItemStack(item));
                }
                for (ItemStack s : subs) {
                    if (!s.isEmpty() && out.size() < MAX_ITEMS) {
                        out.add(s);
                    }
                }
            }
            all = out;
        }
        return all;
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

    private Rect window() {
        int w = Math.min(width - 12, 270);
        int h = Math.min(height - 12, 230);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        hits.clear();
        hovered = ItemStack.EMPTY;
        if (parent != null) {
            parent.drawScreen(-1000, -1000, partialTicks);
        }
        drawDefaultBackground();
        Rect win = window();
        Gui.drawRect(win.x() - 1, win.y() - 1, win.x() + win.width() + 1, win.y() + win.height() + 1, 0xFF4C5565);
        Gui.drawRect(win.x(), win.y(), win.x() + win.width(), win.y() + 38, 0xFF3C4352);
        Gui.drawRect(win.x(), win.y() + 38, win.x() + win.width(), win.y() + win.height(), 0xFF2F3541);
        fontRenderer.drawStringWithShadow("Select Item", win.x() + 6, win.y() + 6, 0xFFFFFF);
        Rect field = new Rect(win.x() + 6, win.y() + 20, win.width() - 34, 14);
        Gui.drawRect(field.x(), field.y(), field.x() + field.width(), field.y() + field.height(), 0xFF5F83AE);
        Gui.drawRect(field.x(), field.y(), field.x() + field.width(), field.y() + 1, 0xFF7FA3CE);
        RewardTablesScreen.drawFieldText(fontRenderer, field, search);
        Rect close = new Rect(win.x() + win.width() - 22, win.y() + 18, 16, 16);
        if (!Ui.questsGui("close", close.x() + 2, close.y() + 2, 12, 12, 0xFFFFFFFF)) {
            fontRenderer.drawStringWithShadow("x", close.x() + 5, close.y() + 4, 0xFF5555);
        }
        hits.add(new Object[] {close, (Runnable) () -> mc.displayGuiScreen(parent)});

        String needle = search.text().toLowerCase(Locale.ROOT).strip();
        List<ItemStack> shown = new ArrayList<>();
        for (ItemStack s : everything()) {
            if (needle.isEmpty() || s.getDisplayName().toLowerCase(Locale.ROOT).contains(needle)
                    || String.valueOf(s.getItem().getRegistryName()).toLowerCase(Locale.ROOT).contains(needle)) {
                shown.add(s);
            }
        }
        int barH = askCount ? 20 : 0;
        Rect grid = new Rect(win.x() + 4, win.y() + 42, win.width() - 8, win.height() - 46 - barH);
        if (askCount) {
            // the count box: Count, a number, - and +; Shift changes it by ten; the wheel over the number works too
            int by = win.y() + win.height() - 19;
            fontRenderer.drawStringWithShadow("Count", win.x() + 8, by + 4, 0xFFFFFF);
            Rect minus = new Rect(win.x() + 52, by, 16, 16);
            Rect number = new Rect(win.x() + 70, by, 44, 16);
            Rect plus = new Rect(win.x() + 116, by, 16, 16);
            for (Rect b : new Rect[] {minus, plus}) {
                Gui.drawRect(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(), b.contains(mouseX, mouseY) ? 0xFF5F6B80 : 0xFF4C5565);
            }
            fontRenderer.drawStringWithShadow("-", minus.x() + 6, minus.y() + 4, 0xFFFFFF);
            fontRenderer.drawStringWithShadow("+", plus.x() + 5, plus.y() + 4, 0xFFFFFF);
            Gui.drawRect(number.x(), number.y(), number.x() + number.width(), number.y() + number.height(), 0xFF262B35);
            String shownCount = String.valueOf(count);
            fontRenderer.drawStringWithShadow(shownCount, number.x() + (number.width() - fontRenderer.getStringWidth(shownCount)) / 2, number.y() + 4, 0xFFFFFF);
            hits.add(new Object[] {minus, (Runnable) () -> count = Math.max(1, count - (isShiftKeyDown() ? 10 : 1))});
            hits.add(new Object[] {plus, (Runnable) () -> count = Math.min(1_000_000, count + (isShiftKeyDown() ? 10 : 1))});
            countBox = number;
        }
        int cols = Math.max(1, (grid.width() - 12) / CELL);
        int rows = (shown.size() + cols - 1) / cols;
        scroller.layout(grid, rows * CELL, CELL);
        RewardTablesScreen.scissor(mc, grid);
        int firstRow = Math.max(0, scroller.offset / CELL);
        int lastRow = Math.min(rows - 1, (scroller.offset + grid.height()) / CELL + 1);
        for (int r = firstRow; r <= lastRow; r++) {
            for (int c = 0; c < cols; c++) {
                int index = r * cols + c;
                if (index >= shown.size()) {
                    break;
                }
                ItemStack stack = shown.get(index);
                int x = grid.x() + 2 + c * CELL;
                int y = grid.y() + r * CELL - scroller.offset;
                Rect cell = new Rect(x - 1, y, CELL, CELL);
                boolean over = cell.contains(mouseX, mouseY) && grid.contains(mouseX, mouseY);
                if (over) {
                    Gui.drawRect(cell.x(), cell.y(), cell.x() + cell.width(), cell.y() + cell.height(), 0xFF4C5565);
                    hovered = stack;
                }
                RenderHelper.enableGUIStandardItemLighting();
                itemRender.renderItemAndEffectIntoGUI(stack, x, y + 1);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableLighting();
                hits.add(new Object[] {RewardTablesScreen.clip(cell, grid), (Runnable) () -> {
                    mc.displayGuiScreen(parent);
                    picked.accept(stack, count);
                }});
            }
        }
        RewardTablesScreen.unscissor();
        if (shown.isEmpty()) {
            fontRenderer.drawStringWithShadow("Nothing found.", grid.x() + 6, grid.y() + 6, 0x888899);
        }
        scroller.draw();
        if (!hovered.isEmpty()) {
            renderToolTip(hovered, mouseX, mouseY);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button != 0) {
            return;
        }
        if (scroller.press(mouseX, mouseY)) {
            return;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (((Rect) hits.get(i)[0]).contains(mouseX, mouseY)) {
                ((Runnable) hits.get(i)[1]).run();
                return;
            }
        }
        if (!window().contains(mouseX, mouseY)) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        scroller.drag(mouseY);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        scroller.release();
    }

    @Override
    protected void keyTyped(char typed, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        } else {
            RewardTablesScreen.typeInto(search, typed, keyCode, false);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mx = Mouse.getEventX() * width / mc.displayWidth;
            int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            if (askCount && countBox != null && countBox.contains(mx, my)) {
                count = Math.max(1, Math.min(1_000_000, count + (wheel > 0 ? 1 : -1) * (isShiftKeyDown() ? 10 : 1)));
            } else {
                scroller.wheel(wheel);
            }
        }
    }
}
