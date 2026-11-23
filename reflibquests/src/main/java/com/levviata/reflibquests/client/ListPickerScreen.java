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
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.levviata.reflib.gui.Rect;

@SideOnly(Side.CLIENT)
final class ListPickerScreen extends GuiScreen {

    private static final int ROW = 14;

    record Option(String label, String value) {}

    private final GuiScreen parent;
    private final String title;
    private final List<Option> options;
    private final Consumer<String> picked;
    private final TextEditor search = new TextEditor("", 60);
    private final RewardTablesScreen.Scroller scroller = new RewardTablesScreen.Scroller();
    private final List<Object[]> hits = new ArrayList<>();

    ListPickerScreen(GuiScreen parent, String title, List<Option> options, Consumer<String> picked) {
        this.parent = parent;
        this.title = title;
        this.options = options;
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
            parent.drawScreen(-1000, -1000, partialTicks);
        }
        drawDefaultBackground();
        Rect win = window();
        Gui.drawRect(win.x() - 1, win.y() - 1, win.x() + win.width() + 1, win.y() + win.height() + 1, 0xFF4C5565);
        Gui.drawRect(win.x(), win.y(), win.x() + win.width(), win.y() + 38, 0xFF3C4352);
        Gui.drawRect(win.x(), win.y() + 38, win.x() + win.width(), win.y() + win.height(), 0xFF2F3541);
        fontRenderer.drawStringWithShadow(title, win.x() + 6, win.y() + 6, 0xFFFFFF);
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
        List<Option> shown = new ArrayList<>();
        for (Option o : options) {
            if (needle.isEmpty() || o.label().toLowerCase(Locale.ROOT).contains(needle) || o.value().toLowerCase(Locale.ROOT).contains(needle)) {
                shown.add(o);
            }
        }
        Rect list = new Rect(win.x() + 4, win.y() + 42, win.width() - 8, win.height() - 46);
        scroller.layout(list, shown.size() * ROW, ROW);
        RewardTablesScreen.scissor(mc, list);
        int rowWidth = list.width() - (scroller.max() > 0 ? 12 : 0);
        for (int i = 0; i < shown.size(); i++) {
            Option o = shown.get(i);
            int y = list.y() + i * ROW - scroller.offset;
            if (y + ROW < list.y() || y > list.y() + list.height()) {
                continue;
            }
            Rect row = new Rect(list.x(), y, rowWidth, ROW);
            if (row.contains(mouseX, mouseY) && list.contains(mouseX, mouseY)) {
                Gui.drawRect(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), 0xFF4C5565);
            }
            Ui.drawText(fontRenderer, o.label(), row.x() + 4, row.y() + 3, 0xFFFFFFFF, row.width() - 8);
            final String value = o.value();
            hits.add(new Object[] {RewardTablesScreen.clip(row, list), (Runnable) () -> {
                mc.displayGuiScreen(parent);
                picked.accept(value);
            }});
        }
        RewardTablesScreen.unscissor();
        if (shown.isEmpty()) {
            fontRenderer.drawStringWithShadow("Nothing found.", list.x() + 6, list.y() + 6, 0x888899);
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
