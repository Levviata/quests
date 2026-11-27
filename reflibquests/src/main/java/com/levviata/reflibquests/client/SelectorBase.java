/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.levviata.reflib.gui.Rect;

/**
 * Shared frame of every grid selector: window, header, preview box, List Mode button, search bar, scrolling grid,
 * Accept and Cancel. A selector only supplies its cells and what each part looks like; everything else (layout,
 * scrolling, search, window widening for long headers, click and key routing) lives here.
 *
 * @param <C> what one cell of the grid holds
 */
@SideOnly(Side.CLIENT)
abstract class SelectorBase<C> extends GuiScreen {

    static final int W = 185;
    static final int H = 202;
    static final int BASE_COLS = 9;
    static final int CELL = 18;
    static final Rect PREVIEW = new Rect(7, 18, 32, 32);
    static final Rect MODE = new Rect(7, 56, 20, 20);
    static final Rect SEARCH = new Rect(29, 58, 100, 16);
    static final Rect GRID0 = new Rect(3, 81, 166, 91);
    static final Rect TRACK0 = new Rect(170, 81, 11, 91);
    static final Rect ACCEPT0 = new Rect(50, 177, 62, 20);
    static final Rect CANCEL0 = new Rect(117, 177, 62, 20);

    static final int TEXT = 0xFFD8DEE9;
    static final int BG = 0xFF3B4252;
    static final int PANEL = 0xFF2E3440;
    static final int DARK = 0xFF1C1F27;
    static final int LIGHT = 0xFF495366;

    protected final GuiScreen parent;
    protected final TextEditor search = new TextEditor("", 60);
    protected boolean searchFocused = true;
    /** Index of the current List Mode, 0 .. modeCount() - 1. */
    protected int mode;
    protected int scroll;

    private boolean dragging;
    private List<C> shown = new ArrayList<>();
    private String shownFor = null;
    private C hovered;
    private boolean hoverMode;

    protected SelectorBase(GuiScreen parent) {
        this.parent = parent;
    }

    // ------------------------------------------------------------------ what a selector supplies

    /** Header text, may use colour codes. Also decides how wide the window gets. */
    protected abstract String titleText();

    /** Cells for the current mode; {@code needle} is the lower-cased, stripped search text. */
    protected abstract List<C> buildCells(String needle);

    /** Number of entries in the current mode before searching, shown in the List Mode tooltip. */
    protected abstract int totalCount();

    /** Name of the current List Mode for the tooltip. */
    protected abstract String modeName();

    /** Draws the List Mode icon inside the 20x20 button at {@code b}. */
    protected abstract void drawModeIcon(Rect b);

    /** Draws one cell's content; {@code (cx, cy)} is the top-left of the 18x18 cell, content area starts at +1. */
    protected abstract void drawCell(C cell, int cx, int cy);

    /** Tooltip for a hovered cell. */
    protected abstract void drawCellTooltip(C cell, int mouseX, int mouseY);

    /** A cell was clicked. */
    protected abstract void select(C cell);

    /** Accept was pressed (button or Enter). Must leave this screen. */
    protected abstract void accept();

    /** Draws the contents of the preview box (the box itself is drawn here). */
    protected abstract void drawPreview(Rect p);

    // ------------------------------------------------------------------ optional hooks

    /** Number of List Modes; clicking the mode button cycles through them when more than one. */
    protected int modeCount() {
        return 1;
    }

    protected boolean showPreview() {
        return true;
    }

    /** Key for the cell cache; override when the cells depend on more than mode and search text. */
    protected String cacheKey() {
        return mode + "|" + search.text();
    }

    /** Forces the cells to be rebuilt on the next frame. */
    protected void invalidate() {
        shownFor = null;
    }

    /** Extra widgets between the preview and the List Mode button. */
    protected void drawExtras(Rect win, int mouseX, int mouseY) {
    }

    /** Clicks on those extra widgets; return true if the click was used. */
    protected boolean clickExtras(Rect win, int mouseX, int mouseY) {
        return false;
    }

    /** Tooltip of an extra widget; return true if one was drawn. */
    protected boolean drawExtraTooltip(int mouseX, int mouseY) {
        return false;
    }

    /** True when every key press goes to the search bar (no other text field exists). */
    protected boolean searchOnly() {
        return false;
    }

    /** Key press while the search bar is not focused. */
    protected void typeExtras(char typed, int keyCode) {
    }

    protected final C hovered() {
        return hovered;
    }

    // ------------------------------------------------------------------ layout

    protected final int extra() {
        int need = fontRenderer.getStringWidth(titleText()) + 14 - W;
        return need <= 0 ? 0 : (need + CELL - 1) / CELL * CELL;
    }

    protected final int cols() {
        return BASE_COLS + extra() / CELL;
    }

    protected final Rect gridR() {
        return new Rect(GRID0.x(), GRID0.y(), GRID0.width() + extra(), GRID0.height());
    }

    protected final Rect trackR() {
        return new Rect(TRACK0.x() + extra(), TRACK0.y(), TRACK0.width(), TRACK0.height());
    }

    protected final Rect acceptR() {
        return new Rect(ACCEPT0.x() + extra(), ACCEPT0.y(), ACCEPT0.width(), ACCEPT0.height());
    }

    protected final Rect cancelR() {
        return new Rect(CANCEL0.x() + extra(), CANCEL0.y(), CANCEL0.width(), CANCEL0.height());
    }

    protected final Rect win() {
        return new Rect((width - W - extra()) / 2, (height - H) / 2, W + extra(), H);
    }

    protected static Rect at(Rect win, Rect r) {
        return new Rect(win.x() + r.x(), win.y() + r.y(), r.width(), r.height());
    }

    private int viewHeight() {
        return GRID0.height() - 3;
    }

    private int maxScroll(List<C> list) {
        int rows = (list.size() + cols() - 1) / cols();
        return Math.max(0, rows * CELL + 2 - viewHeight());
    }

    private List<C> cells() {
        String key = cacheKey();
        if (key.equals(shownFor)) {
            return shown;
        }
        shownFor = key;
        shown = buildCells(search.text().toLowerCase(Locale.ROOT).strip());
        scroll = 0;
        return shown;
    }

    // ------------------------------------------------------------------ drawing helpers

    static void box(int x, int y, int w, int h, int fill, int topLeft, int bottomRight) {
        Gui.drawRect(x, y, x + w, y + h, fill);
        Gui.drawRect(x, y, x + w, y + 1, topLeft);
        Gui.drawRect(x, y, x + 1, y + h, topLeft);
        Gui.drawRect(x, y + h - 1, x + w, y + h, bottomRight);
        Gui.drawRect(x + w - 1, y, x + w, y + h, bottomRight);
    }

    static void button(Rect r, boolean hover) {
        Gui.drawRect(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), 0xFF000000);
        box(r.x() + 1, r.y() + 1, r.width() - 2, r.height() - 2, BG, 0xFF4F5666, 0xFF2F3645);
        if (hover) {
            int x = r.x() + 1;
            int y = r.y() + 1;
            int w = r.width() - 2;
            int h = r.height() - 2;
            Gui.drawRect(x, y, x + w, y + 1, 0xFFFFFFFF);
            Gui.drawRect(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
            Gui.drawRect(x, y, x + 1, y + h, 0xFFFFFFFF);
            Gui.drawRect(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);
        }
    }

    protected final void field(Rect r, TextEditor edit, boolean focused, String placeholder) {
        box(r.x(), r.y(), r.width(), r.height(), 0xFF5E81AC, 0xFF506E93, 0xFF739ED2);
        String text = edit.text();
        int x = r.x() + 4;
        int y = r.y() + 4;
        int room = r.width() - 8;
        if (text.isEmpty() && !focused && placeholder != null) {
            fontRenderer.drawString("§o" + placeholder, x, y, 0xFF97ADC9);
            return;
        }
        int caret = Math.min(edit.caret(), text.length());
        int first = 0;
        while (first < caret && fontRenderer.getStringWidth(text.substring(first, caret)) > room - 6) {
            first++;
        }
        String visible = fontRenderer.trimStringToWidth(text.substring(first), room);
        if (edit.hasSelection() && focused) {
            int s0 = Math.max(first, edit.selStart());
            int s1 = Math.min(first + visible.length(), edit.selEnd());
            if (s1 > s0) {
                Gui.drawRect(x + fontRenderer.getStringWidth(text.substring(first, s0)), r.y() + 2,
                        x + fontRenderer.getStringWidth(text.substring(first, s1)), r.y() + r.height() - 2, 0xAA3A6FD0);
            }
        }
        fontRenderer.drawString(visible, x, y, TEXT);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = x + fontRenderer.getStringWidth(text.substring(first, Math.max(first, caret)));
            fontRenderer.drawString("_", cx, y, TEXT);
        }
    }

    /** Colours {@code namespace:path} the way every selector header and tooltip shows an id. */
    static String idTitle(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "§e" + id : "§6" + id.substring(0, colon + 1) + "§e" + id.substring(colon + 1);
    }

    protected final void renderItem(ItemStack stack, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
    }

    /** An item at double size filling the preview box. */
    protected final void renderItemBig(Rect p, ItemStack stack) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(p.x() + 0, p.y() + 0, 0);
        GlStateManager.scale(2, 2, 1);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(stack, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
        GlStateManager.disableLighting();
    }

    private int thumbHeight(List<C> list) {
        int content = (list.size() + cols() - 1) / cols() * CELL + 2;
        return Math.max(7, Math.min(TRACK0.height() - 4, (TRACK0.height() - 4) * viewHeight() / Math.max(1, content)));
    }

    private void drawScrollBar(Rect win, List<C> list) {
        Rect t = at(win, trackR());
        Gui.drawRect(t.x(), t.y(), t.x() + t.width(), t.y() + t.height(), 0xFF101010);
        box(t.x() + 1, t.y() + 1, t.width() - 2, t.height() - 2, PANEL, DARK, LIGHT);
        int max = maxScroll(list);
        int th = thumbHeight(list);
        int travel = t.height() - 4 - th;
        int ty = t.y() + 2 + (max == 0 ? 0 : travel * scroll / max);
        int tx = t.x() + 2;
        Gui.drawRect(tx, ty, tx + 7, ty + th, 0xFF313745);
        Gui.drawRect(tx, ty, tx + 7, ty + 1, 0xFF5E6B84);
        Gui.drawRect(tx, ty + 1, tx + 6, ty + th - 1, 0xFF434C5E);
    }

    private void dragTo(int mouseY) {
        List<C> list = cells();
        Rect t = at(win(), trackR());
        int th = thumbHeight(list);
        int travel = Math.max(1, t.height() - 4 - th);
        double f = (mouseY - t.y() - 2 - th / 2.0) / travel;
        scroll = (int) Math.round(Math.max(0, Math.min(1, f)) * maxScroll(list));
    }

    // ------------------------------------------------------------------ screen

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

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (parent != null) {
            parent.drawScreen(-1000, -1000, partialTicks);
        }
        drawDefaultBackground();
        Rect win = win();
        int wx = win.x();
        int wy = win.y();
        hovered = null;
        hoverMode = false;
        List<C> list = cells();
        scroll = Math.max(0, Math.min(maxScroll(list), scroll));

        final int ww = W + extra();
        Gui.drawRect(wx, wy, wx + ww, wy + H, 0xFF101010);
        box(wx + 1, wy + 1, ww - 2, H - 2, BG, LIGHT, DARK);

        String title = titleText();
        fontRenderer.drawString(fontRenderer.trimStringToWidth(title, ww - 14), wx + 7, wy + 6, TEXT);

        if (showPreview()) {
            Rect p = at(win, PREVIEW);
            box(p.x(), p.y(), p.width(), p.height(), PANEL, DARK, LIGHT);
            drawPreview(p);
        }

        drawExtras(win, mouseX, mouseY);

        Rect mb = at(win, MODE);
        hoverMode = mb.contains(mouseX, mouseY);
        button(mb, hoverMode);
        drawModeIcon(mb);
        field(at(win, SEARCH), search, searchFocused, "Search...");

        Gui.drawRect(wx + 2, wy + 79, wx + 183 + extra(), wy + 80, 0xFF282D38);
        Gui.drawRect(wx + 2, wy + 80, wx + 183 + extra(), wy + 175, PANEL);
        Gui.drawRect(wx + 2, wy + 175, wx + 183 + extra(), wy + 176, 0xFF535864);
        Rect g = at(win, gridR());
        Gui.drawRect(g.x(), g.y(), g.x() + g.width(), g.y() + g.height(), 0xFF101010);
        Gui.drawRect(g.x() + 1, g.y() + 1, g.x() + g.width() - 1, g.y() + g.height() - 1, PANEL);
        Rect view = new Rect(g.x() + 1, g.y() + 1, g.width() - 2, g.height() - 2);
        RewardTablesScreen.scissor(mc, view);
        int first = Math.max(0, (scroll - 2) / CELL);
        int last = Math.min((list.size() + cols() - 1) / cols() - 1, (scroll + view.height()) / CELL + 1);
        for (int r = first; r <= last; r++) {
            for (int c = 0; c < cols(); c++) {
                int index = r * cols() + c;
                if (index >= list.size()) {
                    break;
                }
                C cell = list.get(index);
                int cx = wx + 5 + c * CELL;
                int cy = wy + 83 + r * CELL - scroll;
                box(cx, cy, CELL, CELL, PANEL, DARK, LIGHT);
                drawCell(cell, cx, cy);
                if (view.contains(mouseX, mouseY) && mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                    Gui.drawRect(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, 0x40FFFFFF);
                    hovered = cell;
                }
            }
        }
        RewardTablesScreen.unscissor();
        drawScrollBar(win, list);

        for (Rect r : new Rect[] {acceptR(), cancelR()}) {
            Rect b = at(win, r);
            boolean accept = r.x() == acceptR().x();
            button(b, b.contains(mouseX, mouseY));
            String icon = accept ? "textures/icons/accept.png" : "textures/icons/cancel.png";
            if (!Ui.icon(Ui.LIBRARY, icon, b.x() + 4, b.y() + 2, 16, 16, 0xFFFFFFFF)) {
                fontRenderer.drawStringWithShadow(accept ? "✔" : "✘", b.x() + 8, b.y() + 8, accept ? 0xFF98C600 : 0xFFFF3F27);
            }
            fontRenderer.drawStringWithShadow(accept ? "Accept" : "Cancel", b.x() + 22, b.y() + 8, b.contains(mouseX, mouseY) ? 0xFFEBCB8B : TEXT);
        }

        if (at(win, acceptR()).contains(mouseX, mouseY)) {
            drawHoveringText("§7[⇧ + Enter]", mouseX, mouseY);
        } else if (at(win, cancelR()).contains(mouseX, mouseY)) {
            drawHoveringText("§7[ESC]", mouseX, mouseY);
        } else if (drawExtraTooltip(mouseX, mouseY)) {
            return;
        } else if (hoverMode) {
            drawHoveringText(java.util.List.of("List Mode", "§7" + modeName() + " §8[" + totalCount() + "]"), mouseX, mouseY);
        } else if (hovered != null) {
            drawCellTooltip(hovered, mouseX, mouseY);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        Rect win = win();
        if (button != 0) {
            return;
        }
        if (!win.contains(mouseX, mouseY)) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (at(win, acceptR()).contains(mouseX, mouseY)) {
            accept();
        } else if (at(win, cancelR()).contains(mouseX, mouseY)) {
            mc.displayGuiScreen(parent);
        } else if (clickExtras(win, mouseX, mouseY)) {
            return;
        } else if (at(win, MODE).contains(mouseX, mouseY)) {
            if (modeCount() > 1) {
                mode = (mode + 1) % modeCount();
                scroll = 0;
            }
        } else if (at(win, SEARCH).contains(mouseX, mouseY)) {
            searchFocused = true;
        } else if (at(win, trackR()).contains(mouseX, mouseY)) {
            dragging = true;
            dragTo(mouseY);
        } else if (hovered != null) {
            select(hovered);
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
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            accept();
        } else if (searchFocused || searchOnly()) {
            searchFocused = true;
            RewardTablesScreen.typeInto(search, typed, keyCode, false);
        } else {
            typeExtras(typed, keyCode);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll -= (wheel > 0 ? 1 : -1) * CELL;
            scroll = Math.max(0, Math.min(maxScroll(cells()), scroll));
        }
    }
}
