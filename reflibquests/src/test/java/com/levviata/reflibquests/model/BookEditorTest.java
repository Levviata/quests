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
    void textEditsWorkOnTheCurrentTextAndAcceptLongValues() {
        Quest q = ed.createQuest(chapter.id, 1, 1);
        assertEquals(OK, ed.setTitleOnly(q.id, "A title"));
        assertEquals("A title", q.title);
        assertEquals(OK, ed.setSubtitleOnly(q.id, "s".repeat(500)), "long subtitles are fine");
        assertEquals(500, q.subtitle.length());
        assertEquals(OK, ed.addLines(q.id, List.of("one")));
        assertEquals(OK, ed.addLines(q.id, List.of("two", "three")));
        assertEquals(List.of("one", "two", "three"), q.description, "each call adds to what is already there");
        assertEquals(OK, ed.addLines(q.id, List.of("x".repeat(3000))));
        assertEquals(INVALID, ed.addLines(q.id, List.of()));
        assertEquals(INVALID, ed.addLines(q.id, List.of("two" + (char) 10 + "lines")));
        assertEquals(NOT_FOUND, ed.addLines("ffff", List.of("a")));
        assertEquals(INVALID, ed.setTitleOnly(q.id, "x".repeat(BookEditor.MAX_TITLE + 1)));
    }

    @Test
    void picturesCanBeMovedAndDeleted() {
        chapter.images.add(new Definitions.ChapterImage("img1", DataNode.Compound.empty().with("image", "a:b.png").with("x", 1.0).with("y", 2.0)));
        assertEquals(OK, ed.moveImage("img1", 3.13, -4.0));
        assertEquals(3.25, chapter.images.get(0).raw().getDouble("x", 0), "positions snap to a quarter unit");
        assertEquals(-4.0, chapter.images.get(0).raw().getDouble("y", 0));
        assertEquals("a:b.png", chapter.images.get(0).raw().getString("image", ""), "the rest of the picture's data is kept");
        assertEquals(INVALID, ed.moveImage("img1", Double.NaN, 0));
        assertEquals(NOT_FOUND, ed.moveImage("nope", 0, 0));
        assertEquals(OK, ed.deleteImage("img1"));
        assertTrue(chapter.images.isEmpty());
        assertEquals(NOT_FOUND, ed.deleteImage("img1"));
    }

    @Test
    void setPropChangesQuestProperties() {
        Quest q = ed.createQuest(chapter.id, 1, 1);
        assertNotNull(q);
        assertEquals(OK, ed.setProp(q.id, "hide_dep_lines", "true"));
        assertEquals(Tristate.TRUE, q.hideDependencyLines);
        assertEquals(OK, ed.setProp(q.id, "hide_dependent_lines", "false"));
        assertEquals(Tristate.FALSE, q.hideDependentLines);
        assertEquals(OK, ed.setProp(q.id, "hide_dep_lines", "default"));
        assertEquals(Tristate.DEFAULT, q.hideDependencyLines);
        assertEquals(OK, ed.setProp(q.id, "dependency_requirement", "ONE_STARTED"));
        assertEquals(Definitions.DependencyMode.ONE_STARTED, q.dependencyMode);
        assertEquals(OK, ed.setProp(q.id, "size", "2.5"));
        assertEquals(2.5, q.size);
        assertEquals(OK, ed.setProp(q.id, "optional", "true"));
        assertTrue(q.optional);
        assertEquals(OK, ed.setProp(q.id, "min_required_dependencies", "3"));
        assertEquals(3, q.minRequiredDependencies);
        assertEquals(OK, ed.setProp(q.id, "shape", "hexagon"));
        assertEquals("hexagon", q.shape);
    }

    @Test
    void setPropHandlesTheQuickMenuProperties() {
        Quest q = ed.createQuest(chapter.id, 1, 1);
        assertEquals(OK, ed.setProp(q.id, "disable_toast", "true"));
        assertTrue(q.disableToast);
        assertEquals(OK, ed.setProp(q.id, "hide_lock_icon", "true"));
        assertTrue(q.hideLockIcon);
        assertEquals(OK, ed.setProp(q.id, "ignore_reward_blocking", "true"));
        assertTrue(q.ignoreRewardBlocking);
        assertEquals(OK, ed.setProp(q.id, "hide_text_until_complete", "true"));
        assertEquals(Tristate.TRUE, q.hideTextUntilComplete);
        assertEquals(OK, ed.setProp(q.id, "disable_jei", "false"));
        assertEquals(Tristate.FALSE, q.disableJei);
        assertEquals(OK, ed.setProp(q.id, "progression_mode", "FLEXIBLE"));
        assertEquals(Definitions.ProgressionMode.FLEXIBLE, q.progressionMode);
        assertEquals(INVALID, ed.setProp(q.id, "progression_mode", "SOMETIMES"));
        assertEquals(INVALID, ed.setProp(q.id, "hide_text_until_complete", "maybe"));
    }

    @Test
    void setPropRefusesBadValues() {
        Quest q = ed.createQuest(chapter.id, 1, 1);
        assertEquals(INVALID, ed.setProp(q.id, "size", "100"));
        assertEquals(INVALID, ed.setProp(q.id, "size", "abc"));
        assertEquals(INVALID, ed.setProp(q.id, "dependency_requirement", "SOMETIMES"));
        assertEquals(INVALID, ed.setProp(q.id, "min_required_dependencies", "-1"));
        assertEquals(INVALID, ed.setProp(q.id, "repeatable", "maybe"));
        assertEquals(INVALID, ed.setProp(q.id, "no_such_property", "true"));
        assertEquals(NOT_FOUND, ed.setProp("ffff", "optional", "true"));
    }

    @Test
    void chapterPropertiesAreSetAndChecked() {
        assertEquals(OK, ed.setChapterProp(chapter.id, "shape", "hexagon"));
        assertEquals("hexagon", chapter.defaultQuestShape);
        assertEquals(OK, ed.setChapterProp(chapter.id, "shape", ""));
        assertEquals("", chapter.defaultQuestShape);
        assertEquals(OK, ed.setChapterProp(chapter.id, "always_invisible", "true"));
        assertTrue(chapter.alwaysInvisible);
        assertEquals(OK, ed.setChapterProp(chapter.id, "hide_dep_lines", "true"));
        assertTrue(chapter.defaultHideDependencyLines);
        assertEquals(OK, ed.setChapterProp(chapter.id, "progression_mode", "LINEAR"));
        assertEquals(Definitions.ProgressionMode.LINEAR, chapter.progressionMode);
        assertEquals(OK, ed.setChapterProp(chapter.id, "sequential", "true"));
        assertTrue(chapter.requireSequentialTasks);
        assertEquals(INVALID, ed.setChapterProp(chapter.id, "shape", "blob"));
        assertEquals(INVALID, ed.setChapterProp(chapter.id, "progression_mode", "SOMETIMES"));
        assertEquals(INVALID, ed.setChapterProp(chapter.id, "nothing", "true"));
        assertEquals(NOT_FOUND, ed.setChapterProp("ffff", "shape", "circle"));
    }

    @Test
    void rewardTablesCanBeBuiltAndChanged() {
        Definitions.RewardTable t = ed.createTable("Loot");
        assertNotNull(t);
        assertNull(ed.createTable("  "));
        assertEquals(t, book.tables().get(t.id));
        assertEquals("loot", t.filename);
        assertEquals("loot_2", ed.createTable("Loot").filename, "file names stay unique");

        DataNode.Compound params = DataNode.Compound.empty().with("item", DataNode.Compound.empty().with("id", "minecraft:apple")).with("count", 1);
        String entry = ed.addTableEntry(t.id, "item", params, 2f);
        assertNotNull(entry);
        assertNull(ed.addTableEntry(t.id, "item", DataNode.Compound.empty(), 1f), "an item entry needs an item");
        assertNull(ed.addTableEntry(t.id, "item", params, 0f), "weights are above zero");
        assertNull(ed.addTableEntry(t.id, "nonsense", params, 1f));
        assertNull(ed.addTableEntry("ffff", "item", params, 1f));

        Definitions.RewardTable chosen = ed.createTable("Chosen", "00000000000000AB");
        assertEquals("00000000000000AB", chosen.id, "a caller may pick the id");
        assertNotEquals("00000000000000AB", ed.createTable("Again", "00000000000000AB").id, "but not one that is taken");
        assertNotEquals("zzz", ed.createTable("Bad id", "zzz").id);
        assertEquals("00000000000000CD", ed.addTableEntry(chosen.id, "xp", DataNode.Compound.empty().with("xp", 5), 1f, "00000000000000CD"));

        assertEquals(OK, ed.setEntryWeight(t.id, entry, 5f));
        assertEquals(5f, t.entries.get(0).weight());
        assertEquals(INVALID, ed.setEntryWeight(t.id, entry, -1f));
        assertEquals(NOT_FOUND, ed.setEntryWeight(t.id, "nope", 1f));

        assertEquals(OK, ed.setTableProp(t.id, "empty_weight", "3.5"));
        assertEquals(3.5f, t.emptyWeight);
        assertEquals(OK, ed.setTableProp(t.id, "loot_size", "4"));
        assertEquals(4, t.lootSize);
        assertEquals(INVALID, ed.setTableProp(t.id, "loot_size", "-1"));
        assertEquals(INVALID, ed.setTableProp(t.id, "empty_weight", "abc"));
        assertEquals(INVALID, ed.setTableProp(t.id, "bogus", "1"));

        assertEquals(OK, ed.renameTable(t.id, "Better loot"));
        assertEquals("Better loot", t.title);
        assertEquals(INVALID, ed.renameTable(t.id, ""));

        // a table that a reward uses cannot be deleted
        Quest q = ed.createQuest(chapter.id, 0, 0);
        assertNotNull(ed.addReward(q.id, "xp", DataNode.Compound.empty().with("table_id", t.id).with("xp", 5)));
        assertEquals(INVALID, ed.deleteTable(t.id));
        ed.removeReward(q.rewards.get(0).id());
        assertEquals(OK, ed.removeTableEntry(t.id, entry));
        assertEquals(NOT_FOUND, ed.removeTableEntry(t.id, entry));
        assertEquals(OK, ed.deleteTable(t.id));
        assertEquals(NOT_FOUND, ed.deleteTable(t.id));
    }

    @Test
    void taskAndRewardSettingsAreSetAndChecked() {
        Quest q = ed.createQuest(chapter.id, 0, 0);
        String task = ed.addTask(q.id, "item", DataNode.Compound.empty().with("item", DataNode.Compound.empty().with("id", "minecraft:apple")).with("count", 3));
        assertNotNull(task);
        assertEquals(OK, ed.setTaskProp(task, "title", "Eat apples"));
        assertEquals("Eat apples", q.tasks.get(q.tasks.size() - 1).params().getString("title", ""));
        assertEquals(OK, ed.setTaskProp(task, "title", ""));
        assertFalse(q.tasks.get(q.tasks.size() - 1).params().has("title"), "an empty title removes it");
        assertEquals(OK, ed.setTaskProp(task, "optional_task", "true"));
        assertTrue(q.tasks.get(q.tasks.size() - 1).optionalTask());
        assertEquals(OK, ed.setTaskProp(task, "consume_items", "false"));
        assertEquals(INVALID, ed.setTaskProp(task, "consume_items", "maybe"));
        assertEquals(INVALID, ed.setTaskProp(task, "optional_task", "yes"));
        assertEquals(INVALID, ed.setTaskProp(task, "nonsense", "x"));
        assertEquals(INVALID, ed.setTaskProp(task, "title", "two" + (char) 10 + "lines"));
        assertEquals(NOT_FOUND, ed.setTaskProp("ffff", "title", "x"));
        String check = ed.addTask(q.id, "checkmark", DataNode.Compound.empty());
        assertEquals(INVALID, ed.setTaskProp(check, "consume_items", "true"), "only item tasks consume items");

        String reward = ed.addReward(q.id, "xp", DataNode.Compound.empty().with("xp", 10));
        assertNotNull(reward);
        assertEquals(OK, ed.setRewardProp(reward, "xp", "250"));
        assertEquals(250, q.rewards.get(0).params().getInt("xp", 0));
        assertEquals(INVALID, ed.setRewardProp(reward, "xp", "0"));
        assertEquals(INVALID, ed.setRewardProp(reward, "xp", "lots"));
        assertEquals(INVALID, ed.setRewardProp(reward, "count", "5"), "an XP reward has no item count");
        assertEquals(OK, ed.setRewardProp(reward, "auto", "no_toast"));
        assertEquals("no_toast", q.rewards.get(0).autoClaim());
        assertEquals(OK, ed.setRewardProp(reward, "auto", "default"));
        assertEquals("default", q.rewards.get(0).autoClaim());
        assertEquals(INVALID, ed.setRewardProp(reward, "auto", "sometimes"));
        assertEquals(NOT_FOUND, ed.setRewardProp("ffff", "xp", "5"));
    }

    @Test
    void chapterGroupsCanBeMadeFilledReorderedAndRemoved() {
        Chapter second = ed.createChapter("Second");
        Chapter third = ed.createChapter("Third");
        Definitions.ChapterGroup g = ed.createGroup("Group", "");
        assertNotNull(g);
        assertNull(ed.createGroup("  ", ""));
        assertEquals("0000000000000ABC".length(), ed.createGroup("Own id", "0000000000000ABC").id.length());
        assertEquals("0000000000000ABC", book.groups().get(1).id, "a caller may choose the id");

        assertEquals(OK, ed.renameGroup(g.id, "Renamed"));
        assertEquals("Renamed", g.title);
        assertEquals(INVALID, ed.renameGroup(g.id, ""));
        assertEquals(NOT_FOUND, ed.renameGroup("ffff", "x"));

        assertEquals(OK, ed.setChapterGroup(second.id, g.id));
        assertEquals(OK, ed.setChapterGroup(third.id, g.id));
        assertEquals(g.id, second.groupId);
        assertEquals(INVALID, ed.setChapterGroup(chapter.id, "nope"));
        assertEquals(NOT_FOUND, ed.setChapterGroup("ffff", g.id));

        // chapters move only among the chapters of their own group
        assertEquals(OK, ed.moveChapter(third.id, -1));
        assertEquals(java.util.List.of(chapter.id, third.id, second.id), book.chapters().stream().map(c -> c.id).toList());
        assertEquals(OK, ed.moveChapter(third.id, -1), "already first in its group: nothing changes");
        assertEquals(java.util.List.of(chapter.id, third.id, second.id), book.chapters().stream().map(c -> c.id).toList());
        assertEquals(0, book.chapters().get(0).order);
        assertEquals(2, book.chapters().get(2).order);
        assertEquals(INVALID, ed.moveChapter(third.id, 5));

        assertEquals(OK, ed.moveGroup(g.id, 1));
        assertEquals(g.id, book.groups().get(1).id);

        assertEquals(OK, ed.deleteGroup(g.id));
        assertEquals("", second.groupId, "its chapters stay, without a group");
        assertEquals(NOT_FOUND, ed.deleteGroup(g.id));
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
