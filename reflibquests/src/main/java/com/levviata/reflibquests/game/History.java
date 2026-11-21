/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.ChapterGroup;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.RewardTable;
import com.levviata.reflibquests.model.DeepCopy;
import com.levviata.reflibquests.model.QuestBook;

/**
 * The edit history: one entry per edit, each holding copies of what that edit could change (the chapters involved, or a
 * reward table) as they were before it. Undoing swaps those copies back in; redoing swaps the copies taken at undo time.
 * Server side only, and only for the quest book (player progress is not part of it).
 */
final class History {

    static final int MAX = 300;

    /** Edits after which the order of the chapters or the list of groups must be put back as well. */
    private static final Set<String> LAYOUT_OPS = Set.of("create_chapter", "delete_chapter", "create_group", "rename_group", "delete_group",
            "set_chapter_group", "move_chapter", "move_group");

    /** A chapter as it was (null copy: it did not exist), and where in the book's list it stood. */
    record ChapterSnap(String id, int index, @Nullable Chapter copy) {}

    record TableSnap(String id, @Nullable RewardTable copy) {}

    /** The order of the chapters and the list of chapter groups, for edits that change those (null: untouched). */
    record Layout(List<String> chapterOrder, List<ChapterGroup> groups) {}

    record Entry(String label, long time, List<ChapterSnap> chapters, List<TableSnap> tables, @Nullable Layout layout) {}

    /** What was copied before an edit, until the edit is known to have worked. */
    static final class Open {
        final String op;
        final Set<String> chapterIds = new LinkedHashSet<>();
        final Set<String> tableIds = new LinkedHashSet<>();
        final Set<String> knownChapters = new LinkedHashSet<>();
        final Set<String> knownTables = new LinkedHashSet<>();
        final List<ChapterSnap> chapters = new ArrayList<>();
        final List<TableSnap> tables = new ArrayList<>();
        @Nullable Layout layout;
        String label = "";

        Open(String op) {
            this.op = op;
        }
    }

    private final ArrayDeque<Entry> undo = new ArrayDeque<>();
    private final ArrayDeque<Entry> redo = new ArrayDeque<>();

    int undoCount() {
        return undo.size();
    }

    int redoCount() {
        return redo.size();
    }

    String undoLabel() {
        return undo.isEmpty() ? "" : undo.peekLast().label();
    }

    String redoLabel() {
        return redo.isEmpty() ? "" : redo.peekLast().label();
    }

    void clear() {
        undo.clear();
        redo.clear();
    }

    // ------------------------------------------------------------------ before an edit

    /** Copy what the edit may change. Ops that do not change the book return null. */
    @Nullable
    static Open begin(QuestBook book, String op, DataNode.Compound a) {
        if (op.equals("save_now") || op.equals("reset_progress") || op.equals("complete_quest") || op.equals("undo") || op.equals("redo")) {
            return null;
        }
        Open o = new Open(op);
        String id = a.getString("id", "");
        switch (op) {
            case "create_chapter", "create_table", "create_group", "rename_group", "move_chapter", "move_group" -> { }
            case "set_chapter_group" -> o.chapterIds.add(id);
            case "delete_group" -> {
                for (Chapter c : book.chapters()) {
                    if (c.groupId.equals(id)) {
                        o.chapterIds.add(c.id);
                    }
                }
            }
            case "create_quest" -> o.chapterIds.add(a.getString("chapter", ""));
            case "rename_chapter", "set_chapter_prop" -> o.chapterIds.add(id);
            case "delete_chapter" -> {
                o.chapterIds.add(id);
                Chapter c = book.chapter(id);
                if (c != null) {
                    Set<String> gone = new LinkedHashSet<>();
                    c.quests.forEach(q -> gone.add(q.id));
                    addDependantChapters(book, gone, o);
                }
            }
            case "delete_quest" -> {
                Chapter c = book.chapterOf(id);
                if (c != null) {
                    o.chapterIds.add(c.id);
                }
                addDependantChapters(book, Set.of(id), o);
            }
            case "task_value", "remove_task", "set_task_prop" -> {
                Quest q = book.questOfTask(id);
                Chapter c = q == null ? null : book.chapterOf(q.id);
                if (c != null) {
                    o.chapterIds.add(c.id);
                }
            }
            case "remove_reward", "set_reward_prop" -> {
                for (Chapter c : book.chapters()) {
                    for (Quest q : c.quests) {
                        for (Reward r : q.rewards) {
                            if (r.id().equals(id)) {
                                o.chapterIds.add(c.id);
                            }
                        }
                    }
                }
            }
            case "move_image", "delete_image" -> {
                for (Chapter c : book.chapters()) {
                    for (var img : c.images) {
                        if (img.id().equals(id)) {
                            o.chapterIds.add(c.id);
                        }
                    }
                }
            }
            case "rename_table", "delete_table", "set_table_prop", "add_table_entry", "set_entry_weight", "remove_table_entry" -> o.tableIds.add(id);
            default -> {
                Chapter c = book.chapterOf(id); // every other edit names a quest
                if (c != null) {
                    o.chapterIds.add(c.id);
                }
            }
        }
        for (Chapter c : book.chapters()) {
            o.knownChapters.add(c.id);
        }
        o.knownTables.addAll(book.tables().keySet());
        for (String cid : o.chapterIds) {
            Chapter c = book.chapter(cid);
            if (c != null) {
                o.chapters.add(new ChapterSnap(cid, book.chapters().indexOf(c), DeepCopy.chapter(c)));
            }
        }
        for (String tid : o.tableIds) {
            RewardTable t = book.tables().get(tid);
            if (t != null) {
                o.tables.add(new TableSnap(tid, DeepCopy.table(t)));
            }
        }
        if (LAYOUT_OPS.contains(op)) {
            List<String> order = new ArrayList<>();
            book.chapters().forEach(c -> order.add(c.id));
            List<ChapterGroup> groups = new ArrayList<>();
            book.groups().forEach(g -> groups.add(DeepCopy.group(g)));
            o.layout = new Layout(order, groups);
        }
        o.label = label(book, op, a);
        return o;
    }

    private static void addDependantChapters(QuestBook book, Set<String> gone, Open o) {
        for (Chapter c : book.chapters()) {
            boolean touched = false;
            for (Quest q : c.quests) {
                for (String dep : q.dependencies) {
                    if (gone.contains(dep)) {
                        touched = true;
                    }
                }
            }
            for (var link : c.links) {
                if (gone.contains(link.linkedQuest())) {
                    touched = true;
                }
            }
            if (touched) {
                o.chapterIds.add(c.id);
            }
        }
    }

    /** The edit worked: keep what was copied, plus a "did not exist" note for anything it created. */
    void commit(QuestBook book, Open o) {
        for (int i = 0; i < book.chapters().size(); i++) {
            Chapter c = book.chapters().get(i);
            if (!o.knownChapters.contains(c.id)) {
                o.chapters.add(new ChapterSnap(c.id, i, null));
            }
        }
        for (String tid : book.tables().keySet()) {
            if (!o.knownTables.contains(tid)) {
                o.tables.add(new TableSnap(tid, null));
            }
        }
        if (o.chapters.isEmpty() && o.tables.isEmpty() && o.layout == null) {
            return;
        }
        undo.addLast(new Entry(o.label, System.currentTimeMillis(), o.chapters, o.tables, o.layout));
        while (undo.size() > MAX) {
            undo.removeFirst();
        }
        redo.clear();
    }

    // ------------------------------------------------------------------ undo and redo

    /** @return the label of what was undone, or null if there is nothing to undo */
    @Nullable
    String undo(QuestBook book) {
        return step(book, undo, redo);
    }

    @Nullable
    String redo(QuestBook book) {
        return step(book, redo, undo);
    }

    /** The chapters and tables that the last undo or redo put back, so only those need to be sent on. */
    final Set<String> lastChapterIds = new LinkedHashSet<>();
    final Set<String> lastTableIds = new LinkedHashSet<>();
    /** True when the last undo or redo also moved chapters or changed groups, so the whole book has to be sent again. */
    boolean lastNeedsFullSync;

    @Nullable
    private String step(QuestBook book, ArrayDeque<Entry> from, ArrayDeque<Entry> to) {
        Entry e = from.pollLast();
        if (e == null) {
            return null;
        }
        lastChapterIds.clear();
        lastTableIds.clear();
        lastNeedsFullSync = e.layout() != null;
        e.chapters().forEach(s -> lastChapterIds.add(s.id()));
        e.tables().forEach(s -> lastTableIds.add(s.id()));
        // what is there now is what the opposite step has to put back
        List<ChapterSnap> now = new ArrayList<>();
        for (ChapterSnap s : e.chapters()) {
            Chapter c = book.chapter(s.id());
            now.add(new ChapterSnap(s.id(), c == null ? s.index() : book.chapters().indexOf(c), c == null ? null : DeepCopy.chapter(c)));
        }
        List<TableSnap> nowTables = new ArrayList<>();
        for (TableSnap s : e.tables()) {
            RewardTable t = book.tables().get(s.id());
            nowTables.add(new TableSnap(s.id(), t == null ? null : DeepCopy.table(t)));
        }
        for (ChapterSnap s : e.chapters()) {
            Chapter existing = book.chapter(s.id());
            int at = existing == null ? -1 : book.chapters().indexOf(existing);
            if (s.copy() == null) {
                if (existing != null) {
                    book.chapters().remove(at);
                }
            } else if (existing != null) {
                book.chapters().set(at, s.copy());
            } else {
                book.chapters().add(Math.min(Math.max(0, s.index()), book.chapters().size()), s.copy());
            }
        }
        for (TableSnap s : e.tables()) {
            if (s.copy() == null) {
                book.tables().remove(s.id());
            } else {
                book.tables().put(s.id(), s.copy());
            }
        }
        Layout nowLayout = null;
        if (e.layout() != null) {
            List<String> order = new ArrayList<>();
            book.chapters().forEach(c -> order.add(c.id));
            List<ChapterGroup> groups = new ArrayList<>();
            book.groups().forEach(g -> groups.add(DeepCopy.group(g)));
            nowLayout = new Layout(order, groups);
            // put the saved groups back, and the chapters in the saved order (chapters it does not know stay at the end)
            book.groups().clear();
            book.groups().addAll(e.layout().groups());
            List<String> saved = e.layout().chapterOrder();
            book.chapters().sort(java.util.Comparator.comparingInt(c -> {
                int at = saved.indexOf(c.id);
                return at < 0 ? Integer.MAX_VALUE : at;
            }));
            for (int i = 0; i < book.chapters().size(); i++) {
                book.chapters().get(i).order = i;
            }
        }
        to.addLast(new Entry(e.label(), System.currentTimeMillis(), now, nowTables, nowLayout));
        return e.label();
    }

    // ------------------------------------------------------------------ wording

    private static String quoted(QuestBook book, String questId) {
        Quest q = book.quest(questId);
        String t = q == null ? "" : q.title.replaceAll("&[0-9a-fk-orz]", "").replaceAll("§.", "");
        return t.isEmpty() ? "a quest" : "\"" + t + "\"";
    }

    private static String label(QuestBook book, String op, DataNode.Compound a) {
        String id = a.getString("id", "");
        switch (op) {
            case "move_quest":
                return "Moved " + quoted(book, id);
            case "delete_quest":
                return "Deleted " + quoted(book, id);
            case "create_quest":
                return "Created a quest";
            case "create_chapter":
                return "Created a chapter";
            case "create_group":
            case "rename_group":
            case "delete_group":
            case "move_group":
                return "Changed a chapter group";
            case "set_chapter_group":
            case "move_chapter":
                return "Moved a chapter";
            case "delete_chapter":
                return "Deleted a chapter";
            case "rename_chapter":
            case "set_chapter_prop":
                return "Changed a chapter";
            case "create_table":
            case "rename_table":
            case "delete_table":
            case "set_table_prop":
            case "add_table_entry":
            case "set_entry_weight":
            case "remove_table_entry":
                return "Changed a reward table";
            case "move_image":
            case "delete_image":
                return "Changed a picture";
            default:
                return "Changed " + quoted(book, id.isEmpty() ? "" : id);
        }
    }

    static String pretty(String op) {
        String s = op.replace('_', ' ');
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
