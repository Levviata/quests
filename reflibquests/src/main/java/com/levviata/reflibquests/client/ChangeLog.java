/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.data.DataFiles;
import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.ReflibQuestsMod;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.QuestBook;
import com.levviata.reflibquests.text.RichText;

/**
 * The change history: one line for each edit, "time | Action Type 'name'", drawn over the bottom of the map. Lines are kept in
 * {@code config/reflibquests/changelog.json5}-style data so that the last ones are still there next time the book is opened;
 * lines from earlier sessions are drawn dimmer.
 */
@SideOnly(Side.CLIENT)
final class ChangeLog {

    private record Line(long at, String action, String type, String name, boolean session) {}

    /** The three parts of a line. */
    private record Desc(String action, String type, String name) {}

    private static final ArrayDeque<Line> LINES = new ArrayDeque<>();
    private static final int KEEP = 200;
    private static boolean loaded;
    private static long lastAdded;
    private static final SimpleDateFormat CLOCK = new SimpleDateFormat("HH:mm:ss");

    private ChangeLog() {}

    private static Path file() {
        return new File(Minecraft.getMinecraft().gameDir, "config/reflibquests/changelog.snbt").toPath();
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (file().toFile().isFile()) {
                for (DataNode n : DataFiles.readLenient(file()).getList("lines")) {
                    if (n instanceof DataNode.Compound c) {
                        LINES.addLast(new Line(c.getLong("t", 0), c.getString("a", ""), c.getString("y", ""), c.getString("n", ""), false));
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            ReflibQuestsMod.LOGGER.warn("Cannot read the change history: {}", ex.getMessage());
        }
    }

    private static void save() {
        try {
            List<DataNode> list = new ArrayList<>();
            for (Line l : LINES) {
                list.add(DataNode.Compound.empty().with("t", l.at()).with("a", l.action()).with("y", l.type()).with("n", l.name()));
            }
            DataFiles.write(file(), DataNode.Compound.empty().with("lines", new DataNode.Lst(list)));
        } catch (IOException ex) {
            ReflibQuestsMod.LOGGER.warn("Cannot save the change history: {}", ex.getMessage());
        }
    }

    static void add(String action, String type, String name) {
        load();
        long now = System.currentTimeMillis();
        Line last = LINES.peekLast();
        if (action.equals("Move") && last != null && last.session() && last.action().equals(action) && last.type().equals(type)
                && last.name().equals(name) && now - last.at() < 2000) {
            // repeated nudges (Ctrl + arrows) show as one line; every press is still its own undo step
            LINES.removeLast();
        }
        lastAdded = now;
        LINES.addLast(new Line(lastAdded, action, type, name, true));
        while (LINES.size() > KEEP) {
            LINES.removeFirst();
        }
        save();
    }

    static int size() {
        load();
        return LINES.size();
    }

    /** Drop the lines added after the log had {@code size} lines (edits that were cancelled). */
    static void truncate(int size) {
        load();
        while (LINES.size() > size) {
            LINES.removeLast();
        }
        save();
    }

    /** Add the line for an edit, worded from the book as it is before the edit is made. */
    static void addFor(String op, DataNode.Compound args) {
        Desc d = describe(op, args);
        if (d != null) {
            add(d.action(), d.type(), d.name());
        }
        if (!NOT_UNDOABLE.contains(op)) {
            UNDO_STACK.addLast(d != null ? d : new Desc("Edit", "", ""));
            while (UNDO_STACK.size() > 100) {
                UNDO_STACK.removeFirst();
            }
            REDO_STACK.clear(); // a new edit ends what could be redone
        }
    }

    // ------------------------------------------------------------------ what Undo and Redo would do

    /** Edits that are not steps of the undo history. */
    private static final java.util.Set<String> NOT_UNDOABLE = java.util.Set.of("reset_progress", "complete_quest", "save_now", "undo", "redo", "complete_task", "reset_task", "grab_item");
    private static final ArrayDeque<Desc> UNDO_STACK = new ArrayDeque<>();
    private static final ArrayDeque<Desc> REDO_STACK = new ArrayDeque<>();

    static void noteUndo() {
        if (!UNDO_STACK.isEmpty()) {
            REDO_STACK.addLast(UNDO_STACK.removeLast());
        }
    }

    static void noteRedo() {
        if (!REDO_STACK.isEmpty()) {
            UNDO_STACK.addLast(REDO_STACK.removeLast());
        }
    }

    static int undoSize() {
        return UNDO_STACK.size();
    }

    /** Forget the steps noted after the stack had {@code size} entries (edits that were cancelled). */
    static void truncateUndo(int size) {
        while (UNDO_STACK.size() > size) {
            UNDO_STACK.removeLast();
        }
    }

    static void clearHistoryNotes() {
        UNDO_STACK.clear();
        REDO_STACK.clear();
    }

    private static String typeCode(String type) {
        String kind = type.contains(" ") ? type.substring(0, type.indexOf(' ')) : type;
        switch (kind) {
            case "Task":
                return "\u00a79";
            case "Reward":
            case "Chapter":
                return "\u00a76";
            case "Image":
                return "\u00a7d";
            case "Progress":
                return "\u00a7c";
            default:
                return "\u00a7a";
        }
    }

    /**
     * The tooltip of the Undo or Redo button: the steps it would undo or redo, the newest first, and its key.
     * With no steps noted (the history is older than this session) the server's own wording is used.
     */
    static List<String> historyTip(boolean undo, String shortcut, String serverLabel) {
        List<String> out = new ArrayList<>();
        out.add("\u00a7f" + (undo ? "Undo" : "Redo"));
        ArrayDeque<Desc> stack = undo ? UNDO_STACK : REDO_STACK;
        int shown = 0;
        java.util.Iterator<Desc> it = stack.descendingIterator();
        while (it.hasNext() && shown++ < 8) {
            Desc d = it.next();
            String line = "\u00a77\u2022 " + d.action() + (d.type().isEmpty() ? "" : " " + typeCode(d.type()) + d.type() + "\u00a77:");
            out.add(d.name().isEmpty() ? line : line + " '" + d.name() + "'");
        }
        if (shown == 0 && !serverLabel.isEmpty()) {
            out.add("\u00a77\u2022 " + serverLabel);
        }
        out.add("\u00a78[" + shortcut + "]");
        return out;
    }

    // ------------------------------------------------------------------ drawing

    private static final int BRIGHT_TIME = 0xFFFF55;
    private static final int BRIGHT_TEXT = 0xFFFFFF;
    private static final int DIM_TIME = 0xB9B93A;
    private static final int DIM_TEXT = 0xA8A8A8;
    /** A line stays bright for this long after it was added; then it goes gray. */
    private static final long GRAY_AFTER_MS = 5000;

    /** The colour of the kind of thing a line is about (bright, or the dimmer form for gray lines). */
    private static int typeColor(String type, boolean bright) {
        int c;
        String kind = type.contains(" ") ? type.substring(0, type.indexOf(' ')) : type;
        switch (kind) {
            case "Task" -> c = 0x5555FF;
            case "Reward" -> c = 0xFF55FF;
            case "Chapter" -> c = 0x55FFFF;
            case "Image" -> c = 0x55FFFF;
            case "Progress" -> c = 0xFF5555;
            default -> c = 0x55FF55;
        }
        if (bright) {
            return c;
        }
        int r = (c >> 16 & 255) * 3 / 4;
        int g = (c >> 8 & 255) * 3 / 4;
        int b = (c & 255) * 3 / 4;
        return r << 16 | g << 8 | b;
    }

    /** The newest lines, as many as the preferences allow (at least ten), for a few seconds after each change. */
    private static List<Line> visible() {
        load();
        ClientPrefs.ensureLoaded();
        // the panel goes away a few seconds after the last change, unless the preferences keep it on screen
        if (!ClientPrefs.alwaysShowHistory && System.currentTimeMillis() - lastAdded >= (long) (ClientPrefs.historySeconds * 1000)) {
            return List.of();
        }
        int count = Math.max(10, ClientPrefs.maxLines);
        Line[] all = LINES.toArray(new Line[0]);
        List<Line> out = new ArrayList<>();
        for (int i = Math.max(0, all.length - count); i < all.length; i++) {
            out.add(all[i]);
        }
        return out;
    }

    /** Draws the band of lines so that its bottom edge is at {@code bottom}, from x to x + width. */
    static void draw(FontRenderer fr, int x, int bottom, int width) {
        List<Line> lines = visible();
        if (lines.isEmpty()) {
            return;
        }
        float scale = (float) Math.max(0.3, Math.min(2.0, ClientPrefs.changelogScale)) * 0.88f;
        int rowHeight = Math.max(6, Math.round(11 * scale));
        int height = lines.size() * rowHeight + 2;
        int widest = 0;
        for (Line l : lines) {
            String all = CLOCK.format(new Date(l.at())) + " | " + l.action() + " " + (l.type().isEmpty() ? "" : l.type() + (colon(l.type()) ? ": " : " "))
                    + (l.name().isEmpty() ? "" : "'" + l.name() + "'");
            widest = Math.max(widest, Math.round(fr.getStringWidth(all) * scale) + 8);
        }
        width = Math.min(width, widest); // as wide as its text
        Gui.drawRect(x, bottom - height, x + width, bottom, 0x6E000000);
        GlStateManager.enableBlend();
        int y = bottom - height + 2;
        long nowMs = System.currentTimeMillis();
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            // each line goes gray five seconds after it was added, whether or not the panel was on screen meanwhile
            boolean now = l.session() && nowMs - l.at() < GRAY_AFTER_MS;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 3, y, 0);
            GlStateManager.scale(scale, scale, 1f);
            int cx = 0;
            String time = CLOCK.format(new Date(l.at())) + " | ";
            fr.drawStringWithShadow(time, cx, 0, now ? BRIGHT_TIME : DIM_TIME);
            cx += fr.getStringWidth(time);
            fr.drawStringWithShadow(l.action() + " ", cx, 0, now ? BRIGHT_TEXT : DIM_TEXT);
            cx += fr.getStringWidth(l.action() + " ");
            if (!l.type().isEmpty()) {
                fr.drawStringWithShadow(l.type() + (colon(l.type()) ? ": " : " "), cx, 0, typeColor(l.type(), now));
                cx += fr.getStringWidth(l.type() + (colon(l.type()) ? ": " : " "));
            }
            if (!l.name().isEmpty()) {
                String name = "'" + l.name() + "'";
                fr.drawStringWithShadow(fr.trimStringToWidth(name, Math.max(20, (int) ((width - 6) / scale) - cx)), cx, 0, now ? BRIGHT_TEXT : DIM_TEXT);
            }
            GlStateManager.popMatrix();
            y += rowHeight;
        }
    }

    /** Tasks and rewards are written "Create Task: 'name'", the rest "Move Quest 'name'". */
    private static boolean colon(String type) {
        return type.equals("Task") || type.equals("Reward");
    }

    // ------------------------------------------------------------------ what an edit says

    private static String quest(QuestBook book, String id) {
        Quest q = book == null ? null : book.quest(id);
        return q == null ? "" : RichText.stripCodes(QuestText.title(q));
    }

    private static String chapter(QuestBook book, String id) {
        Chapter c = book == null ? null : book.chapter(id);
        return c == null ? "" : RichText.stripCodes(c.title.isEmpty() ? c.id : c.title);
    }

    /** Wording like upstream's history: an action, the kind of thing, and its name. Null for edits that are not recorded. */
    private static Desc describe(String op, DataNode.Compound a) {
        QuestBook book = ClientQuests.book();
        String id = a.getString("id", "");
        switch (op) {
            case "move_quest":
                return new Desc("Move", "Quest", quest(book, id));
            case "delete_quest":
                return new Desc("Delete", "Quest", quest(book, id));
            case "move_task":
                return new Desc("Move", "Task", "");
            case "move_reward":
                return new Desc("Move", "Reward", "");
            case "set_bezier":
                return new Desc("Modify", "Quest", quest(book, id));
            case "move_quests":
                return new Desc("Move", "Quest", a.getList("moves").size() + " quests");
            case "delete_quests":
                return new Desc("Delete", "Quest", a.getList("ids").size() + " quests");
            case "create_quest":
                return new Desc("Create", "Quest", "");
            case "set_title":
                return new Desc("Update Translation", "Quest quest title", RichText.stripCodes(a.getString("text", "")));
            case "set_subtitle":
                return new Desc("Update Translation", "Quest quest subtitle", RichText.stripCodes(a.getString("text", "")));
            case "set_text":
            case "add_lines":
                return new Desc("Update Translation", "Quest quest description", quest(book, id));
            case "set_prop":
            case "set_flag":
            case "set_shape":
            case "toggle_dep":
                return new Desc("Update", "Quest", quest(book, id));
            case "add_task": {
                String kind = a.getString("type", "");
                ItemStack held = Minecraft.getMinecraft().player == null ? ItemStack.EMPTY : Minecraft.getMinecraft().player.getHeldItemMainhand();
                String name = kind.equals("item") && !held.isEmpty() ? held.getDisplayName()
                        : kind.equals("checkmark") ? "Checkmark" : kind.equals("xp") ? "XP"
                        : kind.equals("gamestage") ? "Stage: myStage" : kind.equals("interaction") ? "Interaction: minecraft:air" : kind;
                return new Desc("Create", "Task", name);
            }
            case "remove_task":
                return new Desc("Delete", "Task", "");
            case "task_value":
            case "set_task_prop":
                return new Desc("Update", "Task", "");
            case "set_reward_prop":
                return new Desc("Update", "Reward", "");
            case "add_reward": {
                String kind = a.getString("type", "");
                ItemStack held = Minecraft.getMinecraft().player == null ? ItemStack.EMPTY : Minecraft.getMinecraft().player.getHeldItemMainhand();
                String name = kind.equals("item") && !held.isEmpty() ? held.getDisplayName()
                        : kind.equals("xp") ? "XP" : kind.equals("xp_levels") ? "XP Levels" : kind;
                return new Desc("Create", "Reward", name);
            }
            case "remove_reward":
                return new Desc("Delete", "Reward", "");
            case "create_chapter":
                return new Desc("Create", "Chapter", RichText.stripCodes(a.getString("title", "")));
            case "rename_chapter":
                return new Desc("Update Translation", "Chapter chapter title", RichText.stripCodes(a.getString("title", "")));
            case "set_chapter_prop":
                return new Desc("Update", "Chapter", chapter(book, id));
            case "delete_chapter":
                return new Desc("Delete", "Chapter", chapter(book, id));
            case "create_group":
                return new Desc("Create", "Chapter Group", RichText.stripCodes(a.getString("title", "")));
            case "rename_group":
                return new Desc("Update Translation", "Chapter Group title", RichText.stripCodes(a.getString("title", "")));
            case "delete_group":
                return new Desc("Delete", "Chapter Group", "");
            case "move_group":
                return new Desc("Move", "Chapter Group", "");
            case "set_chapter_group":
            case "move_chapter":
                return new Desc("Move", "Chapter", chapter(book, id));
            case "move_image":
                return new Desc("Move", "Image", "");
            case "delete_image":
                return new Desc("Delete", "Image", "");
            case "set_image_props":
                return new Desc("Modify", "Image", "");
            case "create_image":
                return new Desc("Create", "Image", "");
            case "create_link":
                return new Desc("Create", "Quest Link", "");
            case "delete_link":
                return new Desc("Delete", "Quest Link", "");
            case "create_table":
                return new Desc("Create", "Reward Table", RichText.stripCodes(a.getString("title", "")));
            case "rename_table":
                return new Desc("Update Translation", "Reward Table title", RichText.stripCodes(a.getString("title", "")));
            case "delete_table":
                return new Desc("Delete", "Reward Table", "");
            case "set_table_prop":
            case "add_table_entry":
            case "remove_table_entry":
            case "set_entry_weight":
                return new Desc("Update", "Reward Table", "");
            case "reset_progress":
                return new Desc("Reset", "Progress", quest(book, id));
            case "complete_quest":
                return new Desc("Complete", "Quest", quest(book, id));
            default:
                return null; // undo, redo and saving have their own lines or none
        }
    }
}
