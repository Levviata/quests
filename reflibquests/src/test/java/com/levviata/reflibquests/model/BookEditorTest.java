/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import static com.levviata.reflibquests.model.BookEditor.Result.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.QuestLink;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Tristate;

class BookEditorTest {

    private QuestBook book;
    private BookEditor ed;
    private Chapter chapter;

    @BeforeEach
    void setUp() {
        book = new QuestBook();
        ed = new BookEditor(book, new Random(5));
        chapter = ed.createChapter("First chapter");
    }

    @Test
    void chaptersCreateRenameDelete() {
        assertNotNull(chapter);
        assertEquals("first_chapter", chapter.filename);
        Chapter second = ed.createChapter("First Chapter!");
        assertEquals("first_chapter_2", second.filename, "file names stay unique");
        assertTrue(second.order > chapter.order, "new chapters go last");
        assertNull(ed.createChapter("   "));
        assertNull(ed.createChapter("x".repeat(BookEditor.MAX_TITLE + 1)));
        assertNull(ed.createChapter("two\nlines"));
        assertEquals(OK, ed.renameChapter(chapter.id, "Renamed"));
        assertEquals("Renamed", book.chapter(chapter.id).title);
        assertEquals(INVALID, ed.renameChapter(chapter.id, ""));
        assertEquals(NOT_FOUND, ed.renameChapter("nope", "x"));
        assertEquals(OK, ed.deleteChapter(second.id));
        assertNull(book.chapter(second.id));
        assertEquals(NOT_FOUND, ed.deleteChapter(second.id));
    }

    @Test
    void idsAreSixteenHexDigitsAndUnique() {
        Quest a = ed.createQuest(chapter.id, 0, 0);
        Quest b = ed.createQuest(chapter.id, 1, 1);
        assertTrue(a.id.matches("[0-9A-F]{16}"), a.id);
        assertNotEquals(a.id, b.id);
        assertNotEquals(a.tasks.get(0).id(), b.tasks.get(0).id());
    }

    @Test
    void createQuestSnapsAndValidates() {
        Quest q = ed.createQuest(chapter.id, 1.26, -2.74);
        assertEquals(1.5, q.x);
        assertEquals(-2.5, q.y);
        assertEquals("New quest", q.title);
        assertEquals(1, q.tasks.size());
        assertEquals("checkmark", q.tasks.get(0).type());
        assertSame(q, book.quest(q.id));
        assertNull(ed.createQuest("nope", 0, 0));
        assertNull(ed.createQuest(chapter.id, Double.NaN, 0));
        assertNull(ed.createQuest(chapter.id, 0, 1e9));
    }

    @Test
    void moveAndDeleteQuestCleanUpReferences() {
        Quest a = ed.createQuest(chapter.id, 0, 0);
        Quest b = ed.createQuest(chapter.id, 2, 0);
        Chapter other = ed.createChapter("Other");
        Quest c = ed.createQuest(other.id, 0, 0);
        assertEquals(OK, ed.toggleDependency(b.id, a.id));
        assertEquals(OK, ed.toggleDependency(c.id, a.id));
        other.links.add(new QuestLink("L1", a.id, 1, 1, DataNode.Compound.empty()));
        assertEquals(OK, ed.moveQuest(a.id, 5.1, 5.9));
        assertEquals(5.0, a.x);
        assertEquals(6.0, a.y);
        assertEquals(INVALID, ed.moveQuest(a.id, Double.POSITIVE_INFINITY, 0));
        assertEquals(OK, ed.deleteQuest(a.id));
        assertNull(book.quest(a.id));
        assertTrue(b.dependencies.isEmpty());
        assertTrue(c.dependencies.isEmpty());
        assertTrue(other.links.isEmpty(), "links to a deleted quest go too");
        assertEquals(NOT_FOUND, ed.deleteQuest(a.id));
    }

    @Test
    void deletingAChapterCleansReferencesToItsQuests() {
        Quest a = ed.createQuest(chapter.id, 0, 0);
        Chapter other = ed.createChapter("Other");
        Quest c = ed.createQuest(other.id, 0, 0);
        ed.toggleDependency(c.id, a.id);
        assertEquals(OK, ed.deleteChapter(chapter.id));
        assertTrue(c.dependencies.isEmpty());
        assertNull(book.quest(a.id));
    }

    @Test
    void dependenciesToggleAndRefuseCycles() {
        Quest a = ed.createQuest(chapter.id, 0, 0);
        Quest b = ed.createQuest(chapter.id, 1, 0);
        Quest c = ed.createQuest(chapter.id, 2, 0);
        assertEquals(INVALID, ed.toggleDependency(a.id, a.id));
        assertEquals(OK, ed.toggleDependency(b.id, a.id));
        assertEquals(OK, ed.toggleDependency(c.id, b.id));
        assertEquals(CYCLE, ed.toggleDependency(a.id, c.id), "a -> c -> b -> a would loop");
        assertEquals(CYCLE, ed.toggleDependency(a.id, b.id));
        assertEquals(List.of(), a.dependencies);
        assertEquals(OK, ed.toggleDependency(b.id, a.id), "toggling again removes it");
        assertTrue(b.dependencies.isEmpty());
        assertEquals(NOT_FOUND, ed.toggleDependency(a.id, "nope"));
    }

    @Test
    void textIsValidated() {
        Quest q = ed.createQuest(chapter.id, 0, 0);
        assertEquals(OK, ed.setText(q.id, " Title ", "Sub", List.of("a", "b")));
        assertEquals("Title", q.title);
        assertEquals(List.of("a", "b"), q.description);
        assertEquals(INVALID, ed.setText(q.id, "x\ny", "", List.of()));
        assertEquals(INVALID, ed.setText(q.id, "x", "", List.of("one\ntwo")));
        assertEquals(INVALID, ed.setText(q.id, "x", "", java.util.Collections.nCopies(BookEditor.MAX_LINES + 1, "l")));
        assertEquals(INVALID, ed.setText(q.id, "x", "", List.of("y".repeat(BookEditor.MAX_LINE + 1))));
        assertEquals("Title", q.title, "a rejected edit changes nothing");
        assertEquals(NOT_FOUND, ed.setText("nope", "x", "", List.of()));
    }

    @Test
    void tasksAddRemoveAndSetValue() {
        Quest q = ed.createQuest(chapter.id, 0, 0);
        DataNode.Compound item = DataNode.Compound.empty().with("item", DataNode.Compound.empty().with("id", "minecraft:dirt")).with("count", 3);
        String taskId = ed.addTask(q.id, "item", item);
        assertNotNull(taskId);
        assertEquals(3, book.task(taskId).maxProgress());
        assertEquals(OK, ed.setTaskValue(taskId, 64));
        assertEquals(64, book.task(taskId).maxProgress());
        assertEquals(OK, ed.setTaskValue(taskId, 3_000_000_000L > BookEditor.MAX_VALUE ? 999_999_999L : 1));
        assertEquals(INVALID, ed.setTaskValue(taskId, 0));
        assertEquals(INVALID, ed.setTaskValue(taskId, BookEditor.MAX_VALUE + 1));
        String xp = ed.addTask(q.id, "xp", DataNode.Compound.empty());
        assertEquals(1, book.task(xp).maxProgress(), "xp tasks get a default value");
        assertEquals(OK, ed.setTaskValue(xp, 30));
        String check = q.tasks.get(0).id();
        assertEquals(INVALID, ed.setTaskValue(check, 5), "checkmarks have no amount");
        assertNull(ed.addTask(q.id, "item", DataNode.Compound.empty()), "an item task needs an item");
        assertNull(ed.addTask(q.id, "fluid", DataNode.Compound.empty()), "only editor task types");
        assertNull(ed.addTask("nope", "checkmark", DataNode.Compound.empty()));
        assertEquals(OK, ed.removeTask(taskId));
        assertNull(book.task(taskId));
        assertEquals(NOT_FOUND, ed.removeTask(taskId));
    }

    @Test
    void taskLimit() {
        Quest q = ed.createQuest(chapter.id, 0, 0);
        for (int i = 1; i < 20; i++) {
            assertNotNull(ed.addTask(q.id, "checkmark", DataNode.Compound.empty()));
        }
        assertNull(ed.addTask(q.id, "checkmark", DataNode.Compound.empty()));
    }

    @Test
    void rewardsAddAndRemove() {
        Quest q = ed.createQuest(chapter.id, 0, 0);
        String r1 = ed.addReward(q.id, "xp", DataNode.Compound.empty().with("xp", 10));
        assertNotNull(r1);
        assertNull(ed.addReward(q.id, "item", DataNode.Compound.empty()), "an item reward needs an item");
        assertNull(ed.addReward(q.id, "loot", DataNode.Compound.empty()), "only editor reward types");
        assertEquals(1, q.rewards.size());
        assertEquals(OK, ed.removeReward(r1));
        assertEquals(NOT_FOUND, ed.removeReward(r1));
        assertTrue(q.rewards.isEmpty());
    }

    @Test
    void flagsAndShape() {
        Quest q = ed.createQuest(chapter.id, 0, 0);
        assertEquals(OK, ed.setFlag(q.id, "optional", true));
        assertTrue(q.optional);
        assertEquals(OK, ed.setFlag(q.id, "repeatable", true));
        assertEquals(Tristate.TRUE, q.canRepeat);
        assertEquals(OK, ed.setFlag(q.id, "repeatable", false));
        assertEquals(Tristate.DEFAULT, q.canRepeat, "off means 'use the chapter default'");
        assertEquals(OK, ed.setFlag(q.id, "hide_details", true));
        assertEquals(Tristate.TRUE, q.hideDetailsUntilStartable);
        assertEquals(INVALID, ed.setFlag(q.id, "no_such_flag", true));
        assertEquals(NOT_FOUND, ed.setFlag("nope", "optional", true));
        assertEquals(OK, ed.setShape(q.id, "Diamond"));
        assertEquals("diamond", q.shape);
        assertEquals(INVALID, ed.setShape(q.id, "triangle"));
        assertEquals(OK, ed.setShape(q.id, ""));
    }

    @Test
    void editedBookSurvivesProgressRules() {
        Quest a = ed.createQuest(chapter.id, 0, 0);
        Quest b = ed.createQuest(chapter.id, 2, 0);
        ed.toggleDependency(b.id, a.id);
        TeamProgress p = new TeamProgress(UUID.randomUUID());
        ProgressEngine engine = new ProgressEngine(book);
        assertTrue(engine.setTaskProgress(p, b.tasks.get(0).id(), 1, 1).isEmpty(), "b waits for a");
        engine.setTaskProgress(p, a.tasks.get(0).id(), 1, 2);
        assertFalse(engine.setTaskProgress(p, b.tasks.get(0).id(), 1, 3).isEmpty());
        assertTrue(p.isCompleted(b.id));
    }
}
