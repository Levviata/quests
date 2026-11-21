/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.data.DataFiles;
import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.ReflibQuestsMod;
import com.levviata.reflibquests.model.BookEditor;
import com.levviata.reflibquests.model.QuestBook;
import com.levviata.reflibquests.net.QuestNet;

/**
 * Quest book edits are shown on screen at once but sent to the server in one go: when the questbook (and its windows) is
 * left, and every five minutes while it stays open. While they wait they are also kept in
 * {@code config/reflibquests/pending_table_edits.snbt}, so closing the game or a crash does not lose them; they are
 * sent the next time the player is in a world and can edit.
 *
 * <p>Edits that make up new ids on the server (a new quest, task, reward or chapter) cannot be shown ahead of time; those
 * first send everything waiting, to keep the order, and then go straight to the server.
 */
@SideOnly(Side.CLIENT)
final class TableEdits {

    private record Op(String op, DataNode.Compound args) {}

    private static final long AUTOSAVE_MS = 5 * 60_000L;
    /** Edits the client can carry out on its own copy of the book exactly as the server will. */
    private static final Set<String> LOCAL = Set.of(
            "create_table", "rename_table", "delete_table", "set_table_prop", "add_table_entry", "set_entry_weight", "remove_table_entry",
            "move_quest", "delete_quest", "move_quests", "delete_quests", "set_bezier", "delete_chapter", "rename_chapter", "set_chapter_prop", "set_text", "set_title", "set_subtitle",
            "set_prop", "set_flag", "set_shape", "toggle_dep", "task_value", "remove_task", "remove_reward", "move_image", "delete_image", "set_image_props", "delete_link",
            "set_task_prop", "set_reward_prop", "set_book_prop", "move_task", "move_reward",
            "create_group", "rename_group", "delete_group", "set_chapter_group", "move_chapter", "move_group", "set_group_chapter");

    private static final List<Op> PENDING = new ArrayList<>();
    private static boolean fileChecked;
    private static long lastFlush = System.currentTimeMillis();

    private TableEdits() {}

    private static Path file() {
        return new File(Minecraft.getMinecraft().gameDir, "config/reflibquests/pending_table_edits.snbt").toPath();
    }

    /** Make an edit: shown now where the client can do that itself, sent when the book is left; otherwise sent straight away. */
    static void send(String op, DataNode.Compound args) {
        if (LOCAL.contains(op)) {
            record(op, args);
        } else {
            ChangeLog.addFor(op, args);
            flush(); // keep the order: what was done before goes first
            QuestNet.edit(op, args);
        }
    }

    /** Apply the edit to the client's own copy of the book and remember it for sending. */
    static void record(String op, DataNode.Compound args) {
        ChangeLog.addFor(op, args); // worded from the book as it is before the edit
        QuestBook book = ClientQuests.book();
        if (book != null) {
            applyLocally(new BookEditor(book, new Random()), op, args);
        }
        enqueue(op, args);
    }

    /** Remember an edit whose effect on the screen was already shown by the caller. */
    static void queue(String op, DataNode.Compound args) {
        ChangeLog.addFor(op, args);
        enqueue(op, args);
    }

    private static void enqueue(String op, DataNode.Compound args) {
        if (PENDING.isEmpty()) {
            lastFlush = System.currentTimeMillis(); // the five minutes count from the first waiting edit
        }
        PENDING.add(new Op(op, args));
        persist();
    }

    private static List<String> lines(List<DataNode> list) {
        List<String> out = new ArrayList<>();
        for (DataNode n : list) {
            if (n instanceof DataNode.Str s) {
                out.add(s.value());
            }
        }
        return out;
    }

    static void applyLocally(BookEditor ed, String op, DataNode.Compound a) {
        switch (op) {
            case "create_table" -> ed.createTable(a.getString("title", ""), a.getString("id", ""));
            case "rename_table" -> ed.renameTable(a.getString("id", ""), a.getString("title", ""));
            case "delete_table" -> ed.deleteTable(a.getString("id", ""));
            case "set_table_prop" -> ed.setTableProp(a.getString("id", ""), a.getString("key", ""), a.getString("value", ""));
            case "set_entry_weight" -> ed.setEntryWeight(a.getString("id", ""), a.getString("entry", ""), (float) a.getDouble("weight", 0));
            case "remove_table_entry" -> ed.removeTableEntry(a.getString("id", ""), a.getString("entry", ""));
            case "add_table_entry" -> ed.addTableEntry(a.getString("id", ""), a.getString("type", ""), a.getCompound("params"),
                    (float) a.getDouble("weight", 1), a.getString("entry", ""));
            case "move_quest" -> ed.moveQuest(a.getString("id", ""), a.getDouble("x", Double.NaN), a.getDouble("y", Double.NaN));
            case "delete_quest" -> ed.deleteQuest(a.getString("id", ""));
            case "move_quests" -> ed.moveQuests(a);
            case "set_bezier" -> ed.setBezier(a.getString("id", ""), a.getCompound("points"));
            case "delete_quests" -> ed.deleteQuests(a);
            case "delete_chapter" -> ed.deleteChapter(a.getString("id", ""));
            case "rename_chapter" -> ed.renameChapter(a.getString("id", ""), a.getString("title", ""));
            case "set_chapter_prop" -> ed.setChapterProp(a.getString("id", ""), a.getString("key", ""), a.getString("value", ""));
            case "set_text" -> ed.setText(a.getString("id", ""), a.getString("title", ""), a.getString("subtitle", ""), lines(a.getList("desc")));
            case "set_title" -> ed.setTitleOnly(a.getString("id", ""), a.getString("text", ""));
            case "set_subtitle" -> ed.setSubtitleOnly(a.getString("id", ""), a.getString("text", ""));
            case "set_prop" -> ed.setProp(a.getString("id", ""), a.getString("key", ""), a.getString("value", ""));
            case "set_flag" -> ed.setFlag(a.getString("id", ""), a.getString("flag", ""), a.getBool("value", false));
            case "set_shape" -> ed.setShape(a.getString("id", ""), a.getString("shape", ""));
            case "toggle_dep" -> ed.toggleDependency(a.getString("id", ""), a.getString("dep", ""));
            case "task_value" -> ed.setTaskValue(a.getString("id", ""), a.getLong("value", 0));
            case "remove_task" -> ed.removeTask(a.getString("id", ""));
            case "remove_reward" -> ed.removeReward(a.getString("id", ""));
            case "move_image" -> ed.moveImage(a.getString("id", ""), a.getDouble("x", 0), a.getDouble("y", 0));
            case "delete_image" -> ed.deleteImage(a.getString("id", ""));
            case "set_image_props" -> ed.setImageProps(a.getString("id", ""), a.getCompound("props"));
            case "delete_link" -> ed.deleteLink(a.getString("id", ""));
            case "create_group" -> ed.createGroup(a.getString("title", ""), a.getString("id", ""));
            case "rename_group" -> ed.renameGroup(a.getString("id", ""), a.getString("title", ""));
            case "delete_group" -> ed.deleteGroup(a.getString("id", ""));
            case "set_chapter_group" -> ed.setChapterGroup(a.getString("id", ""), a.getString("group", ""));
            case "move_chapter" -> ed.moveChapter(a.getString("id", ""), a.getInt("by", 0));
            case "move_group" -> ed.moveGroup(a.getString("id", ""), a.getInt("by", 0));
            case "set_group_chapter" -> ed.setGroupChapter(a.getString("id", ""), a.getString("chapter", ""));
            case "set_task_prop" -> ed.setTaskProp(a.getString("id", ""), a.getString("key", ""), a.getString("value", ""));
            case "move_task" -> ed.moveTask(a.getString("id", ""), a.getInt("by", 0));
            case "move_reward" -> ed.moveReward(a.getString("id", ""), a.getInt("by", 0));
            case "set_book_prop" -> ed.setBookProp(a.getString("key", ""), a.getString("value", ""));
            case "set_reward_prop" -> ed.setRewardProp(a.getString("id", ""), a.getString("key", ""), a.getString("value", ""));
            default -> { }
        }
    }

    /** What a window that can be cancelled needs to put the book back: the waiting edits, the log and the reward tables. */
    static final class Mark {
        private final int pending = PENDING.size();
        private final int log = ChangeLog.size();
        private final int undoNotes = ChangeLog.undoSize();
        private final java.util.Map<String, com.levviata.reflibquests.model.Definitions.RewardTable> tables = new java.util.LinkedHashMap<>();
        private boolean open = true;

        private Mark() {
            QuestBook book = ClientQuests.book();
            if (book != null) {
                book.tables().forEach((id, t) -> tables.put(id, com.levviata.reflibquests.model.DeepCopy.table(t)));
            }
            holds++;
        }
    }

    private static int holds;

    /** Start a window whose edits only count on Accept. Nothing is sent to the server until {@link #accept} or {@link #rollback}. */
    static Mark mark() {
        return new Mark();
    }

    static void accept(Mark m) {
        if (m.open) {
            m.open = false;
            holds = Math.max(0, holds - 1);
        }
    }

    /** Cancel: put the reward tables back and forget the edits made since the mark. */
    static void rollback(Mark m) {
        if (!m.open) {
            return;
        }
        m.open = false;
        holds = Math.max(0, holds - 1);
        QuestBook book = ClientQuests.book();
        if (book != null) {
            book.tables().clear();
            book.tables().putAll(m.tables);
        }
        while (PENDING.size() > m.pending) {
            PENDING.remove(PENDING.size() - 1);
        }
        ChangeLog.truncate(m.log);
        ChangeLog.truncateUndo(m.undoNotes);
        if (PENDING.isEmpty()) {
            try {
                Files.deleteIfExists(file());
            } catch (IOException ex) {
                ReflibQuestsMod.LOGGER.warn("Cannot remove the pending quest edits: {}", ex.getMessage());
            }
        } else {
            persist();
        }
    }

    static boolean hasPending() {
        return !PENDING.isEmpty();
    }

    /** True while the player is in the questbook or any window opened from it. */
    private static boolean inQuestUi() {
        Object s = Minecraft.getMinecraft().currentScreen;
        return s != null && s.getClass().getName().startsWith("com.levviata.reflibquests.client.");
    }

    /** Send everything that is waiting, in order. */
    static void flush() {
        if (PENDING.isEmpty() || !ClientQuests.ready() || !ClientQuests.canEdit()) {
            return;
        }
        for (Op o : PENDING) {
            QuestNet.edit(o.op(), o.args());
        }
        PENDING.clear();
        lastFlush = System.currentTimeMillis();
        try {
            Files.deleteIfExists(file());
        } catch (IOException ex) {
            ReflibQuestsMod.LOGGER.warn("Cannot remove the pending quest edits: {}", ex.getMessage());
        }
    }

    /** Called every client tick: sends what is waiting once the questbook is left, or when five minutes have passed. */
    static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!fileChecked && mc.world != null && ClientQuests.ready()) {
            fileChecked = true;
            loadFile();
        }
        if (mc.world == null) {
            fileChecked = false; // look at the file again in the next world
            return;
        }
        if (PENDING.isEmpty()) {
            return;
        }
        if (holds > 0) {
            return; // a window with Accept / Cancel is open
        }
        if (!inQuestUi() || System.currentTimeMillis() - lastFlush >= AUTOSAVE_MS) {
            flush();
        }
    }

    private static void persist() {
        try {
            List<DataNode> list = new ArrayList<>();
            for (Op o : PENDING) {
                list.add(DataNode.Compound.empty().with("op", o.op()).with("args", o.args()));
            }
            DataFiles.write(file(), DataNode.Compound.empty().with("ops", new DataNode.Lst(list)));
        } catch (IOException ex) {
            ReflibQuestsMod.LOGGER.warn("Cannot keep the pending quest edits: {}", ex.getMessage());
        }
    }

    private static void loadFile() {
        try {
            if (!file().toFile().isFile()) {
                return;
            }
            DataNode.Compound c = DataFiles.readLenient(file());
            List<Op> found = new ArrayList<>();
            for (DataNode n : c.getList("ops")) {
                if (n instanceof DataNode.Compound o) {
                    found.add(new Op(o.getString("op", ""), o.getCompound("args")));
                }
            }
            found.addAll(PENDING); // anything made since then comes after
            PENDING.clear();
            PENDING.addAll(found);
            ReflibQuestsMod.LOGGER.info("Found {} quest edit(s) that were not sent last time; sending them now.", found.size());
        } catch (IOException | RuntimeException ex) {
            ReflibQuestsMod.LOGGER.warn("Cannot read the pending quest edits: {}", ex.getMessage());
        }
    }
}
