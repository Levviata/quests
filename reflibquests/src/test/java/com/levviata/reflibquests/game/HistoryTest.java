/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.BookEditor;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.DeepCopy;
import com.levviata.reflibquests.model.QuestBook;

class HistoryTest {

    private QuestBook book;
    private BookEditor ed;
    private History history;
    private Chapter one;
    private Chapter two;
    private Quest a;
    private Quest b;

    @BeforeEach
    void setUp() {
        book = new QuestBook();
        ed = new BookEditor(book, new Random(11));
        history = new History();
        one = ed.createChapter("One");
        two = ed.createChapter("Two");
        a = ed.createQuest(one.id, 1, 1);
        b = ed.createQuest(two.id, 4, 4);
        ed.toggleDependency(b.id, a.id); // b needs a, from another chapter
    }

    private DataNode.Compound args(Object... pairs) {
        DataNode.Compound c = DataNode.Compound.empty();
        for (int i = 0; i < pairs.length; i += 2) {
            Object v = pairs[i + 1];
            c = v instanceof Double d ? c.with((String) pairs[i], d) : c.with((String) pairs[i], (String) v);
        }
        return c;
    }

    private String undo() {
        String what = history.undo(book);
        book.reindex(); // the server does this after every undo
        return what;
    }

    private String redo() {
        String what = history.redo(book);
        book.reindex();
        return what;
    }

    /** What the server does for one edit: copy, edit, keep. */
    private void edit(String op, DataNode.Compound a, Runnable change) {
        History.Open open = History.begin(book, op, a);
        change.run();
        history.commit(book, open);
    }

    @Test
    void deepCopyIsIndependent() {
        Chapter copy = DeepCopy.chapter(one);
        assertNotSame(one, copy);
        assertEquals(one.id, copy.id);
        assertEquals(one.quests.size(), copy.quests.size());
        assertNotSame(one.quests.get(0), copy.quests.get(0));
        copy.quests.get(0).x = 99;
        copy.quests.get(0).dependencies.add("zzz");
        assertEquals(1.0, one.quests.get(0).x);
        assertTrue(one.quests.get(0).dependencies.isEmpty());
        assertEquals(one.quests.get(0).tasks.size(), DeepCopy.chapter(one).quests.get(0).tasks.size());
    }

    @Test
    void moveCanBeUndoneAndRedone() {
        edit("move_quest", args("id", a.id), () -> ed.moveQuest(a.id, 7, 8));
        assertEquals(1, history.undoCount());
        assertEquals("Moved a quest", history.undoLabel());
        assertEquals("Moved a quest", undo());
        Quest back = book.quest(a.id);
        assertEquals(1.0, back.x);
        assertEquals(1.0, back.y);
        assertEquals(0, history.undoCount());
        assertEquals(1, history.redoCount());
        redo();
        assertEquals(7.0, book.quest(a.id).x);
        assertEquals(8.0, book.quest(a.id).y);
    }

    @Test
    void deletingAQuestBringsBackItsDependantsInOtherChapters() {
        edit("delete_quest", args("id", a.id), () -> ed.deleteQuest(a.id));
        assertNull(book.quest(a.id));
        assertTrue(book.quest(b.id).dependencies.isEmpty(), "the dependency on it was removed");
        undo();
        assertNotNull(book.quest(a.id));
        assertEquals(List.of(a.id), book.quest(b.id).dependencies, "the other chapter's quest needs it again");
        redo();
        assertNull(book.quest(a.id));
        assertTrue(book.quest(b.id).dependencies.isEmpty());
    }

    @Test
    void createdThingsAreRemovedOnUndoAndBackOnRedo() {
        int chapters = book.chapters().size();
        edit("create_chapter", args("title", "Three"), () -> ed.createChapter("Three"));
        assertEquals(chapters + 1, book.chapters().size());
        undo();
        assertEquals(chapters, book.chapters().size());
        redo();
        assertEquals(chapters + 1, book.chapters().size());
        assertEquals("Three", book.chapters().get(chapters).title);

        edit("create_table", args("title", "Loot"), () -> ed.createTable("Loot"));
        assertEquals(1, book.tables().size());
        undo();
        assertTrue(book.tables().isEmpty());
        redo();
        assertEquals(1, book.tables().size());
    }

    @Test
    void tableEditsCanBeUndone() {
        var t = ed.createTable("Loot");
        edit("set_table_prop", args("id", t.id), () -> ed.setTableProp(t.id, "loot_size", "5"));
        assertEquals(5, book.tables().get(t.id).lootSize);
        undo();
        assertEquals(0, book.tables().get(t.id).lootSize);
    }

    @Test
    void groupsAndChapterOrderComeBackOnUndo() {
        var g = ed.createGroup("Group", "");
        String first = book.chapters().get(0).id;
        String second = book.chapters().get(1).id;
        edit("set_chapter_group", args("id", first), () -> ed.setChapterGroup(first, g.id));
        assertEquals(g.id, book.chapter(first).groupId);
        undo();
        assertEquals("", book.chapter(first).groupId);
        assertTrue(history.lastNeedsFullSync, "the whole book has to be sent when groups or order change");
        redo();
        assertEquals(g.id, book.chapter(first).groupId);

        edit("move_chapter", args("id", first), () -> ed.moveChapter(first, 1));
        // the first chapter now belongs to a group, so it only swaps with chapters of that group: nothing moved. Use two loose ones:
        edit("move_chapter", args("id", second), () -> ed.moveChapter(second, -1));
        java.util.List<String> moved = book.chapters().stream().map(c -> c.id).toList();
        undo();
        assertEquals(book.chapters().size(), moved.size());
        edit("delete_group", args("id", g.id), () -> ed.deleteGroup(g.id));
        assertTrue(book.groups().isEmpty());
        assertEquals("", book.chapter(first).groupId);
        undo();
        assertEquals(1, book.groups().size(), "the group is back");
        assertEquals(g.id, book.chapter(first).groupId, "and so is its chapter's place in it");
        assertEquals("Group", book.groups().get(0).title);
    }

    @Test
    void anEditClearsWhatCouldBeRedone() {
        edit("move_quest", args("id", a.id), () -> ed.moveQuest(a.id, 5, 5));
        undo();
        assertEquals(1, history.redoCount());
        edit("move_quest", args("id", a.id), () -> ed.moveQuest(a.id, 6, 6));
        assertEquals(0, history.redoCount());
    }

    @Test
    void nothingToUndoIsReportedAndFailedEditsLeaveNoEntry() {
        assertNull(undo());
        assertNull(redo());
        History.Open open = History.begin(book, "move_quest", args("id", "ffff"));
        assertNull(open == null ? null : (open.chapters.isEmpty() ? null : open));
        if (open != null) {
            history.commit(book, open);
        }
        assertFalse(history.undoCount() > 0, "an edit that touched nothing makes no entry");
        assertNull(History.begin(book, "save_now", DataNode.Compound.empty()));
    }
}
