/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflib.gui.Rect;
import com.levviata.reflibquests.io.Ids;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.RewardTable;
import com.levviata.reflibquests.model.Definitions.WeightedReward;
import com.levviata.reflibquests.model.QuestBook;
import com.levviata.reflibquests.text.RichText;

/**
 * "Edit Reward Tables": every table in the book, alphabetically, with how many quest rewards use it. Add creates a table
 * and lets you name it at once; clicking a table opens it; right-click renames. Changes show at once and are sent to the
 * server when these windows are left (see {@link TableEdits}).
 */
@SideOnly(Side.CLIENT)
final class RewardTablesScreen extends GuiScreen {

    private static final int ROW = 20;

    private final GuiScreen parent;
    private final Set<String> fresh = new HashSet<>();
    private final List<Object[]> hits = new ArrayList<>(); // {Rect, Runnable}
    private final List<Object[]> rowRects = new ArrayList<>(); // {Rect, String id}
    private final Scroller scroller = new Scroller();
    private Map<String, int[]> usage = new HashMap<>();
    private long usageAt;
    private String scrollTo = "";
    private String editingId = "";
    private TextEditor edit = new TextEditor("", 1000);
    private String armedDelete = "";
    private long armedUntil;
    private List<String> pendingTip;

    RewardTablesScreen(GuiScreen parent) {
        this.parent = parent;
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
        commitEdit();
        super.onGuiClosed();
    }

    // ------------------------------------------------------------------ shared helpers

    static DataNode.Compound args(String k, String v) {
        return DataNode.Compound.empty().with(k, v);
    }

    static String newId() {
        long v = new Random().nextLong();
        return v == 0 ? "0000000000000001" : String.format("%016X", v);
    }

    static String plain(RewardTable t) {
        return RichText.stripCodes(t.title.isEmpty() ? t.id : t.title).toLowerCase(Locale.ROOT);
    }

    static ItemStack iconOf(RewardTable t) {
        for (WeightedReward w : t.entries) {
            ItemStack s = RewardsScreen.rewardIcon(w.reward());
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        return new ItemStack(Blocks.CHEST);
    }

    static String trim(float v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 100) / 100f);
    }

    static Rect clip(Rect r, Rect to) {
        int x0 = Math.max(r.x(), to.x());
        int y0 = Math.max(r.y(), to.y());
        int x1 = Math.min(r.x() + r.width(), to.x() + to.width());
        int y1 = Math.min(r.y() + r.height(), to.y() + to.height());
        return new Rect(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    static void scissor(net.minecraft.client.Minecraft mc, Rect r) {
        int f = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(r.x() * f, mc.displayHeight - (r.y() + r.height()) * f, Math.max(0, r.width() * f), Math.max(0, r.height() * f));
    }

    static void unscissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /** The game's own scroll bar: a dark track with a bevelled grey thumb. Drag it, click the track, or use the wheel. */
    static final class Scroller {
        int offset;
        private int content;
        private int step = 20;
        private Rect bar;
        private int thumb;
        private boolean dragging;

        void layout(Rect view, int contentHeight, int rowStep) {
            content = contentHeight;
            step = rowStep;
            int max = Math.max(0, content - view.height());
            offset = Math.max(0, Math.min(max, offset));
            bar = max > 0 ? new Rect(view.x() + view.width() - 10, view.y(), 10, view.height()) : null;
            thumb = max > 0 ? Math.max(15, view.height() * view.height() / content) : 0;
        }

        int max() {
            return bar == null ? 0 : Math.max(0, content - bar.height());
        }

        void draw() {
            if (bar == null) {
                return;
            }
            Gui.drawRect(bar.x(), bar.y(), bar.x() + bar.width(), bar.y() + bar.height(), 0xFF000000);
            Gui.drawRect(bar.x() + 1, bar.y() + 1, bar.x() + bar.width() - 1, bar.y() + bar.height() - 1, 0xFF373737);
            int ty = bar.y() + (bar.height() - thumb) * offset / Math.max(1, max());
            int x = bar.x() + 1;
            Gui.drawRect(x, ty, x + 8, ty + thumb, 0xFF000000);
            Gui.drawRect(x, ty, x + 7, ty + thumb - 1, 0xFFFFFFFF);
            Gui.drawRect(x + 1, ty + 1, x + 8, ty + thumb, 0xFF555555);
            Gui.drawRect(x + 1, ty + 1, x + 7, ty + thumb - 1, 0xFFC6C6C6);
        }

        boolean press(int mx, int my) {
            if (bar != null && bar.contains(mx, my)) {
                dragging = true;
                follow(my);
                return true;
            }
            return false;
        }

        void follow(int my) {
            if (bar == null) {
                return;
            }
            double f = (my - bar.y() - thumb / 2.0) / Math.max(1, bar.height() - thumb);
            offset = (int) Math.round(Math.max(0, Math.min(1, f)) * max());
        }

        void drag(int my) {
            if (dragging) {
                follow(my);
            }
        }

        void release() {
            dragging = false;
        }

        void wheel(int direction) {
            offset = Math.max(0, Math.min(max(), offset - Integer.signum(direction) * step));
        }
    }

    // ------------------------------------------------------------------ this screen

    private Rect window() {
        int w = Math.min(width - 20, 300);
        int h = Math.min(height - 20, 300);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    private void refreshUsage(QuestBook book) {
        long now = System.currentTimeMillis();
        if (now < usageAt) {
            return;
        }
        usageAt = now + 500;
        Map<String, int[]> counts = new HashMap<>();
        for (Chapter c : book.chapters()) {
            for (Quest q : c.quests) {
                for (Reward r : q.rewards) {
                    String id = r.params().getString("table_id", "");
                    if (!id.isEmpty()) {
                        counts.computeIfAbsent(Ids.normalize(id), k -> new int[1])[0]++;
                    }
                }
            }
        }
        usage = counts;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        hits.clear();
        rowRects.clear();
        drawDefaultBackground();
        FontRenderer fr = fontRenderer;
        QuestBook book = ClientQuests.book();
        Rect win = window();
        QuestArt.tile(QuestArt.DIRT, win.x(), win.y(), win.width(), win.height(), 32, 0xFF3C414D);
        Ui.outline(win, 0xFF0E0F12);
        fr.drawStringWithShadow("Edit Reward Tables", win.x() + 8, win.y() + 10, 0xFFFFFF);
        if (book == null) {
            return;
        }
        refreshUsage(book);

        Rect add = new Rect(win.x() + win.width() - 64, win.y() + 4, 58, 20);
        Ui.vanillaButton(add, add.contains(mouseX, mouseY));
        if (!Ui.icon(Ui.LIBRARY, "textures/icons/add.png", add.x() + 5, add.y() + 3, 14, 14, 0xFFFFFFFF)) {
            fr.drawStringWithShadow("+", add.x() + 8, add.y() + 6, 0x7BD21E);
        }
        fr.drawStringWithShadow("Add", add.x() + 22, add.y() + 6, 0xFFFFFF);
        hits.add(new Object[] {add, (Runnable) this::addTable});

        Rect list = new Rect(win.x() + 6, win.y() + 28, win.width() - 12, win.height() - 28 - 30);
        Gui.drawRect(list.x(), list.y(), list.x() + list.width(), list.y() + list.height(), 0xFF22252C);
        List<RewardTable> tables = new ArrayList<>(book.tables().values());
        tables.sort((a, b) -> plain(a).compareTo(plain(b)));
        scroller.layout(list, tables.size() * ROW, ROW);
        if (!scrollTo.isEmpty()) {
            for (int i = 0; i < tables.size(); i++) {
                if (tables.get(i).id.equals(scrollTo)) {
                    scroller.offset = Math.max(0, Math.min(scroller.max(), i * ROW - (list.height() - ROW) / 2));
                }
            }
            scrollTo = "";
        }

        RewardTable hoverTable = null;
        scissor(mc, list);
        int rowWidth = list.width() - (scroller.max() > 0 ? 12 : 2);
        for (int i = 0; i < tables.size(); i++) {
            RewardTable t = tables.get(i);
            int y = list.y() + i * ROW - scroller.offset;
            if (y + ROW < list.y() || y > list.y() + list.height()) {
                continue;
            }
            Rect row = new Rect(list.x() + 1, y, rowWidth, ROW - 1);
            boolean over = row.contains(mouseX, mouseY) && list.contains(mouseX, mouseY) && !scroller.dragging;
            if (fresh.contains(t.id)) {
                Gui.drawRect(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), 0xFF2E7D32);
            } else if (over) {
                Gui.drawRect(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), 0x40FFFFFF);
            }
            Gui.drawRect(row.x(), row.y() + row.height(), row.x() + row.width(), row.y() + row.height() + 1, 0xFF3A3F4B);
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(iconOf(t), row.x() + 3, row.y() + 1);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            Rect trash = new Rect(row.x() + row.width() - 16, row.y() + 3, 13, 13);
            boolean armed = t.id.equals(armedDelete) && System.currentTimeMillis() < armedUntil;
            if (t.id.equals(editingId)) {
                drawField(fr, new Rect(row.x() + 22, row.y() + 2, row.width() - 44, ROW - 5), edit);
            } else {
                Ui.drawText(fr, RichText.toRainbowAware(t.title.isEmpty() ? t.id : t.title), row.x() + 24, row.y() + 6,
                        over ? 0xFFFFE08A : 0xFFFFFFFF, row.width() - 44);
            }
            if (over || armed) {
                boolean overTrash = trash.contains(mouseX, mouseY);
                if (!Ui.icon(Ui.LIBRARY, "textures/icons/bin.png", trash.x(), trash.y(), 13, 13, armed || overTrash ? 0xFFFF5555 : 0xFFBBBBBB)) {
                    fr.drawString("x", trash.x() + 4, trash.y() + 3, armed || overTrash ? 0xFF5555 : 0xBBBBBB);
                }
                if (overTrash) {
                    pendingTip = List.of(armed ? "Click again to remove" : "Remove");
                } else if (over && !t.id.equals(editingId)) {
                    hoverTable = t;
                }
            }
            final String id = t.id;
            rowRects.add(new Object[] {clip(row, list), id});
            if (over || armed) {
                hits.add(new Object[] {clip(trash, list), (Runnable) () -> removeTable(id)});
            }
            hits.add(new Object[] {clip(row, list), (Runnable) () -> {
                if (editingId.equals(id)) {
                    return;
                }
                commitEdit();
                mc.displayGuiScreen(new TablePanel(this, id));
            }});
        }
        unscissor();
        if (tables.isEmpty()) {
            fr.drawStringWithShadow("No reward tables yet. Press Add.", list.x() + 8, list.y() + 8, 0x888899);
        }
        scroller.draw();

        Rect close = new Rect(win.x() + win.width() - 6 - 90, win.y() + win.height() - 26, 90, 20);
        Ui.vanillaButton(close, close.contains(mouseX, mouseY));
        fr.drawStringWithShadow("Close", close.x() + 45 - fr.getStringWidth("Close") / 2, close.y() + 6, 0xFFFFFF);
        hits.add(new Object[] {close, (Runnable) () -> mc.displayGuiScreen(parent)});

        if (pendingTip != null) {
            Ui.drawTooltip(fr, pendingTip, mouseX, mouseY, width, height);
            pendingTip = null;
        } else if (hoverTable != null) {
            int uses = usage.getOrDefault(hoverTable.id, new int[1])[0];
            List<String> lines = new ArrayList<>();
            lines.add("Used in " + uses + " Quest Rewards");
            float total = hoverTable.emptyWeight;
            for (WeightedReward w : hoverTable.entries) {
                total += w.weight();
            }
            int shown = 0;
            for (WeightedReward w : hoverTable.entries) {
                if (shown++ >= 12) {
                    lines.add("§7...and " + (hoverTable.entries.size() - 12) + " more");
                    break;
                }
                int pct = total > 0 ? Math.round(1000 * w.weight() / total) : 0;
                lines.add("- " + QuestText.reward(w.reward()) + " §7[" + pct / 10f + "%]");
            }
            Ui.drawTooltip(fr, lines, mouseX, mouseY, width, height);
        }
    }

    static void drawField(FontRenderer fr, Rect r, TextEditor edit) {
        Gui.drawRect(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), 0xFF4C7BC4);
        String text = edit.text();
        int caret = Math.min(edit.caret(), text.length());
        int room = r.width() - 8;
        int first = 0;
        while (first < caret && fr.getStringWidth(text.substring(first, caret)) > room) {
            first++;
        }
        String visible = fr.trimStringToWidth(text.substring(first), r.width() - 6);
        int base = r.x() + 3;
        if (edit.hasSelection()) {
            int s0 = Math.max(first, edit.selStart());
            int s1 = Math.min(first + visible.length(), edit.selEnd());
            if (s1 > s0) {
                Gui.drawRect(base + fr.getStringWidth(text.substring(first, s0)), r.y() + 1,
                        base + fr.getStringWidth(text.substring(first, s1)), r.y() + r.height() - 1, 0xAA3A6FD0);
            }
        }
        fr.drawStringWithShadow(visible, base, r.y() + (r.height() - 8) / 2, 0xFFFFFF);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = base + fr.getStringWidth(text.substring(first, Math.max(first, caret)));
            Gui.drawRect(cx, r.y() + 2, cx + 1, r.y() + r.height() - 2, 0xFFFFFFFF);
        }
    }

    /** Keys for a one-line field backed by a {@link TextEditor}; true if the key was handled. */
    static boolean typeInto(TextEditor edit, char typed, int keyCode, boolean numeric) {
        boolean shift = isShiftKeyDown();
        if (isCtrlKeyDown()) {
            switch (keyCode) {
                case Keyboard.KEY_A -> edit.selectAll();
                case Keyboard.KEY_Z -> edit.undo();
                case Keyboard.KEY_Y -> edit.redo();
                case Keyboard.KEY_C -> {
                    if (edit.hasSelection()) {
                        setClipboardString(edit.selected());
                    }
                }
                case Keyboard.KEY_X -> {
                    if (edit.hasSelection()) {
                        setClipboardString(edit.selected());
                        edit.deleteSelection();
                    }
                }
                case Keyboard.KEY_V -> edit.insert(getClipboardString().replace("\n", " "), false);
                default -> { }
            }
            return true;
        }
        switch (keyCode) {
            case Keyboard.KEY_LEFT -> edit.left(shift);
            case Keyboard.KEY_RIGHT -> edit.right(shift);
            case Keyboard.KEY_HOME -> edit.setCaret(0, shift);
            case Keyboard.KEY_END -> edit.setCaret(edit.text().length(), shift);
            case Keyboard.KEY_BACK -> edit.backspace();
            case Keyboard.KEY_DELETE -> edit.delete();
            default -> {
                boolean ok = numeric ? Character.isDigit(typed) || typed == '.' : ChatAllowedCharacters.isAllowedCharacter(typed);
                if (ok) {
                    edit.insert(String.valueOf(typed));
                }
            }
        }
        return true;
    }

    private void startEdit(String id) {
        commitEdit();
        QuestBook book = ClientQuests.book();
        RewardTable t = book == null ? null : book.tables().get(id);
        if (t == null) {
            return;
        }
        editingId = id;
        edit = new TextEditor(t.title, 1000);
        edit.selectAll();
        scrollTo = id;
    }

    private void addTable() {
        commitEdit();
        String id = newId();
        TableEdits.record("create_table", args("title", "New table").with("id", id));
        fresh.add(id);
        startEdit(id);
    }

    private void removeTable(String id) {
        if (id.equals(armedDelete) && System.currentTimeMillis() < armedUntil) {
            armedDelete = "";
            if (id.equals(editingId)) {
                editingId = "";
            }
            TableEdits.record("delete_table", args("id", id));
            fresh.remove(id);
        } else {
            armedDelete = id;
            armedUntil = System.currentTimeMillis() + 3000;
        }
    }

    private void commitEdit() {
        if (editingId.isEmpty()) {
            return;
        }
        String id = editingId;
        editingId = "";
        String text = edit.text().strip();
        QuestBook book = ClientQuests.book();
        RewardTable t = book == null ? null : book.tables().get(id);
        if (t != null && !text.isEmpty() && !text.equals(t.title)) {
            TableEdits.record("rename_table", args("id", id).with("title", text));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button == 1) {
            for (Object[] r : rowRects) {
                if (((Rect) r[0]).contains(mouseX, mouseY)) {
                    startEdit((String) r[1]);
                    return;
                }
            }
            return;
        }
        if (button != 0) {
            return;
        }
        if (scroller.press(mouseX, mouseY)) {
            return;
        }
        for (Object[] hit : hits) {
            if (((Rect) hit[0]).contains(mouseX, mouseY)) {
                ((Runnable) hit[1]).run();
                return;
            }
        }
        commitEdit();
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
        if (!editingId.isEmpty()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                editingId = "";
            } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                commitEdit();
            } else {
                typeInto(edit, typed, keyCode, false);
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
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

    // ------------------------------------------------------------------ one table

    /** The inside of one table: its rewards with weights and chances, a + menu to add, and a cog for its settings. */
    @SideOnly(Side.CLIENT)
    static final class TablePanel extends GuiScreen {

        private static final int ROW = 26;

        private final GuiScreen parent;
        private final String tableId;
        private final List<Object[]> hits = new ArrayList<>();
        private final Scroller scroller = new Scroller();
        private boolean menuOpen;
        private String editingEntry = "";
        private TextEditor weightEdit = new TextEditor("", 12);

        TablePanel(GuiScreen parent, String tableId) {
            this.parent = parent;
            this.tableId = tableId;
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
            commitWeight();
            super.onGuiClosed();
        }

        private Rect window() {
            int w = Math.min(width - 20, 340);
            int h = Math.min(height - 20, 300);
            return new Rect((width - w) / 2, (height - h) / 2, w, h);
        }

        private RewardTable table() {
            QuestBook book = ClientQuests.book();
            return book == null ? null : book.tables().get(tableId);
        }

        private void commitWeight() {
            if (editingEntry.isEmpty()) {
                return;
            }
            String rid = editingEntry;
            editingEntry = "";
            RewardTable t = table();
            if (t == null) {
                return;
            }
            try {
                float v = Float.parseFloat(weightEdit.text().strip());
                for (WeightedReward w : t.entries) {
                    if (w.reward().id().equals(rid) && v > 0 && v <= 1_000_000f && v != w.weight()) {
                        TableEdits.record("set_entry_weight", args("id", tableId).with("entry", rid).with("weight", (double) v));
                    }
                }
            } catch (NumberFormatException ignored) {
                // not a number: keep the old weight
            }
        }

        private void nudge(WeightedReward w, int by) {
            commitWeight();
            float next = w.weight() + by;
            if (next > 0 && next <= 1_000_000f) {
                TableEdits.record("set_entry_weight", args("id", tableId).with("entry", w.reward().id()).with("weight", (double) next));
            }
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            hits.clear();
            drawDefaultBackground();
            FontRenderer fr = fontRenderer;
            RewardTable table = table();
            if (table == null) {
                mc.displayGuiScreen(parent); // it was removed
                return;
            }
            Rect win = window();
            QuestArt.tile(QuestArt.DIRT, win.x(), win.y(), win.width(), win.height(), 32, 0xFF3C414D);
            Ui.outline(win, 0xFF0E0F12);
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(iconOf(table), win.x() + 8, win.y() + 7);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            Ui.drawText(fr, RichText.toRainbowAware(table.title.isEmpty() ? table.id : table.title), win.x() + 30, win.y() + 11,
                    0xFFFFFFFF, win.width() - 30 - 56);
            Rect cog = new Rect(win.x() + win.width() - 26, win.y() + 5, 20, 20);
            Rect plus = new Rect(cog.x() - 24, cog.y(), 20, 20);
            Ui.vanillaButton(plus, plus.contains(mouseX, mouseY));
            if (!Ui.icon(Ui.LIBRARY, "textures/icons/add.png", plus.x() + 2, plus.y() + 2, 16, 16, 0xFFFFFFFF)) {
                fr.drawStringWithShadow("+", plus.x() + 7, plus.y() + 6, 0x7BD21E);
            }
            Ui.vanillaButton(cog, cog.contains(mouseX, mouseY));
            if (!Ui.questsGui("settings", cog.x() + 2, cog.y() + 2, 16, 16, 0xFFDBDBDB)) {
                fr.drawStringWithShadow("S", cog.x() + 7, cog.y() + 6, 0xFFFFFF);
            }

            Rect list = new Rect(win.x() + 6, win.y() + 32, win.width() - 12, win.height() - 32 - 30);
            Gui.drawRect(list.x(), list.y(), list.x() + list.width(), list.y() + list.height(), 0xFF22252C);
            float total = table.emptyWeight;
            for (WeightedReward w : table.entries) {
                total += w.weight();
            }
            scroller.layout(list, table.entries.size() * ROW, ROW);
            int rowWidth = list.width() - (scroller.max() > 0 ? 12 : 2);
            scissor(mc, list);
            for (int i = 0; i < table.entries.size(); i++) {
                WeightedReward w = table.entries.get(i);
                int y = list.y() + i * ROW - scroller.offset;
                if (y + ROW < list.y() || y > list.y() + list.height()) {
                    continue;
                }
                Rect row = new Rect(list.x() + 1, y, rowWidth, ROW - 1);
                boolean over = row.contains(mouseX, mouseY) && list.contains(mouseX, mouseY) && !scroller.dragging;
                if (over) {
                    Gui.drawRect(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), 0x28FFFFFF);
                }
                Gui.drawRect(row.x(), row.y() + row.height(), row.x() + row.width(), row.y() + row.height() + 1, 0xFF3A3F4B);
                ItemStack icon = RewardsScreen.rewardIcon(w.reward());
                if (icon != null) {
                    RenderHelper.enableGUIStandardItemLighting();
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(row.x() + 5, row.y() + 2, 0);
                    GlStateManager.scale(1.25f, 1.25f, 1f);
                    itemRender.renderItemAndEffectIntoGUI(icon, 0, 0);
                    GlStateManager.popMatrix();
                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.disableLighting();
                }
                int right = row.x() + row.width() - 4;
                Rect del = new Rect(right - 13, row.y() + 6, 13, 13);
                Rect more = new Rect(del.x() - 17, row.y() + 6, 13, 13);
                Rect weightBox = new Rect(more.x() - 4 - 36, row.y() + 5, 36, 15);
                Rect less = new Rect(weightBox.x() - 4 - 13, row.y() + 6, 13, 13);
                small(fr, del, "x", mouseX, mouseY, list);
                small(fr, more, "+", mouseX, mouseY, list);
                small(fr, less, "-", mouseX, mouseY, list);
                String rid = w.reward().id();
                if (rid.equals(editingEntry)) {
                    drawField(fr, weightBox, weightEdit);
                } else {
                    boolean overWeight = weightBox.contains(mouseX, mouseY) && list.contains(mouseX, mouseY);
                    Ui.box(weightBox, overWeight ? 0xFF3A4054 : 0xFF181A20);
                    String weight = trim(w.weight());
                    fr.drawString(fr.trimStringToWidth(weight, 32), weightBox.x() + weightBox.width() / 2 - Math.min(32, fr.getStringWidth(weight)) / 2,
                            weightBox.y() + 4, 0xFFFFFF);
                }
                int pct = total > 0 ? Math.round(1000 * w.weight() / total) : 0;
                String pctText = pct / 10f + "%";
                fr.drawString(pctText, less.x() - 6 - fr.getStringWidth(pctText), row.y() + 9, 0x888899);
                int textRight = less.x() - 12 - fr.getStringWidth(pctText);
                Ui.drawText(fr, QuestText.reward(w.reward()), row.x() + 32, row.y() + 9, 0xFFFFFFFF, Math.max(10, textRight - row.x() - 34));
                hits.add(new Object[] {clip(del, list), (Runnable) () -> {
                    commitWeight();
                    TableEdits.record("remove_table_entry", args("id", tableId).with("entry", rid));
                }});
                hits.add(new Object[] {clip(more, list), (Runnable) () -> nudge(w, isShiftKeyDown() ? 10 : 1)});
                hits.add(new Object[] {clip(less, list), (Runnable) () -> nudge(w, isShiftKeyDown() ? -10 : -1)});
                hits.add(new Object[] {clip(weightBox, list), (Runnable) () -> {
                    if (!editingEntry.equals(rid)) {
                        commitWeight();
                        editingEntry = rid;
                        weightEdit = new TextEditor(trim(w.weight()), 12);
                        weightEdit.selectAll();
                    }
                }});
            }
            unscissor();
            if (table.entries.isEmpty()) {
                fr.drawStringWithShadow("Nothing in this table yet. Press + to add.", list.x() + 8, list.y() + 8, 0x888899);
            }
            scroller.draw();
            Rect back = new Rect(win.x() + win.width() - 6 - 90, win.y() + win.height() - 26, 90, 20);
            Ui.vanillaButton(back, back.contains(mouseX, mouseY));
            fr.drawStringWithShadow("Back", back.x() + 45 - fr.getStringWidth("Back") / 2, back.y() + 6, 0xFFFFFF);
            hits.add(new Object[] {back, (Runnable) () -> mc.displayGuiScreen(parent)});
            hits.add(new Object[] {cog, (Runnable) () -> {
                commitWeight();
                mc.displayGuiScreen(new TableProperties(this, table));
            }});
            hits.add(new Object[] {plus, (Runnable) () -> {
                commitWeight();
                menuOpen = !menuOpen;
            }});

            if (menuOpen) {
                String[] labels = {"Item (held)", "XP", "XP Levels"};
                Rect menu = new Rect(plus.x() - 40, plus.y() + 22, 110, 4 + labels.length * 16);
                // the menu's own area comes first so the lines inside it (added after) are found before it
                hits.add(0, new Object[] {menu, (Runnable) () -> { }});
                Ui.box(menu, 0xFA23262D);
                for (int i = 0; i < labels.length; i++) {
                    Rect r = new Rect(menu.x() + 1, menu.y() + 2 + i * 16, menu.width() - 2, 16);
                    fr.drawStringWithShadow(labels[i], r.x() + 6, r.y() + 4, r.contains(mouseX, mouseY) ? 0xFFFF55 : 0xFFFFFF);
                    final int which = i;
                    hits.add(0, new Object[] {r, (Runnable) () -> {
                        menuOpen = false;
                        addEntry(which);
                    }});
                }
            }
        }

        private void addEntry(int which) {
            DataNode.Compound a = args("id", tableId).with("weight", 1.0).with("entry", newId());
            switch (which) {
                case 0 -> {
                    ItemStack held = mc.player == null ? ItemStack.EMPTY : mc.player.getHeldItemMainhand();
                    if (held.isEmpty() || held.getItem().getRegistryName() == null) {
                        mc.ingameGUI.getChatGUI().printChatMessage(new TextComponentString("[Quests] Hold the item in your main hand first."));
                        return;
                    }
                    DataNode.Compound item = DataNode.Compound.empty().with("id", held.getItem().getRegistryName().toString());
                    if (held.getMetadata() != 0) {
                        item = item.with("damage", held.getMetadata());
                    }
                    a = a.with("type", "item").with("params", DataNode.Compound.empty().with("item", item).with("count", 1));
                }
                case 1 -> a = a.with("type", "xp").with("params", DataNode.Compound.empty().with("xp", 10));
                default -> a = a.with("type", "xp_levels").with("params", DataNode.Compound.empty().with("xp_levels", 1));
            }
            TableEdits.record("add_table_entry", a);
            scroller.offset = Integer.MAX_VALUE; // show the new entry at the bottom
        }

        private void small(FontRenderer fr, Rect r, String label, int mx, int my, Rect clipTo) {
            boolean over = r.contains(mx, my) && clipTo.contains(mx, my);
            Ui.box(r, over ? 0xFF3A4054 : 0xFF23262F);
            fr.drawString(label, r.x() + r.width() / 2 - fr.getStringWidth(label) / 2, r.y() + 3, 0xFFFFFF);
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
            if (button != 0) {
                return;
            }
            if (scroller.press(mouseX, mouseY)) {
                return;
            }
            for (Object[] hit : hits) {
                if (((Rect) hit[0]).contains(mouseX, mouseY)) {
                    ((Runnable) hit[1]).run();
                    return;
                }
            }
            commitWeight();
            menuOpen = false;
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
            if (!editingEntry.isEmpty()) {
                if (keyCode == Keyboard.KEY_ESCAPE) {
                    editingEntry = "";
                } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                    commitWeight();
                } else {
                    typeInto(weightEdit, typed, keyCode, true);
                }
                return;
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                if (menuOpen) {
                    menuOpen = false;
                } else {
                    mc.displayGuiScreen(parent);
                }
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

    /** Title, empty weight and loot size of one table. Nothing is changed until Accept. */
    @SideOnly(Side.CLIENT)
    private static final class TableProperties extends SettingsScreen {
        private final RewardTable table;
        private String title;
        private String empty;
        private String loot;
        private final String initialTitle;
        private final String initialEmpty;
        private final String initialLoot;

        TableProperties(GuiScreen parent, RewardTable t) {
            super(parent, RichText.stripCodes(t.title.isEmpty() ? t.id : t.title));
            this.table = t;
            this.title = t.title;
            this.empty = trim(t.emptyWeight);
            this.loot = String.valueOf(t.lootSize);
            this.initialTitle = title;
            this.initialEmpty = empty;
            this.initialLoot = loot;
            section("Reward Table");
            text("Title", () -> title, v -> title = v).tip("The name shown when a quest uses this table.");
            number("Empty Weight", false, () -> empty, v -> empty = trim(Math.max(0, Float.parseFloat(v))))
                    .tip("How likely it is that nothing is given, in the same units as the entry weights.");
            number("Loot Size", true, () -> loot, v -> loot = String.valueOf(Math.max(0, Math.min(100, Integer.parseInt(v)))))
                    .tip("How many things a loot crate of this table gives.");
        }

        @Override
        protected void accepted() {
            if (!title.strip().isEmpty() && !title.equals(initialTitle)) {
                TableEdits.record("rename_table", args("id", table.id).with("title", title.strip()));
            }
            if (!empty.equals(initialEmpty)) {
                TableEdits.record("set_table_prop", args("id", table.id).with("key", "empty_weight").with("value", empty));
            }
            if (!loot.equals(initialLoot)) {
                TableEdits.record("set_table_prop", args("id", table.id).with("key", "loot_size").with("value", loot));
            }
        }

        @Override
        protected void cancelled() {
            // nothing was changed
        }
    }
}
