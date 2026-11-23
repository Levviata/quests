/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.levviata.reflib.gui.Rect;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.ProgressEngine;
import com.levviata.reflibquests.model.QuestBook;
import com.levviata.reflibquests.model.TeamProgress;
import com.levviata.reflibquests.net.QuestNet;
import com.levviata.reflibquests.text.RichText;

@SideOnly(Side.CLIENT)
final class RewardsScreen extends GuiScreen {

    private static final int ROW = 14;

    private record Entry(Chapter chapter, Quest quest, List<Reward> rewards, boolean needsChoice) {}

    private final GuiScreen parent;
    private final List<Entry> entries = new ArrayList<>();
    private long refreshAt;
    private int scroll;
    private final List<Object[]> hits = new ArrayList<>();

    RewardsScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        if (now < refreshAt) {
            return;
        }
        refreshAt = now + 250;
        entries.clear();
        QuestBook book = ClientQuests.book();
        TeamProgress p = ClientQuests.progress();
        ProgressEngine engine = ClientQuests.engine();
        UUID me = mc.player == null ? null : mc.player.getUniqueID();
        if (book == null || p == null || engine == null || me == null) {
            return;
        }
        for (Chapter c : book.chapters()) {
            for (Quest q : c.quests) {
                if (!p.isCompleted(q.id)) {
                    continue;
                }
                List<Reward> ready = new ArrayList<>();
                boolean choice = false;
                for (Reward r : q.rewards) {
                    if (engine.canClaim(p, me, r) && QuestText.grantable(book, r, 0)) {
                        if (r.type().equals("choice")) {
                            choice = true;
                        } else {
                            ready.add(r);
                        }
                    }
                }
                if (!ready.isEmpty() || choice) {
                    entries.add(new Entry(c, q, ready, choice));
                }
            }
        }
    }

    private Rect window() {
        int w = Math.min(width - 20, 270);
        int h = Math.min(height - 20, 240);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        hits.clear();
        refresh();
        drawDefaultBackground();
        FontRenderer fr = fontRenderer;
        Rect win = window();
        QuestArt.tile(QuestArt.DIRT, win.x(), win.y(), win.width(), win.height(), 32, 0xFF3C414D);
        Ui.outline(win, 0xFF0E0F12);
        fr.drawStringWithShadow("Select Rewards", win.x() + 6, win.y() + 6, 0xFFFFFF);
        Rect list = new Rect(win.x() + 4, win.y() + 20, win.width() - 8, win.height() - 20 - 30);
        Gui.drawRect(list.x(), list.y(), list.x() + list.width(), list.y() + list.height(), 0xFF2A2E38);

        int contentHeight = 0;
        Chapter lastChapter = null;
        List<Object[]> rows = new ArrayList<>();
        for (Entry e : entries) {
            if (e.chapter() != lastChapter) {
                rows.add(new Object[] {e.chapter(), contentHeight});
                contentHeight += ROW;
                lastChapter = e.chapter();
            }
            rows.add(new Object[] {e, contentHeight});
            contentHeight += ROW;
        }
        int maxScroll = Math.max(0, contentHeight - list.height());
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        scissor(list);
        for (Object[] row : rows) {
            int y = list.y() + (Integer) row[1] - scroll;
            if (y + ROW < list.y() || y > list.y() + list.height()) {
                continue;
            }
            if (row[0] instanceof Chapter c) {
                if (!Ui.questsGui("arrow_expanded", list.x() + 3, y + 3, 8, 8, 0xFF55FF55)) {
                    fr.drawString("v", list.x() + 4, y + 3, 0x55FF55);
                }
                Ui.drawText(fr, c.title.isEmpty() ? c.id : RichText.toRainbowAware(c.title), list.x() + 14, y + 3, 0xFF55FF55, list.width() - 18);
                continue;
            }
            Entry e = (Entry) row[0];
            Rect line = new Rect(list.x() + 1, y, list.width() - 2, ROW);
            boolean hover = line.contains(mouseX, mouseY);
            if (hover) {
                Gui.drawRect(line.x(), line.y(), line.x() + line.width(), line.y() + line.height(), 0x33FFFFFF);
            }
            int iconRight = line.x() + line.width() - 4;
            if (e.needsChoice()) {
                fr.drawString("choose", iconRight - fr.getStringWidth("choose"), y + 3, 0xFFAA00);
                iconRight -= fr.getStringWidth("choose") + 4;
            }
            for (Reward rw : e.rewards()) {
                iconRight -= 14;
                ItemStack icon = rewardIcon(rw);
                if (icon != null) {
                    RenderHelper.enableGUIStandardItemLighting();
                    itemRender.renderItemAndEffectIntoGUI(icon, iconRight, y - 1);
                    RenderHelper.disableStandardItemLighting();
                } else {
                    fr.drawString("?", iconRight + 3, y + 3, 0xAAAAAA);
                }
            }
            Ui.drawText(fr, QuestText.title(e.quest()), line.x() + 8, y + 3, hover ? 0xFFFFFFA0 : 0xFFFFFFFF, Math.max(10, iconRight - line.x() - 12));
            hits.add(new Object[] {line, (Runnable) () -> claim(e)});
        }
        unscissor();
        if (entries.isEmpty()) {
            fr.drawStringWithShadow("Nothing to claim right now.", list.x() + 8, list.y() + 8, 0x888899);
        }
        if (maxScroll > 0) {
            int knob = Math.max(12, list.height() * list.height() / contentHeight);
            int ky = list.y() + (list.height() - knob) * scroll / maxScroll;
            Gui.drawRect(list.x() + list.width() - 3, ky, list.x() + list.width(), ky + knob, 0xFF808080);
        }

        int by = win.y() + win.height() - 24;
        int bw = (win.width() - 16) / 3;
        Rect xp = new Rect(win.x() + 4, by, bw, 18);
        Rect all = new Rect(win.x() + 8 + bw, by, bw, 18);
        Rect close = new Rect(win.x() + 12 + 2 * bw, by, bw, 18);
        button(fr, xp, "Claim XP", mouseX, mouseY, hasXp());
        button(fr, all, "Claim All", mouseX, mouseY, !entries.isEmpty());
        button(fr, close, "Close", mouseX, mouseY, true);
        hits.add(new Object[] {xp, (Runnable) this::claimXp});
        hits.add(new Object[] {all, (Runnable) () -> {
            if (!entries.isEmpty()) {
                QuestNet.claimAll();
            }
        }});
        hits.add(new Object[] {close, (Runnable) () -> mc.displayGuiScreen(parent)});
    }

    private void button(FontRenderer fr, Rect b, String label, int mx, int my, boolean enabled) {
        Ui.box(b, enabled && b.contains(mx, my) ? 0xFF3A4054 : 0xFF23262F);
        fr.drawStringWithShadow(label, b.x() + b.width() / 2 - fr.getStringWidth(label) / 2, b.y() + 5, enabled ? 0xFFFFFF : 0x777788);
    }

    static ItemStack rewardIcon(Reward rw) {
        switch (rw.type()) {
            case "item": {
                ItemStack s = QuestText.stackOf(rw.params().get("item"));
                return s;
            }
            case "xp":
            case "xp_levels":
                return new ItemStack(net.minecraft.init.Items.EXPERIENCE_BOTTLE);
            default:
                return new ItemStack(net.minecraft.init.Blocks.CHEST);
        }
    }

    private boolean hasXp() {
        for (Entry e : entries) {
            for (Reward r : e.rewards()) {
                if (r.type().startsWith("xp")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void claim(Entry e) {
        for (Reward r : e.rewards()) {
            QuestNet.claim(r.id(), -1);
        }
        if (e.needsChoice()) {
            mc.displayGuiScreen(parent);
        }
    }

    private void claimXp() {
        for (Entry e : entries) {
            for (Reward r : e.rewards()) {
                if (r.type().startsWith("xp")) {
                    QuestNet.claim(r.id(), -1);
                }
            }
        }
    }

    private void scissor(Rect r) {
        int f = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(r.x() * f, mc.displayHeight - (r.y() + r.height()) * f, Math.max(0, r.width() * f), Math.max(0, r.height() * f));
    }

    private void unscissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button != 0) {
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
}
