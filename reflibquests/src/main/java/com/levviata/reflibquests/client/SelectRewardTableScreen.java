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
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.levviata.reflib.gui.Rect;
import com.levviata.reflibquests.model.Definitions.RewardTable;
import com.levviata.reflibquests.model.QuestBook;
import com.levviata.reflibquests.text.RichText;

/** "Select Reward Table": a search box over a list of the book's reward tables; clicking one picks it. Opens when a table reward is added. */
@SideOnly(Side.CLIENT)
final class SelectRewardTableScreen extends GuiScreen {

    private static final int ROW = 18;

    private final GuiScreen parent;
    private final Consumer<String> picked;
    private final TextEditor search = new TextEditor("", 60);
    private final RewardTablesScreen.Scroller scroller = new RewardTablesScreen.Scroller();
    private final List<Object[]> hits = new ArrayList<>(); // {Rect, Runnable}

    SelectRewardTableScreen(GuiScreen parent, Consumer<String> picked) {
        this.parent = parent;
        this.picked = picked;
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
        int w = Math.min(width - 12, 260);
        int h = Math.min(height - 12, 214);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        hits.clear();
        if (parent != null) {
            parent.drawScreen(-1000, -1000, partialTicks); // the screen underneath stays visible
        }
        drawDefaultBackground();
        Rect win = window();
        Gui.drawRect(win.x() - 1, win.y() - 1, win.x() + win.width() + 1, win.y() + win.height() + 1, 0xFF4C5565);
        Gui.drawRect(win.x(), win.y(), win.x() + win.width(), win.y() + 38, 0xFF3C4352);
        Gui.drawRect(win.x(), win.y() + 38, win.x() + win.width(), win.y() + win.height(), 0xFF2F3541);
        fontRenderer.drawStringWithShadow("Select Reward Table", win.x() + 6, win.y() + 6, 0xFFFFFF);
        Rect field = new Rect(win.x() + 6, win.y() + 20, win.width() - 34, 14);
        Gui.drawRect(field.x(), field.y(), field.x() + field.width(), field.y() + field.height(), 0xFF5F83AE);
        Gui.drawRect(field.x(), field.y(), field.x() + field.width(), field.y() + 1, 0xFF7FA3CE);
        RewardTablesScreen.drawFieldText(fontRenderer, field, search);
        Rect close = new Rect(win.x() + win.width() - 22, win.y() + 18, 16, 16);
        if (!Ui.questsGui("close", close.x() + 2, close.y() + 2, 12, 12, 0xFFFFFFFF)) {
            fontRenderer.drawStringWithShadow("x", close.x() + 5, close.y() + 4, 0xFF5555);
        }
        hits.add(new Object[] {close, (Runnable) () -> mc.displayGuiScreen(parent)});

        QuestBook book = ClientQuests.book();
        List<RewardTable> shown = new ArrayList<>();
        String needle = search.text().toLowerCase(Locale.ROOT).strip();
        if (book != null) {
            for (RewardTable t : book.tables().values()) {
                String name = RichText.stripCodes(t.title.isEmpty() ? t.id : t.title);
                if (needle.isEmpty() || name.toLowerCase(Locale.ROOT).contains(needle)) {
                    shown.add(t);
                }
            }
            shown.sort((a, b) -> RewardTablesScreen.plain(a).compareTo(RewardTablesScreen.plain(b)));
        }
        Rect list = new Rect(win.x() + 4, win.y() + 42, win.width() - 8, win.height() - 46);
        scroller.layout(list, shown.size() * ROW, ROW);
        RewardTablesScreen.scissor(mc, list);
        int rowWidth = list.width() - (scroller.max() > 0 ? 12 : 0);
        for (int i = 0; i < shown.size(); i++) {
            RewardTable t = shown.get(i);
            int y = list.y() + i * ROW - scroller.offset;
            if (y + ROW < list.y() || y > list.y() + list.height()) {
                continue;
            }
            Rect row = new Rect(list.x(), y, rowWidth, ROW);
            if (row.contains(mouseX, mouseY) && list.contains(mouseX, mouseY)) {
                Gui.drawRect(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), 0xFF4C5565);
            }
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(RewardTablesScreen.iconOf(t), row.x() + 2, row.y() + 1);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            Ui.drawText(fontRenderer, RichText.toRainbowAware(t.title.isEmpty() ? t.id : t.title), row.x() + 22, row.y() + 5, 0xFFFFFFFF, row.width() - 26);
            final String id = t.id;
            hits.add(new Object[] {RewardTablesScreen.clip(row, list), (Runnable) () -> {
                mc.displayGuiScreen(parent);
                picked.accept(id);
            }});
        }
        RewardTablesScreen.unscissor();
        if (shown.isEmpty()) {
            fontRenderer.drawStringWithShadow(needle.isEmpty() ? "No reward tables yet." : "Nothing found.", list.x() + 6, list.y() + 6, 0x888899);
        }
        scroller.draw();
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
            scroller.wheel(wheel);
        }
    }
}
