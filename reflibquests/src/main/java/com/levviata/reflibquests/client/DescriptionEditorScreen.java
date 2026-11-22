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

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflib.gui.Rect;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.QuestBook;
import com.levviata.reflibquests.net.QuestNet;
import com.levviata.reflibquests.text.RichText;

/**
 * "Quest Description Editor": the description as raw text (one entry per line), with a toolbar for formatting, links,
 * page breaks and images, plus undo and redo. Accept saves, the red cross asks before throwing changes away.
 */
@SideOnly(Side.CLIENT)
final class DescriptionEditorScreen extends GuiScreen {

    private static final int ROW = 10;
    private static final int[] PALETTE = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF };

    private enum Overlay { NONE, PALETTE, CONFIRM, QUEST }

    private record Tool(String tip, Consumer<Rect> paint, Runnable action, boolean enabled) {}

    private final GuiScreen parent;
    private final Quest quest;
    private final TextEditor editor;
    private int scrollRows;
    private Overlay overlay = Overlay.NONE;
    private Rect paletteButton = new Rect(0, 0, 0, 0);
    private final TextEditor questQuery = new TextEditor("", 40);
    private List<Quest> questRows = List.of();
    private int questScroll;
    private final List<Object[]> hits = new ArrayList<>(); // {Rect, Runnable}
    private final List<Object[]> toolTips = new ArrayList<>(); // {Rect, String}
    private List<TextEditor.Row> rows = List.of();
    private Rect area = new Rect(0, 0, 0, 0);
    private boolean dragging;

    DescriptionEditorScreen(GuiScreen parent, Quest quest) {
        this.parent = parent;
        this.quest = quest;
        this.editor = new TextEditor(QuestBookWidget.codesAsTyped(String.join("\n", quest.description)), 20000);
    }

    @Override
    public void initGui() {
        super.initGui();
        org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        org.lwjgl.input.Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private Rect window() {
        int w = Math.min(width - 24, 560);
        int h = Math.min(height - 24, 380);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    // ---- actions -------------------------------------------------------------------------------------------------------

    private void accept() {
        List<DataNode> lines = new ArrayList<>();
        String[] parts = editor.text().split("\n", -1);
        int last = parts.length - 1;
        while (last >= 0 && parts[last].isEmpty()) {
            last--;
        }
        for (int i = 0; i <= last; i++) {
            lines.add(new DataNode.Str(parts[i]));
        }
        TableEdits.send("set_text", DataNode.Compound.empty().with("id", quest.id).with("title", quest.title)
                .with("subtitle", quest.subtitle).with("desc", new DataNode.Lst(lines)));
        mc.displayGuiScreen(parent);
    }

    private void close() {
        if (editor.changed()) {
            overlay = Overlay.CONFIRM;
        } else {
            mc.displayGuiScreen(parent);
        }
    }

    private void style(String code) {
        editor.surround("&" + code, "&r");
        ensureVisible();
    }

    /** Puts a whole line into the text at the caret, on a line of its own. */
    private void insertLine(String line) {
        String t = editor.text();
        int c = editor.caret();
        boolean atLineStart = c == 0 || t.charAt(c - 1) == '\n';
        editor.insert((atLineStart ? "" : "\n") + line + "\n", false);
        ensureVisible();
    }

    private void pageBreak() {
        insertLine(RichText.PAGE_BREAK + "\n(new page placeholder text)");
    }

    private void convertToJson(boolean all) {
        String t = editor.text();
        if (all) {
            String[] parts = t.split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                String json = RichText.toJsonLine(parts[i]);
                if (json != null && !parts[i].isBlank() && !parts[i].trim().startsWith("{") && !parts[i].trim().startsWith("[")) {
                    parts[i] = json;
                }
            }
            editor.replaceAllText(String.join("\n", parts));
            return;
        }
        int c = editor.caret();
        int start = t.lastIndexOf('\n', Math.max(0, c - 1)) + 1;
        if (c > 0 && t.charAt(c - 1) == '\n') {
            start = c;
        }
        int end = t.indexOf('\n', start);
        end = end < 0 ? t.length() : end;
        String json = RichText.toJsonLine(t.substring(start, end));
        if (json != null) {
            editor.replaceRange(start, end, json);
        }
    }

    private void openImageDialog() {
        mc.displayGuiScreen(new InsertDialogs.ImageDialog(this, tag -> insertLine(tag)));
    }

    private void openLinkDialog() {
        mc.displayGuiScreen(new InsertDialogs.LinkDialog(this, json -> insertLine(json)));
    }

    /**
     * The "H" button: asks for the "Hovered tooltip" and makes the selected text (or the line the caret is on) a JSON entry
     * that shows it. An entry that already is JSON is left alone.
     */
    private void openHoverPrompt() {
        mc.displayGuiScreen(new NamePrompt(this, width / 2 - 100, height / 3, "Hovered tooltip", "", 300, tip -> {
            if (tip.isBlank()) {
                return;
            }
            if (editor.hasSelection()) {
                editor.replaceSelectionOrAll(sel -> RichText.hoverJson(RichText.stripCodes(sel), tip.strip()));
                return;
            }
            String t = editor.text();
            int c = editor.caret();
            int start = c > 0 && t.charAt(c - 1) == '\n' ? c : t.lastIndexOf('\n', Math.max(0, c - 1)) + 1;
            int end = t.indexOf('\n', start);
            end = end < 0 ? t.length() : end;
            String line = t.substring(start, end);
            if (!line.isBlank() && !line.trim().startsWith("{") && !line.trim().startsWith("[")) {
                editor.replaceRange(start, end, RichText.hoverJson(RichText.stripCodes(line), tip.strip()));
            }
        }));
    }

    private void openQuestPicker() {
        overlay = Overlay.QUEST;
        questQuery.replaceAllText("");
        questScroll = 0;
        refreshQuests();
    }

    private void refreshQuests() {
        QuestBook book = ClientQuests.book();
        List<Quest> out = new ArrayList<>();
        if (book != null) {
            String q = questQuery.text().toLowerCase(Locale.ROOT);
            for (Quest other : book.allQuests()) {
                if (q.isEmpty() || RichText.stripCodes(QuestText.rawTitle(other)).toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(other);
                }
            }
            out.sort(java.util.Comparator.comparing(x -> RichText.stripCodes(QuestText.rawTitle(x)).toLowerCase(Locale.ROOT)));
        }
        questRows = out.size() > 600 ? out.subList(0, 600) : out;
        questScroll = 0;
    }

    private void ensureVisible() {
        if (rows.isEmpty() || area.height() <= 0) {
            return;
        }
        List<TextEditor.Row> now = editor.layout(fontRenderer, area.width() - 8);
        int row = editor.rowOf(now, editor.caret());
        int visible = Math.max(1, (area.height() - 4) / ROW);
        if (row < scrollRows) {
            scrollRows = row;
        } else if (row >= scrollRows + visible) {
            scrollRows = row - visible + 1;
        }
    }

    // ---- drawing -------------------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        hits.clear();
        toolTips.clear();
        drawDefaultBackground();
        FontRenderer fr = fontRenderer;
        Rect win = window();
        Ui.box(win, 0xFF2B2F3A);
        fr.drawStringWithShadow("Quest Description Editor", win.x() + win.width() / 2 - fr.getStringWidth("Quest Description Editor") / 2, win.y() + 4, 0xFFFFFF);
        // toolbar
        Rect bar = new Rect(win.x() + 3, win.y() + 16, win.width() - 6, 22);
        Gui.drawRect(bar.x(), bar.y(), bar.x() + bar.width(), bar.y() + bar.height(), 0xFF1F222B);
        List<Object> items = toolbar(fr);
        int x = bar.x() + 2;
        for (Object o : items) {
            if (o instanceof String) {
                x += 8;
                continue;
            }
            Tool t = (Tool) o;
            Rect b = new Rect(x, bar.y() + 2, 18, 18);
            boolean hover = b.contains(mouseX, mouseY) && overlay == Overlay.NONE;
            Ui.box(b, !t.enabled() ? 0xFF1A1C23 : hover ? 0xFF3C4254 : 0xFF2A2E3A);
            t.paint().accept(b);
            if (t.enabled()) {
                hits.add(new Object[] {b, t.action()});
            }
            toolTips.add(new Object[] {b, t.tip()});
            if (t.tip().startsWith("Palette")) {
                paletteButton = b;
            }
            x += 20;
        }
        Rect cancel = new Rect(bar.x() + bar.width() - 20, bar.y() + 2, 18, 18);
        Ui.box(cancel, cancel.contains(mouseX, mouseY) ? 0xFF5A2A2A : 0xFF3A2328);
        if (!Ui.icon(Ui.LIBRARY, "textures/icons/cancel.png", cancel.x() + 1, cancel.y() + 1, 16, 16, 0xFFFFFFFF)) {
            fr.drawStringWithShadow("x", cancel.x() + 6, cancel.y() + 5, 0xFF5555);
        }
        hits.add(new Object[] {cancel, (Runnable) this::close});
        toolTips.add(new Object[] {cancel, "Cancel"});
        // text area
        area = new Rect(win.x() + 4, bar.y() + bar.height() + 4, win.width() - 8, win.height() - (bar.y() - win.y()) - bar.height() - 8);
        Gui.drawRect(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(), 0xFF3B4150);
        rows = editor.layout(fr, area.width() - 8);
        int visible = Math.max(1, (area.height() - 4) / ROW);
        scrollRows = Math.max(0, Math.min(Math.max(0, rows.size() - visible), scrollRows));
        String text = editor.text();
        ScissorBox.on(area);
        int caretRow = editor.rowOf(rows, editor.caret());
        for (int i = 0; i < visible && scrollRows + i < rows.size(); i++) {
            TextEditor.Row r = rows.get(scrollRows + i);
            int y = area.y() + 2 + i * ROW;
            if (editor.hasSelection()) {
                int s = Math.max(editor.selStart(), r.start());
                int e = Math.min(editor.selEnd(), r.end());
                if (e > s || editor.selStart() <= r.start() && editor.selEnd() > r.end()) {
                    int x1 = area.x() + 4 + fr.getStringWidth(text.substring(r.start(), Math.max(r.start(), s)));
                    int x2 = area.x() + 4 + fr.getStringWidth(text.substring(r.start(), Math.max(r.start(), e)));
                    Gui.drawRect(x1, y - 1, Math.max(x2, x1 + 3), y + ROW - 1, 0xAA3A6FD0);
                }
            }
            fr.drawString(text.substring(r.start(), r.end()), area.x() + 4, y, 0xFFFFFF);
            if (scrollRows + i == caretRow && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = area.x() + 4 + fr.getStringWidth(text.substring(r.start(), Math.min(editor.caret(), r.end())));
                Gui.drawRect(cx, y - 1, cx + 1, y + ROW - 1, 0xFFFFFFFF);
            }
        }
        ScissorBox.off();
        if (rows.size() > visible) {
            int knob = Math.max(10, area.height() * visible / rows.size());
            int ky = area.y() + (area.height() - knob) * scrollRows / Math.max(1, rows.size() - visible);
            Gui.drawRect(area.x() + area.width() - 3, ky, area.x() + area.width() - 1, ky + knob, 0xFF8A8F9C);
        }
        drawOverlay(fr, mouseX, mouseY);
        // tooltips of the toolbar
        if (overlay == Overlay.NONE) {
            for (Object[] t : toolTips) {
                if (((Rect) t[0]).contains(mouseX, mouseY)) {
                    ScaledResolution res = new ScaledResolution(mc);
                    Ui.drawTooltip(fr, java.util.Arrays.asList(((String) t[1]).split("\n")), mouseX, mouseY, res.getScaledWidth(), res.getScaledHeight());
                    break;
                }
            }
        }
    }

    private List<Object> toolbar(FontRenderer fr) {
        List<Object> list = new ArrayList<>();
        list.add(new Tool("Accept\n§7[Shift + Enter]", b -> iconOrText(b, "textures/icons/accept.png", "ok"), this::accept, true));
        list.add("gap");
        list.add(new Tool("Undo\n§7[Ctrl + Z]", b -> iconOrText(b, "textures/icons/undo.png", "<"), editor::undo, editor.canUndo()));
        list.add(new Tool("Redo\n§7[Ctrl + Y]", b -> iconOrText(b, "textures/icons/redo.png", ">"), editor::redo, editor.canRedo()));
        list.add("gap");
        list.add(new Tool("Bold\n§7[Alt + B]", b -> glyph(b, "§lB"), () -> style("l"), true));
        list.add(new Tool("Italic\n§7[Alt + I]", b -> glyph(b, "§oI"), () -> style("o"), true));
        list.add(new Tool("Underline\n§7[Alt + U]", b -> glyph(b, "§nU"), () -> style("n"), true));
        list.add(new Tool("Strikethrough\n§7[Alt + S]", b -> glyph(b, "§mS"), () -> style("m"), true));
        list.add(new Tool("Palette\n§7Pick a colour", b -> {
            Gui.drawRect(b.x() + 3, b.y() + 3, b.x() + 9, b.y() + 9, 0xFFFF5555);
            Gui.drawRect(b.x() + 9, b.y() + 3, b.x() + 15, b.y() + 9, 0xFF55FF55);
            Gui.drawRect(b.x() + 3, b.y() + 9, b.x() + 9, b.y() + 15, 0xFF5555FF);
            Gui.drawRect(b.x() + 9, b.y() + 9, b.x() + 15, b.y() + 15, 0xFFFFFF55);
        }, () -> overlay = overlay == Overlay.PALETTE ? Overlay.NONE : Overlay.PALETTE, true));
        list.add(new Tool("Insert URL/Docs Link\n§7[Alt + L]", b -> {
            if (!Ui.questsGui("chain_link", b.x() + 1, b.y() + 1, 16, 16, 0xFFFFFFFF)) {
                glyph(b, "L");
            }
        }, this::openLinkDialog, true));
        list.add(new Tool("Insert Quest Link\n§7[Alt + Q]", b -> glyph(b, "Q"), this::openQuestPicker, true));
        list.add(new Tool("Hovered Tooltip\n§7Text shown when the selected text is hovered", b -> glyph(b, "H"), this::openHoverPrompt, true));
        list.add(new Tool("Clear Formatting\n§7[Alt + R]", b -> {
            if (!Ui.questsGui("eraser", b.x() + 1, b.y() + 1, 16, 16, 0xFFFFFFFF)) {
                glyph(b, "R");
            }
        }, () -> editor.replaceSelectionOrAll(RichText::clearFormatting), true));
        list.add("gap");
        list.add(new Tool("Add Page Break\n§7[Alt + P]", b -> {
            if (!Ui.questsGui("pagebreak", b.x() + 1, b.y() + 1, 16, 16, 0xFFFFFFFF)) {
                glyph(b, "P");
            }
        }, this::pageBreak, true));
        list.add(new Tool("Image\n§7[Alt + M]", b -> iconOrText(b, "textures/icons/art.png", "I"), this::openImageDialog, true));
        list.add(new Tool("Convert line to Json\n§7Hold Shift: convert all lines\n§7[Alt + J]", b -> glyph(b, "{}"),
                () -> convertToJson(GuiScreen.isShiftKeyDown()), true));
        return list;
    }

    private static void iconOrText(Rect b, String libraryIcon, String fallback) {
        if (!Ui.icon(Ui.LIBRARY, libraryIcon, b.x() + 1, b.y() + 1, 16, 16, 0xFFFFFFFF)) {
            glyph(b, fallback);
        }
    }

    private static void glyph(Rect b, String text) {
        FontRenderer fr = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
        fr.drawStringWithShadow(text, b.x() + 9 - fr.getStringWidth(text) / 2, b.y() + 5, 0xFFFFFF);
    }

    private void drawOverlay(FontRenderer fr, int mouseX, int mouseY) {
        if (overlay == Overlay.PALETTE) {
            Rect box = new Rect(paletteButton.x(), paletteButton.y() + 20, 4 * 14 + 6, 4 * 14 + 6);
            Ui.box(box, 0xFF23262F);
            for (int i = 0; i < 16; i++) {
                Rect sw = new Rect(box.x() + 3 + (i % 4) * 14, box.y() + 3 + (i / 4) * 14, 12, 12);
                Gui.drawRect(sw.x(), sw.y(), sw.x() + 12, sw.y() + 12, 0xFF000000 | PALETTE[i]);
                if (sw.contains(mouseX, mouseY)) {
                    Ui.outline(sw, 0xFFFFFFFF);
                }
                final String code = Integer.toHexString(i);
                hits.add(new Object[] {sw, (Runnable) () -> {
                    overlay = Overlay.NONE;
                    style(code);
                }});
            }
        } else if (overlay == Overlay.CONFIRM) {
            Gui.drawRect(0, 0, width, height, 0x88000000);
            Rect box = new Rect(width / 2 - 90, height / 2 - 28, 180, 56);
            Ui.box(box, 0xFF2B2F3A);
            fr.drawStringWithShadow("Discard Unsaved Changes?", box.x() + 90 - fr.getStringWidth("Discard Unsaved Changes?") / 2, box.y() + 10, 0xFFFFFF);
            Rect yes = new Rect(box.x() + 12, box.y() + 30, 70, 18);
            Rect no = new Rect(box.x() + 98, box.y() + 30, 70, 18);
            Ui.box(yes, yes.contains(mouseX, mouseY) ? 0xFF3A4054 : 0xFF23262F);
            Ui.box(no, no.contains(mouseX, mouseY) ? 0xFF3A4054 : 0xFF23262F);
            fr.drawStringWithShadow("Discard", yes.x() + 35 - fr.getStringWidth("Discard") / 2, yes.y() + 5, 0xFF8080);
            fr.drawStringWithShadow("Keep editing", no.x() + 35 - fr.getStringWidth("Keep editing") / 2, no.y() + 5, 0xFFFFFF);
            hits.clear();
            hits.add(new Object[] {yes, (Runnable) () -> mc.displayGuiScreen(parent)});
            hits.add(new Object[] {no, (Runnable) () -> overlay = Overlay.NONE});
        } else if (overlay == Overlay.QUEST) {
            Gui.drawRect(0, 0, width, height, 0x88000000);
            Rect box = new Rect(width / 2 - 150, height / 2 - 120, 300, 240);
            Ui.box(box, 0xFF2B2F3A);
            fr.drawStringWithShadow("Select Quest Object", box.x() + 8, box.y() + 7, 0xFFFFFF);
            Rect field = new Rect(box.x() + 6, box.y() + 20, box.width() - 12, 14);
            Gui.drawRect(field.x(), field.y(), field.x() + field.width(), field.y() + field.height(), 0xFF4C7BC4);
            boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
            RewardTablesScreen.drawFieldText(fr, field, questQuery);
            Rect list = new Rect(box.x() + 6, box.y() + 38, box.width() - 12, box.height() - 44);
            Gui.drawRect(list.x(), list.y(), list.x() + list.width(), list.y() + list.height(), 0xFF181A20);
            int visible = list.height() / 14;
            questScroll = Math.max(0, Math.min(Math.max(0, questRows.size() - visible), questScroll));
            hits.clear();
            for (int i = 0; i < visible && questScroll + i < questRows.size(); i++) {
                Quest q = questRows.get(questScroll + i);
                Rect rr = new Rect(list.x() + 1, list.y() + i * 14, list.width() - 2, 14);
                if (rr.contains(mouseX, mouseY)) {
                    Gui.drawRect(rr.x(), rr.y(), rr.x() + rr.width(), rr.y() + rr.height(), 0x55FFFFFF);
                }
                Ui.drawText(fr, QuestText.title(q), rr.x() + 3, rr.y() + 3, 0xFFFFE060, rr.width() - 6);
                hits.add(new Object[] {rr, (Runnable) () -> {
                    overlay = Overlay.NONE;
                    insertLine(RichText.linkJson(RichText.stripCodes(QuestText.rawTitle(q)), "change_page", q.id));
                }});
            }
        }
    }

    // ---- input ------------------------------------------------------------------------------------------------------------

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Rect r = (Rect) hits.get(i)[0];
            if (r.contains(mouseX, mouseY) && button == 0) {
                ((Runnable) hits.get(i)[1]).run();
                return;
            }
        }
        if (overlay == Overlay.PALETTE) {
            overlay = Overlay.NONE;
            return;
        }
        if (overlay == Overlay.NONE && area.contains(mouseX, mouseY) && button == 0 && !rows.isEmpty()) {
            placeCaret(mouseX, mouseY, GuiScreen.isShiftKeyDown());
            dragging = true;
        }
    }

    private void placeCaret(int mouseX, int mouseY, boolean extend) {
        int row = scrollRows + Math.max(0, (mouseY - area.y() - 2) / ROW);
        row = Math.min(row, rows.size() - 1);
        editor.setCaret(editor.indexAt(fontRenderer, rows, row, mouseX - area.x() - 4), extend);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if (dragging && overlay == Overlay.NONE && !rows.isEmpty()) {
            placeCaret(mouseX, mouseY, true);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
    }

    @Override
    protected void keyTyped(char typed, int keyCode) throws IOException {
        if (overlay == Overlay.QUEST) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                overlay = Overlay.NONE;
            } else {
                String before = questQuery.text();
                RewardTablesScreen.typeInto(questQuery, typed, keyCode, false);
                if (!before.equals(questQuery.text())) {
                    refreshQuests();
                }
            }
            return;
        }
        if (overlay != Overlay.NONE) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                overlay = Overlay.NONE;
            }
            return;
        }
        boolean ctrl = GuiScreen.isCtrlKeyDown();
        boolean shift = GuiScreen.isShiftKeyDown();
        boolean alt = GuiScreen.isAltKeyDown();
        List<TextEditor.Row> layout = rows.isEmpty() ? editor.layout(fontRenderer, Math.max(20, area.width() - 8)) : rows;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return;
        }
        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && shift) {
            accept();
            return;
        }
        if (ctrl) {
            switch (keyCode) {
                case Keyboard.KEY_Z -> editor.undo();
                case Keyboard.KEY_Y -> editor.redo();
                case Keyboard.KEY_A -> editor.selectAll();
                case Keyboard.KEY_C -> {
                    if (editor.hasSelection()) {
                        GuiScreen.setClipboardString(editor.selected());
                    }
                }
                case Keyboard.KEY_X -> {
                    if (editor.hasSelection()) {
                        GuiScreen.setClipboardString(editor.selected());
                        editor.deleteSelection();
                    }
                }
                case Keyboard.KEY_V -> editor.insert(GuiScreen.getClipboardString(), false);
                default -> { }
            }
            ensureVisible();
            return;
        }
        if (alt) {
            switch (keyCode) {
                case Keyboard.KEY_B -> style("l");
                case Keyboard.KEY_I -> style("o");
                case Keyboard.KEY_U -> style("n");
                case Keyboard.KEY_S -> style("m");
                case Keyboard.KEY_R -> editor.replaceSelectionOrAll(RichText::clearFormatting);
                case Keyboard.KEY_P -> pageBreak();
                case Keyboard.KEY_L -> openLinkDialog();
                case Keyboard.KEY_Q -> openQuestPicker();
                case Keyboard.KEY_M -> openImageDialog();
                case Keyboard.KEY_J -> convertToJson(shift);
                default -> { }
            }
            return;
        }
        switch (keyCode) {
            case Keyboard.KEY_LEFT -> editor.left(shift);
            case Keyboard.KEY_RIGHT -> editor.right(shift);
            case Keyboard.KEY_UP -> editor.vertical(fontRenderer, layout, -1, shift);
            case Keyboard.KEY_DOWN -> editor.vertical(fontRenderer, layout, 1, shift);
            case Keyboard.KEY_HOME -> editor.home(layout, shift);
            case Keyboard.KEY_END -> editor.end(layout, shift);
            case Keyboard.KEY_BACK -> editor.backspace();
            case Keyboard.KEY_DELETE -> editor.delete();
            case Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> editor.insert("\n", false);
            default -> {
                if (ChatAllowedCharacters.isAllowedCharacter(typed)) {
                    editor.insert(String.valueOf(typed));
                }
            }
        }
        ensureVisible();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            if (overlay == Overlay.QUEST) {
                questScroll -= Integer.signum(wheel) * 3;
            } else {
                scrollRows -= Integer.signum(wheel) * 3;
            }
        }
    }

    private static final class ScissorBox {
        static void on(Rect r) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            int f = new ScaledResolution(mc).getScaleFactor();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(r.x() * f, mc.displayHeight - (r.y() + r.height()) * f, Math.max(0, r.width() * f), Math.max(0, r.height() * f));
        }

        static void off() {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }
}
