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
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.levviata.reflib.gui.Rect;

/**
 * A window with grouped settings, coloured values, hover tooltips, and Accept / Cancel. Used by the player
 * preferences and the quest properties; subclasses add entries and say what Accept and Cancel do.
 */
@SideOnly(Side.CLIENT)
abstract class SettingsScreen extends GuiScreen {

    private static final int ROW = 12;
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
        boolean open = true; // sections only
        String tip = "";
        String def = "";
        String initial; // the value when the window was opened
        /** False greys the row out and stops it being edited. */
        Supplier<Boolean> enabled = () -> true;
        /** Shown in the tooltip under the tip (FTB: "Value: ..." for texts, "Min:", "Max:"). */
        boolean valueLine;
        String min = "";
        String max = "";
        /** A line such as "ID: null" for the tooltip. */
        Supplier<String> info;
        /** Lists the options in the tooltip, the chosen one marked. */
        boolean listOptions;
        /** Texts shown without quotes. */
        boolean plain;
        /** How the value is displayed (for example a list as "[ 1 item ]"). */
        java.util.function.Function<String, String> format;
        /** A colour swatch after the value (the value is #RRGGBB). */
        boolean swatch;

        Entry(Kind kind, String label) {
            this.kind = kind;
            this.label = label;
        }

        /** Text shown when the row is hovered. */
        Entry tip(String text) {
            this.tip = text;
            return this;
        }

        /** The default value, shown under the tip. */
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
    private Entry editing;
    private TextEditor edit = new TextEditor("", 120);
    private Rect fieldRect;
    private final List<Object[]> hit = new ArrayList<>(); // {Rect, Runnable}
    private final List<Object[]> hoverRows = new ArrayList<>(); // {info box Rect, Entry}
    private record Change(Entry entry, String before, String after) {}
    private final java.util.ArrayDeque<Change> undoStack = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Change> redoStack = new java.util.ArrayDeque<>();

    SettingsScreen(GuiScreen parent, String title) {
        this.parent = parent;
        this.title = title;
    }

    /** Only the general player settings offer a Default button; the change history covers everything else. */
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

    private Rect window() {
        int w = Math.min(420, width - 20);
        int h = Math.min(height - 20, 360);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
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
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        hit.clear();
        hoverRows.clear();
        drawDefaultBackground();
        FontRenderer fr = fontRenderer;
        Rect win = window();
        Ui.box(win, 0xFF2D3140);
        fr.drawStringWithShadow("§n" + title, win.x() + 8, win.y() + 8, 0xFFFFFF);
        Rect collapse = new Rect(win.x() + win.width() - 34, win.y() + 6, 12, 12);
        Rect expand = new Rect(win.x() + win.width() - 18, win.y() + 6, 12, 12);
        fr.drawStringWithShadow("-", collapse.x() + 4, collapse.y() + 2, 0x66CCFF);
        fr.drawStringWithShadow("+", expand.x() + 4, expand.y() + 2, 0x66CCFF);
        hit.add(new Object[] {collapse, (Runnable) () -> entries.stream().filter(e -> e.kind == Kind.SECTION).forEach(e -> e.open = false)});
        hit.add(new Object[] {expand, (Runnable) () -> entries.stream().filter(e -> e.kind == Kind.SECTION).forEach(e -> e.open = true)});
        Rect list = new Rect(win.x() + 4, win.y() + 24, win.width() - 8, win.height() - 24 - 30);
        Gui.drawRect(list.x(), list.y(), list.x() + list.width(), list.y() + list.height(), 0xFF2B2F3C);
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
        int valueX = list.x() + (int) (list.width() * 0.62);
        for (Object[] row : rows) {
            Entry e = (Entry) row[0];
            int y = list.y() + (Integer) row[1] - scroll;
            if (y + ROW < list.y() || y > list.y() + list.height()) {
                continue;
            }
            Rect line = new Rect(list.x(), y, list.width(), e.kind == Kind.SECTION ? ROW + 1 : ROW);
            if (e.kind == Kind.SECTION) {
                Gui.drawRect(line.x(), line.y(), line.x() + line.width(), line.y() + line.height(), 0xFF454B5D);
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
                color = (color >> 1 & 0x7F7F7F) | 0xFF000000; // dimmed
            }
            fr.drawStringWithShadow(fr.trimStringToWidth(shown, list.x() + list.width() - valueX - 52), valueX, line.y() + 2, color & 0xFFFFFF);
            if (e.swatch && e != editing) {
                int rgb = 0xFFFFFF;
                try {
                    rgb = Integer.parseInt(value.startsWith("#") ? value.substring(1) : value, 16) & 0xFFFFFF;
                } catch (NumberFormatException ex) {
                    // shown white
                }
                int sx = valueX + fr.getStringWidth(shown) + 6;
                Gui.drawRect(sx, line.y() + 3, sx + 6, line.y() + 9, 0xFF000000 | rgb);
            }
            if (showDefault() && e != editing && !value.equals(e.initial)) {
                // "Default": back to what it was when this window opened
                Rect back = new Rect(line.x() + line.width() - 46, line.y() + 1, 42, ROW - 1);
                Ui.box(back, back.contains(mouseX, mouseY) ? 0xFF55607A : 0xFF3A4152);
                fr.drawString("Default", back.x() + 21 - fr.getStringWidth("Default") / 2, back.y() + 2, 0xFFFFFF);
                final String restore = e.initial;
                hit.add(new Object[] {back, (Runnable) () -> change(e, restore)});
            }
            hit.add(new Object[] {line, (Runnable) () -> activate(e)});
        }
        unscissor();
        Rect accept = new Rect(win.x() + win.width() - 150, win.y() + win.height() - 24, 70, 18);
        Rect cancel = new Rect(win.x() + win.width() - 74, win.y() + win.height() - 24, 70, 18);
        Ui.box(accept, accept.contains(mouseX, mouseY) ? 0xFF3A4054 : 0xFF23262F);
        Ui.box(cancel, cancel.contains(mouseX, mouseY) ? 0xFF3A4054 : 0xFF23262F);
        Ui.icon(Ui.LIBRARY, "textures/icons/accept.png", accept.x() + 4, accept.y() + 1, 16, 16, 0xFFFFFFFF);
        fr.drawStringWithShadow("Accept", accept.x() + 24, accept.y() + 5, 0xFFFFFF);
        Ui.icon(Ui.LIBRARY, "textures/icons/cancel.png", cancel.x() + 4, cancel.y() + 1, 16, 16, 0xFFFFFFFF);
        fr.drawStringWithShadow("Cancel", cancel.x() + 24, cancel.y() + 5, 0xFFFFFF);
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
        if (maxScroll > 0) {
            int knob = Math.max(12, list.height() * list.height() / contentHeight);
            int ky = list.y() + (list.height() - knob) * scroll / maxScroll;
            Gui.drawRect(list.x() + list.width() - 3, ky, list.x() + list.width(), ky + knob, 0xFF808080);
        }
        // hover tooltip: the setting's name, what it does, and its default
        if (editing == null && list.contains(mouseX, mouseY)) {
            for (Object[] h : hoverRows) {
                Entry e = (Entry) h[1];
                if (((Rect) h[0]).contains(mouseX, mouseY) && (!e.tip.isEmpty() || !e.def.isEmpty() || e.info != null || e.valueLine || e.listOptions || !e.min.isEmpty())) {
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
                    if ((!e.def.isEmpty() || e.valueLine) && e.info == null) {
                        lines.add("§bDefault: §f" + (e.kind == Kind.TEXT && !e.plain ? "\"" + e.def + "\"" : e.def));
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
                    ScaledResolution res = new ScaledResolution(mc);
                    Ui.drawTooltip(fr, lines, mouseX, mouseY, res.getScaledWidth(), res.getScaledHeight());
                    break;
                }
            }
        }
    }

    private static String formatDouble(String value) {
        try {
            double d = Double.parseDouble(value);
            if (d == Math.rint(d) && Math.abs(d) < 1e9) {
                return String.valueOf((long) d); // whole numbers show without decimals
            }
            return String.format(java.util.Locale.getDefault(), "%.2f", d);
        } catch (NumberFormatException ex) {
            return value;
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

    private void activate(Entry e) {
        commitEdit();
        if (!e.enabled.get()) {
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

    /** Applies a new value and remembers the old one for Undo. */
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
            // not a number: keep the old value
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
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) {
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

    /** The character position under an x coordinate in the open field. */
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
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (draggingField && editing != null && fieldRect != null) {
            edit.setCaret(caretAt(mouseX), true);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingField = false;
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
