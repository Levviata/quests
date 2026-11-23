/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.levviata.reflib.gui.Rect;

@SideOnly(Side.CLIENT)
abstract class SettingsScreen extends GuiScreen {

    private static final int ROW = 11;
    private static final int GREEN = 0xFF4EA84E;
    private static final int RED = 0xFFD03A3A;
    private static final int BLUE = 0xFF2F8BFF;
    private static final int PURPLE = 0xFFB06BFF;
    private static final int ORANGE = 0xFFFFAA33;

    enum Kind { SECTION, BOOL, CHOICE, NUMBER, TEXT }

    static final class Entry {
        final Kind kind;
        final String label;
        List<String> options = List.of();
        Supplier<String> get = () -> "";
        Consumer<String> set = v -> { };
        boolean integer;
        boolean open = true;
        String tip = "";
        String def = "";
        String initial;

        Supplier<Boolean> enabled = () -> true;

        boolean valueLine;
        String min = "";
        String max = "";

        Supplier<String> info;

        boolean listOptions;

        boolean plain;

        java.util.function.Consumer<java.util.function.Consumer<String>> pick;

        java.util.function.Function<String, String> format;

        boolean swatch;

        Entry(Kind kind, String label) {
            this.kind = kind;
            this.label = label;
        }

        Entry tip(String text) {
            this.tip = text;
            return this;
        }

        Entry defaults(String text) {
            this.def = text;
            return this;
        }

        Entry enabledIf(Supplier<Boolean> when) {
            this.enabled = when;
            return this;
        }

        Entry range(String lowest, String highest) {
            this.min = lowest;
            this.max = highest;
            return this;
        }

        Entry showValue() {
            this.valueLine = true;
            return this;
        }

        Entry info(Supplier<String> line) {
            this.info = line;
            return this;
        }

        Entry listing() {
            this.listOptions = true;
            return this;
        }

        Entry plainText() {
            this.plain = true;
            return this;
        }

        Entry formatted(java.util.function.Function<String, String> how) {
            this.format = how;
            return this;
        }

        Entry withSwatch() {
            this.swatch = true;
            return this;
        }
    }

    protected final GuiScreen parent;
    private final String title;
    protected final List<Entry> entries = new ArrayList<>();
    private int scroll;
    private Rect scrollTrack;
    private int scrollMax;
    private int scrollThumb;
    private boolean draggingBar;

    private void dragBar(int mouseY) {
        if (scrollTrack == null) {
            return;
        }
        double f = (mouseY - scrollTrack.y() - 2 - scrollThumb / 2.0) / Math.max(1, scrollTrack.height() - 4 - scrollThumb);
        scroll = (int) Math.round(Math.max(0, Math.min(1, f)) * scrollMax);
    }

    private Entry editing;
    private TextEditor edit = new TextEditor("", 120);
    private Rect fieldRect;
    private final List<Object[]> hit = new ArrayList<>();
    private final List<Object[]> hoverRows = new ArrayList<>();
    private record Change(Entry entry, String before, String after) {}
    private final java.util.ArrayDeque<Change> undoStack = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Change> redoStack = new java.util.ArrayDeque<>();

    SettingsScreen(GuiScreen parent, String title) {
        this.parent = parent;
        this.title = title;
    }

    protected boolean showDefault() {
        return false;
    }

    protected abstract void accepted();

    protected abstract void cancelled();

    protected final void section(String label) {
        entries.add(new Entry(Kind.SECTION, label));
    }

    protected final Entry bool(String label, Supplier<Boolean> get, Consumer<Boolean> set) {
        Entry e = new Entry(Kind.BOOL, label);
        e.get = () -> String.valueOf(get.get());
        e.set = v -> set.accept(Boolean.parseBoolean(v));
        entries.add(e);
        return e;
    }

    protected final Entry choice(String label, List<String> options, Supplier<String> get, Consumer<String> set) {
        Entry e = new Entry(Kind.CHOICE, label);
        e.options = options;
        e.get = get;
        e.set = set;
        entries.add(e);
        return e;
    }

    protected final Entry number(String label, boolean integer, Supplier<String> get, Consumer<String> set) {
        Entry e = new Entry(Kind.NUMBER, label);
        e.integer = integer;
        e.get = get;
        e.set = set;
        entries.add(e);
        return e;
    }

    protected final Entry text(String label, Supplier<String> get, Consumer<String> set) {
        Entry e = new Entry(Kind.TEXT, label);
        e.get = get;
        e.set = set;
        entries.add(e);
        return e;
    }

    private int rowsHeight() {
        int h = 0;
        boolean open = true;
        for (Entry e : entries) {
            if (e.kind == Kind.SECTION) {
                open = e.open;
                h += ROW + 2;
            } else if (open) {
                h += ROW;
            }
        }
        return h;
    }

    private int labelWidest() {
        int w = 0;
        for (Entry e : entries) {
            if (e.kind != Kind.SECTION) {
                w = Math.max(w, fontRenderer.getStringWidth(e.label));
            }
        }
        return w;
    }

    private int sectionWidest() {
        int w = 0;
        for (Entry e : entries) {
            if (e.kind == Kind.SECTION) {
                w = Math.max(w, fontRenderer.getStringWidth(e.label));
            }
        }
        return w;
    }

    private int valueOffset() {
        return 15 + labelWidest() + 6;
    }

    private int valueWidest() {
        int w = 0;
        for (Entry e : entries) {
            if (e.kind == Kind.SECTION) {
                continue;
            }
            String v = e.get.get();
            String shown = e.format != null ? e.format.apply(v) : e.kind == Kind.TEXT && !e.plain ? "\"" + v + "\"" : v;
            w = Math.max(w, fontRenderer.getStringWidth(shown) + (e.swatch ? 12 : 0));
        }
        return w;
    }

    private int sectionCount() {
        int n = 0;
        for (Entry e : entries) {
            if (e.kind == Kind.SECTION) {
                n++;
            }
        }
        return n;
    }

    protected float uiScale() {
        return 1f;
    }

    private int vw() {
        return (int) (width / uiScale());
    }

    private int vh() {
        return (int) (height / uiScale());
    }

    private Rect window() {

        int w = Math.max(Math.max(valueOffset() + 86, valueOffset() + valueWidest() + 14), Math.max(sectionWidest() + 30, 262));
        w = Math.min(w, vw() - 20);
        int rowCount = 0;
        boolean openNow = true;
        for (Entry e : entries) {
            if (e.kind == Kind.SECTION) {
                openNow = e.open;
                rowCount += 2;
            } else if (openNow) {
                rowCount++;
            }
        }
        int h = 51 + ROW * (rowCount + 2);
        h = Math.min(h, Math.min(vh() - 20, (int) (vh() * 0.866)));
        return new Rect((vw() - w) / 2, (vh() - h) / 2, w, h);
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

    @Override
    public void drawScreen(int screenMouseX, int screenMouseY, float partialTicks) {
        drawDefaultBackground();
        GlStateManager.pushMatrix();
        GlStateManager.scale(uiScale(), uiScale(), 1f);
        drawWindow((int) (screenMouseX / uiScale()), (int) (screenMouseY / uiScale()));
        GlStateManager.popMatrix();
    }

    private void drawWindow(int mouseX, int mouseY) {
        hit.clear();
        hoverRows.clear();
        FontRenderer fr = fontRenderer;
        Rect win = window();
        Gui.drawRect(win.x() - 1, win.y() - 1, win.x() + win.width() + 1, win.y() + win.height() + 1, 0xFF101010);
        Gui.drawRect(win.x(), win.y(), win.x() + win.width(), win.y() + win.height(), 0xFF3B4151);

        fr.drawString(title.startsWith("§l") ? title : "§n" + title, win.x() + 8, win.y() + 8, 0xFFD8DEE9);
        Rect collapse = new Rect(win.x() + win.width() - 34, win.y() + 4, 14, 14);
        Rect expand = new Rect(win.x() + win.width() - 18, win.y() + 4, 14, 14);

        if (!Ui.icon(Ui.LIBRARY, "textures/icons/collapse.png", collapse.x(), collapse.y(), 14, 14, 0xFFFFFFFF)) {
            fr.drawStringWithShadow("-", collapse.x() + 4, collapse.y() + 2, 0x66CCFF);
        }
        if (!Ui.icon(Ui.LIBRARY, "textures/icons/expand.png", expand.x(), expand.y(), 14, 14, 0xFFFFFFFF)) {
            fr.drawStringWithShadow("+", expand.x() + 4, expand.y() + 2, 0x66CCFF);
        }
        hit.add(new Object[] {collapse, (Runnable) () -> entries.stream().filter(e -> e.kind == Kind.SECTION).forEach(e -> e.open = false)});
        hit.add(new Object[] {expand, (Runnable) () -> entries.stream().filter(e -> e.kind == Kind.SECTION).forEach(e -> e.open = true)});
        Rect list = new Rect(win.x() + 4, win.y() + 24, win.width() - 8, win.height() - 24 - 30);
        Gui.drawRect(list.x(), list.y(), list.x() + list.width(), list.y() + list.height(), 0xFF2E333F);
        int contentHeight = 0;
        boolean open = true;
        List<Object[]> rows = new ArrayList<>();
        for (Entry e : entries) {
            if (e.kind == Kind.SECTION) {
                open = e.open;
                rows.add(new Object[] {e, contentHeight});
                contentHeight += ROW + 2;
            } else if (open) {
                rows.add(new Object[] {e, contentHeight});
                contentHeight += ROW;
            }
        }
        int maxScroll = Math.max(0, contentHeight - list.height());
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        scissor(list);
        int valueX = list.x() + valueOffset();
        int rowWidth = list.width() - (maxScroll > 0 ? 12 : 0);
        for (Object[] row : rows) {
            Entry e = (Entry) row[0];
            int y = list.y() + (Integer) row[1] - scroll;
            if (y + ROW < list.y() || y > list.y() + list.height()) {
                continue;
            }
            Rect line = new Rect(list.x(), y, rowWidth, e.kind == Kind.SECTION ? ROW + 1 : ROW);
            if (e.kind == Kind.SECTION) {
                Gui.drawRect(line.x(), line.y(), line.x() + line.width(), line.y() + line.height(), 0xFF576074);
                Gui.drawRect(line.x() + 1, line.y() + 1, line.x() + line.width() - 1, line.y() + line.height() - 1, 0xFF3B4151);
                if (!Ui.questsGui(e.open ? "arrow_expanded" : "arrow_collapsed", line.x() + 3, line.y() + 2, 8, 8, 0xFF55FF55)) {
                    fr.drawString(e.open ? "v" : ">", line.x() + 4, line.y() + 2, 0x55FF55);
                }
                fr.drawStringWithShadow(e.label, line.x() + 15, line.y() + 2, 0xFFFF55);
                hit.add(new Object[] {line, (Runnable) () -> e.open = !e.open});
                continue;
            }
            if (line.contains(mouseX, mouseY)) {
                Gui.drawRect(line.x(), line.y(), line.x() + line.width(), line.y() + line.height(), 0x22FFFFFF);
            }
            Rect infoBox = new Rect(line.x() + 2, line.y() + 1, 10, 10);
            Ui.box(infoBox, infoBox.contains(mouseX, mouseY) ? 0xFF55607A : 0xFF3A4152);
            Ui.questsGui("info", infoBox.x() + 1, infoBox.y() + 1, 8, 8, 0xFFFFFFFF);
            hoverRows.add(new Object[] {infoBox, e});
            boolean live = e.enabled.get();
            fr.drawStringWithShadow(e.label, line.x() + 15, line.y() + 2, live ? 0xFFFFFF : 0x6E727C);
            String value = e.get.get();
            if (e.initial == null) {
                e.initial = value;
            }
            int color;
            String shown;
            if (e == editing) {
                shown = "";
                color = 0xFFFFFFFF;
                fieldRect = new Rect(valueX - 2, line.y(), line.x() + line.width() - 2 - (valueX - 2), ROW);
                RewardTablesScreen.drawField(fr, fieldRect, edit);
            } else if (e.kind == Kind.BOOL) {
                shown = value.equals("true") ? "True" : "False";
                color = value.equals("true") ? GREEN : RED;
            } else if (e.kind == Kind.CHOICE) {
                shown = value;
                color = value.equals("True") ? GREEN : value.equals("False") ? RED : BLUE;
            } else if (e.kind == Kind.NUMBER) {
                shown = e.integer ? value : formatDouble(value);
                color = PURPLE;
            } else {
                shown = e.plain ? value : "\"" + value + "\"";
                color = ORANGE;
            }
            if (e.format != null && e != editing) {
                shown = e.format.apply(value);
            }
            if (e.swatch && e != editing) {
                color = 0xFFAAAAAA;
            }
            if (!live) {
                color = (color >> 1 & 0x7F7F7F) | 0xFF000000;
            }
            fr.drawStringWithShadow(fr.trimStringToWidth(shown, list.x() + list.width() - valueX - (showDefault() ? 52 : 6)), valueX, line.y() + 2, color & 0xFFFFFF);
            if (e.swatch && e != editing) {
                int rgb = 0xFFFFFF;
                try {
                    rgb = Integer.parseInt(value.startsWith("#") ? value.substring(1) : value, 16) & 0xFFFFFF;
                } catch (NumberFormatException ex) {

                }
                int sx = valueX + fr.getStringWidth(shown) + 6;
                Gui.drawRect(sx, line.y() + 3, sx + 6, line.y() + 9, 0xFF000000 | rgb);
            }
            if (showDefault() && e != editing && !value.equals(e.initial)) {

                Rect back = new Rect(line.x() + line.width() - 46, line.y() + 1, 42, ROW - 1);
                Ui.box(back, back.contains(mouseX, mouseY) ? 0xFF55607A : 0xFF3A4152);
                fr.drawString("Default", back.x() + 21 - fr.getStringWidth("Default") / 2, back.y() + 2, 0xFFFFFF);
                final String restore = e.initial;
                hit.add(new Object[] {back, (Runnable) () -> change(e, restore)});
            }
            hit.add(new Object[] {line, (Runnable) () -> activate(e)});
        }
        unscissor();

        Rect accept = new Rect(win.x() + win.width() - 5 - 62 - 5 - 62, win.y() + win.height() - 24, 62, 20);
        Rect cancel = new Rect(win.x() + win.width() - 5 - 62, win.y() + win.height() - 24, 62, 20);
        for (Rect b : new Rect[] {accept, cancel}) {
            Gui.drawRect(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(), 0xFF000000);
            Gui.drawRect(b.x() + 1, b.y() + 1, b.x() + b.width() - 1, b.y() + b.height() - 1, 0xFF2F3645);
            Gui.drawRect(b.x() + 1, b.y() + 1, b.x() + b.width() - 2, b.y() + b.height() - 2, 0xFF4F5666);
            Gui.drawRect(b.x() + 2, b.y() + 2, b.x() + b.width() - 2, b.y() + b.height() - 2, 0xFF3B4252);
            if (b.contains(mouseX, mouseY)) {
                Gui.drawRect(b.x() + 1, b.y() + 1, b.x() + b.width() - 1, b.y() + 2, 0xFFFFFFFF);
                Gui.drawRect(b.x() + 1, b.y() + b.height() - 2, b.x() + b.width() - 1, b.y() + b.height() - 1, 0xFFFFFFFF);
                Gui.drawRect(b.x() + 1, b.y() + 1, b.x() + 2, b.y() + b.height() - 1, 0xFFFFFFFF);
                Gui.drawRect(b.x() + b.width() - 2, b.y() + 1, b.x() + b.width() - 1, b.y() + b.height() - 1, 0xFFFFFFFF);
            }
        }
        Ui.icon(Ui.LIBRARY, "textures/icons/accept.png", accept.x() + 4, accept.y() + 2, 16, 16, 0xFFFFFFFF);
        fr.drawStringWithShadow("Accept", accept.x() + 22, accept.y() + 8, 0xFFD8DEE9);
        Ui.icon(Ui.LIBRARY, "textures/icons/cancel.png", cancel.x() + 4, cancel.y() + 2, 16, 16, 0xFFFFFFFF);
        fr.drawStringWithShadow("Cancel", cancel.x() + 22, cancel.y() + 8, 0xFFD8DEE9);
        Rect undo = new Rect(win.x() + 8, win.y() + win.height() - 24, 54, 18);
        Rect redo = new Rect(win.x() + 66, win.y() + win.height() - 24, 54, 18);
        Ui.box(undo, !undoStack.isEmpty() && undo.contains(mouseX, mouseY) ? 0xFF3A4054 : 0xFF23262F);
        Ui.box(redo, !redoStack.isEmpty() && redo.contains(mouseX, mouseY) ? 0xFF3A4054 : 0xFF23262F);
        fr.drawStringWithShadow("Undo", undo.x() + 27 - fr.getStringWidth("Undo") / 2, undo.y() + 5, undoStack.isEmpty() ? 0x666677 : 0xFFFFFF);
        fr.drawStringWithShadow("Redo", redo.x() + 27 - fr.getStringWidth("Redo") / 2, redo.y() + 5, redoStack.isEmpty() ? 0x666677 : 0xFFFFFF);
        hit.add(new Object[] {undo, (Runnable) this::undo});
        hit.add(new Object[] {redo, (Runnable) this::redo});
        hit.add(new Object[] {accept, (Runnable) this::accept});
        hit.add(new Object[] {cancel, (Runnable) this::cancel});
        scrollTrack = null;
        if (maxScroll > 0) {

            Rect t = new Rect(list.x() + list.width() - 11, list.y(), 11, list.height());
            scrollTrack = t;
            scrollMax = maxScroll;
            scrollThumb = Math.max(7, Math.min(t.height() - 4, (t.height() - 4) * list.height() / Math.max(1, contentHeight)));
            Gui.drawRect(t.x(), t.y(), t.x() + t.width(), t.y() + t.height(), 0xFF101010);
            Gui.drawRect(t.x() + 1, t.y() + 1, t.x() + t.width() - 1, t.y() + t.height() - 1, 0xFF2E3440);
            Gui.drawRect(t.x() + 1, t.y() + 1, t.x() + t.width() - 1, t.y() + 2, 0xFF1C1F27);
            Gui.drawRect(t.x() + 1, t.y() + 1, t.x() + 2, t.y() + t.height() - 1, 0xFF1C1F27);
            int travel = t.height() - 4 - scrollThumb;
            int ty = t.y() + 2 + travel * scroll / maxScroll;
            int tx = t.x() + 2;
            Gui.drawRect(tx, ty, tx + 7, ty + scrollThumb, 0xFF313745);
            Gui.drawRect(tx, ty, tx + 7, ty + 1, 0xFF5E6B84);
            Gui.drawRect(tx, ty + 1, tx + 6, ty + scrollThumb - 1, 0xFF434C5E);
        }

        if (editing == null && list.contains(mouseX, mouseY)) {
            for (Object[] h : hoverRows) {
                Entry e = (Entry) h[1];
                if (((Rect) h[0]).contains(mouseX, mouseY) && (!e.tip.isEmpty() || !defaultOf(e).isEmpty() || e.kind == Kind.TEXT && !e.label.equals("Tags")
                        || e.info != null || e.valueLine || e.listOptions || !e.min.isEmpty())) {
                    List<String> lines = new ArrayList<>();
                    lines.add("§n" + e.label);
                    if (!e.tip.isEmpty()) {
                        for (String t : e.tip.split("\n")) {
                            lines.add("§7§o" + t);
                        }
                    }
                    lines.add("");
                    if (e.info != null) {
                        lines.add("§b" + e.info.get().replaceFirst(": ", ": §f"));
                    }
                    if (e.valueLine) {
                        lines.add("§bValue: §f\"" + e.get.get() + "\"");
                    }
                    String shownDefault = defaultOf(e);
                    boolean emptyTextDefault = e.kind == Kind.TEXT && !e.label.equals("Tags") && shownDefault.isEmpty();
                    if ((!shownDefault.isEmpty() || e.valueLine || emptyTextDefault) && e.info == null && !e.label.equals("Tags")) {
                        lines.add("§bDefault: §f" + (e.kind == Kind.TEXT && !e.plain ? "\"" + shownDefault + "\"" : shownDefault));
                    }
                    if (!e.min.isEmpty()) {
                        lines.add("§bMin: §f" + e.min);
                    }
                    if (!e.max.isEmpty()) {
                        lines.add("§bMax: §f" + e.max);
                    }
                    if (e.listOptions && e.kind == Kind.CHOICE) {
                        lines.add("");
                        for (String o : e.options) {
                            lines.add(o.equals(e.get.get()) ? "§b+ " + o : "§8- " + o);
                        }
                    }
                    while (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty()) {
                        lines.remove(lines.size() - 1);
                    }
                    Ui.drawTooltip(fr, lines, mouseX, mouseY, vw(), vh());
                    break;
                }
            }
        }
    }

    private static String formatDouble(String value) {
        try {
            double d = Double.parseDouble(value);
            if (d == Math.rint(d) && Math.abs(d) < 1e9) {
                return String.valueOf((long) d);
            }
            return String.format(java.util.Locale.getDefault(), "%.2f", d);
        } catch (NumberFormatException ex) {
            return value;
        }
    }

    private void scissor(Rect r) {
        double f = new ScaledResolution(mc).getScaleFactor() * uiScale();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (r.x() * f), (int) (mc.displayHeight - (r.y() + r.height()) * f), (int) Math.max(0, r.width() * f), (int) Math.max(0, r.height() * f));
    }

    private void unscissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static String defaultOf(Entry e) {
        if (!e.def.isEmpty() || e.label.equals("Tags")) {
            return e.def;
        }
        switch (e.kind) {
            case BOOL:
                return "False";
            case NUMBER:
                return "0";
            case CHOICE:
                return e.options.contains("Default") ? "Default" : e.options.contains("False") ? "False" : "";
            default:
                return "";
        }
    }

    private void activate(Entry e) {
        commitEdit();
        if (!e.enabled.get()) {
            return;
        }
        if (e.pick != null) {
            e.pick.accept(v -> change(e, v));
            return;
        }
        switch (e.kind) {
            case BOOL:
                change(e, String.valueOf(!Boolean.parseBoolean(e.get.get())));
                break;
            case CHOICE: {
                int i = e.options.indexOf(e.get.get());
                change(e, e.options.get((i + 1) % e.options.size()));
                break;
            }
            case NUMBER:
            case TEXT:
                editing = e;
                edit = new TextEditor(e.get.get(), 120);
                edit.selectAll();
                break;
            default:
                break;
        }
    }

    private void change(Entry e, String value) {
        String before = e.get.get();
        e.set.accept(value);
        String after = e.get.get();
        if (!after.equals(before)) {
            undoStack.push(new Change(e, before, after));
            redoStack.clear();
        }
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        Change c = undoStack.pop();
        c.entry().set.accept(c.before());
        redoStack.push(c);
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        Change c = redoStack.pop();
        c.entry().set.accept(c.after());
        undoStack.push(c);
    }

    private void commitEdit() {
        if (editing == null) {
            return;
        }
        try {
            change(editing, edit.text().trim());
        } catch (RuntimeException ignored) {

        }
        editing = null;
    }

    private void accept() {
        commitEdit();
        accepted();
        mc.displayGuiScreen(parent);
    }

    private void cancel() {
        cancelled();
        mc.displayGuiScreen(parent);
    }

    @Override
    protected void mouseClicked(int screenX, int screenY, int mouseButton) throws IOException {
        clicked((int) (screenX / uiScale()), (int) (screenY / uiScale()), mouseButton);
    }

    private void clicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) {
            return;
        }
        if (scrollTrack != null && scrollTrack.contains(mouseX, mouseY)) {
            commitEdit();
            draggingBar = true;
            dragBar(mouseY);
            return;
        }
        if (editing != null && fieldRect != null && fieldRect.contains(mouseX, mouseY)) {
            edit.setCaret(caretAt(mouseX), isShiftKeyDown());
            draggingField = true;
            return;
        }
        for (int i = hit.size() - 1; i >= 0; i--) {
            Rect r = (Rect) hit.get(i)[0];
            if (r.contains(mouseX, mouseY)) {
                ((Runnable) hit.get(i)[1]).run();
                return;
            }
        }
        commitEdit();
    }

    private boolean draggingField;

    private int caretAt(int mouseX) {
        String text = edit.text();
        int caret = Math.min(edit.caret(), text.length());
        int room = fieldRect.width() - 8;
        int first = 0;
        while (first < caret && fontRenderer.getStringWidth(text.substring(first, caret)) > room) {
            first++;
        }
        int x = fieldRect.x() + 3;
        int i = first;
        while (i < text.length() && x + fontRenderer.getStringWidth(text.substring(first, i + 1)) - fontRenderer.getCharWidth(text.charAt(i)) / 2 < mouseX) {
            i++;
        }
        return i;
    }

    @Override
    protected void mouseClickMove(int screenX, int screenY, int button, long timeSinceLastClick) {
        dragged((int) (screenX / uiScale()), (int) (screenY / uiScale()));
    }

    private void dragged(int mouseX, int mouseY) {
        if (draggingBar) {
            dragBar(mouseY);
            return;
        }
        if (draggingField && editing != null && fieldRect != null) {
            edit.setCaret(caretAt(mouseX), true);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingField = false;
        draggingBar = false;
    }

    @Override
    protected void keyTyped(char typed, int keyCode) throws IOException {
        if (editing != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                editing = null;
            } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                commitEdit();
            } else {
                RewardTablesScreen.typeInto(edit, typed, keyCode, false);
            }
            return;
        }
        if (keyCode == Keyboard.KEY_Z && GuiScreen.isCtrlKeyDown()) {
            undo();
        } else if (keyCode == Keyboard.KEY_Y && GuiScreen.isCtrlKeyDown()) {
            redo();
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            cancel();
        } else if (keyCode == Keyboard.KEY_RETURN) {
            accept();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll -= Integer.signum(wheel) * 18;
        }
    }
}
