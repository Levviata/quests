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
    void picturesCanBeCreatedAndALockedOneStaysPut() {
        String id = ed.createImage(chapter.id, "minecraft:backyard", 9.6, 2.0, 0.75, 1.0);
        assertNotNull(id);
        var raw = chapter.images.get(0).raw();
        assertEquals("minecraft:backyard", raw.getString("image", ""));
        assertEquals(0.75, raw.getDouble("width", 0));
        assertEquals(1.0, raw.getDouble("height", 0));
        assertEquals(9.5, raw.getDouble("x", 0), "positions snap like a move does");
        assertNull(ed.createImage("nope", "a:b", 0, 0, 1, 1), "unknown chapter");
        assertNull(ed.createImage(chapter.id, " ", 0, 0, 1, 1), "no name");
        assertNull(ed.createImage(chapter.id, "a:b", Double.NaN, 0, 1, 1), "bad place");
        assertEquals(OK, ed.setImageProps(id, DataNode.Compound.empty().with("position_locked", "true")));
        assertEquals(INVALID, ed.moveImage(id, 1, 1), "Lock Position: the picture cannot be moved");
        assertEquals(9.5, chapter.images.get(0).raw().getDouble("x", 0));
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
    void pastedQuestsGetNewIdsAndKeepTheirLayoutAndInternalDependencies() {
        Quest a = ed.createQuest(chapter.id, 1, 1);
        Quest b = ed.createQuest(chapter.id, 3, 2);
        Quest outside = ed.createQuest(chapter.id, 9, 9);
        b.dependencies.add(a.id);
        b.dependencies.add(outside.id);
        java.util.List<String> made = ed.pasteQuests(chapter.id, java.util.List.of(a.id, b.id), 10, 10, true);
        assertEquals(2, made.size());
        Quest ca = book.quest(made.get(0));
        Quest cb = book.quest(made.get(1));
        assertEquals(10, ca.x);
        assertEquals(10, ca.y);
        assertEquals(12, cb.x);
        assertEquals(11, cb.y);
        assertEquals(java.util.List.of(ca.id, outside.id), cb.dependencies, "inner dependency follows the copy, outside one is kept");
        assertEquals(1, ca.tasks.size());
        assertEquals(false, ca.tasks.get(0).id().equals(a.tasks.get(0).id()), "tasks get new ids");
        java.util.List<String> bare = ed.pasteQuests(chapter.id, java.util.List.of(b.id), 0, 0, false);
        assertEquals(java.util.List.of(), book.quest(bare.get(0)).dependencies);
        assertNull(ed.pasteQuests("nope", java.util.List.of(a.id), 0, 0, true));
    }

    @Test
    void groupMoveAndDeleteAreSingleEditsAndAllOrNothing() {
        Quest a = ed.createQuest(chapter.id, 1, 1);
        Quest b = ed.createQuest(chapter.id, 3, 3);
        DataNode.Compound moves = DataNode.Compound.empty().with("moves", new DataNode.Lst(java.util.List.of(
                DataNode.Compound.empty().with("id", a.id).with("x", 2.0).with("y", 2.0),
                DataNode.Compound.empty().with("id", b.id).with("x", 4.0).with("y", 4.0))));
        assertEquals(OK, ed.moveQuests(moves));
        assertEquals(2.0, a.x);
        assertEquals(4.0, b.y);
        DataNode.Compound bad = DataNode.Compound.empty().with("moves", new DataNode.Lst(java.util.List.of(
                DataNode.Compound.empty().with("id", a.id).with("x", 9.0).with("y", 9.0),
                DataNode.Compound.empty().with("id", "nope").with("x", 1.0).with("y", 1.0))));
        assertEquals(NOT_FOUND, ed.moveQuests(bad));
        assertEquals(2.0, a.x, "nothing moved because one entry was invalid");
        assertEquals(OK, ed.deleteQuests(DataNode.Compound.empty().with("ids",
                new DataNode.Lst(java.util.List.of(new DataNode.Str(a.id), new DataNode.Str(b.id))))));
        assertNull(book.quest(a.id));
        assertNull(book.quest(b.id));
    }

    @Test
    void bezierPointsAreKeptPerDependencyAndSurviveCopyingAndTheFileFormat() {
        Quest dep = ed.createQuest(chapter.id, -6.5, -1.5);
        Quest q = ed.createQuest(chapter.id, -3, -1.5);
        q.dependencies.add(dep.id);
        DataNode.Compound pts = DataNode.Compound.empty().with(dep.id, new DataNode.Lst(java.util.List.of(
                new DataNode.Dbl(-15.982142857142861), new DataNode.Dbl(-6.071428571428566),
                new DataNode.Dbl(10.017857142857139), new DataNode.Dbl(2.750000000000007))));
        assertEquals(OK, ed.setBezier(q.id, pts));
        assertEquals(java.util.List.of(-15.982142857142861, -6.071428571428566, 10.017857142857139, 2.750000000000007), q.depControlPts.get(dep.id));
        assertEquals(q.depControlPts, DeepCopy.quest(q, "COPY").depControlPts, "the history copies the curve");
        assertEquals(q.depControlPts, ControlPoints.read(ControlPoints.write(q.depControlPts)), "the file format round-trips");
        DataNode.Compound notADependency = DataNode.Compound.empty().with("nope", pts.get(dep.id));
        assertEquals(INVALID, ed.setBezier(q.id, notADependency));
        assertEquals(OK, ed.setBezier(q.id, DataNode.Compound.empty()));
        assertEquals(true, q.depControlPts.isEmpty(), "an empty set makes the lines straight again");
    }

    @Test
    void aChapterCanHoldAGroupButNotOneItIsIn() {
        Chapter holder = ed.createChapter("Checklists");
        Chapter inside = ed.createChapter("Inside");
        var group = ed.createGroup("Held", "");
        assertEquals(OK, ed.setChapterGroup(inside.id, group.id));
        assertEquals(INVALID, ed.setGroupChapter(group.id, inside.id), "a chapter cannot hold the group it is in");
        assertEquals(OK, ed.setGroupChapter(group.id, holder.id));
        assertEquals(holder.id, group.raw.getString("chapter", ""));
        assertEquals(NOT_FOUND, ed.setGroupChapter(group.id, "nope"));
        assertEquals(OK, ed.setGroupChapter(group.id, ""));
        assertEquals("", group.raw.getString("chapter", ""), "letting go clears it");
    }

    @Test
    void tasksAndRewardsMoveOnePlaceAndStopAtTheEnds() {
        Quest q = ed.createQuest(chapter.id, 0, 0);
        String second = ed.addTask(q.id, "checkmark", DataNode.Compound.empty());
        String first = q.tasks.get(0).id();
        assertEquals(INVALID, ed.moveTask(first, -1), "already first");
        assertEquals(OK, ed.moveTask(first, 1));
        assertEquals(second, q.tasks.get(0).id());
        assertEquals(INVALID, ed.moveTask(first, 1), "now last");
        String r1 = ed.addReward(q.id, "xp", DataNode.Compound.empty().with("xp", 1));
        String r2 = ed.addReward(q.id, "xp", DataNode.Compound.empty().with("xp", 2));
        assertEquals(OK, ed.moveReward(r2, -1));
        assertEquals(r2, q.rewards.get(0).id());
        assertEquals(INVALID, ed.moveReward(r1, 1));
    }

    @Test
    void newChapterPropertiesAreCheckedAndNewQuestsStartWithTheChapterSize() {
        assertEquals(OK, ed.setChapterProp(chapter.id, "disable_toast", "true"));
        assertEquals(true, chapter.disableToast);
        assertEquals(OK, ed.setChapterProp(chapter.id, "hide_text", "true"));
        assertEquals(OK, ed.setChapterProp(chapter.id, "consume_items", "true"));
        assertEquals(Tristate.TRUE, chapter.defaultConsumeItems);
        assertEquals(INVALID, ed.setChapterProp(chapter.id, "consume_items", "maybe"));
        assertEquals(OK, ed.setChapterProp(chapter.id, "min_width", "300"));
        assertEquals(300, chapter.minWindowWidth);
        assertEquals(INVALID, ed.setChapterProp(chapter.id, "min_width", "-1"));
        assertEquals(OK, ed.setChapterProp(chapter.id, "tags", " a, b ,,c"));
        assertEquals(List.of("a", "b", "c"), chapter.tags);
        assertEquals(OK, ed.setChapterProp(chapter.id, "quest_size", "1.5"));
        assertEquals(INVALID, ed.setChapterProp(chapter.id, "quest_size", "0"));
        assertEquals(1.5, ed.createQuest(chapter.id, 0, 0).size, "new quests start with the chapter's size");
    }

    @Test
    void moreBookSettingsAreCheckedAndKept() {
        assertEquals(OK, ed.setBookProp("pause_game", "true"));
        assertEquals(true, book.settings.getBool("pause_game", false));
        assertEquals(OK, ed.setBookProp("grid_scale", "0.5"));
        assertEquals(INVALID, ed.setBookProp("grid_scale", "0"));
        assertEquals(OK, ed.setBookProp("loot_monster", "600"));
        assertEquals(600, book.settings.getCompound("loot_crate_no_drop").getInt("monster", 0));
        assertEquals(OK, ed.setBookProp("tags", "a, b"));
        assertEquals(2, book.settings.getList("tags").size());
        assertEquals(INVALID, ed.setBookProp("emergency_items_cooldown", "x"));
        assertEquals(OK, ed.setBookProp("lock_message", "Finish the earlier quests first"));
    }

    @Test
    void questLinksAreCreatedAndRemovedAndPicturesTakeSeveralPropertiesAtOnce() {
        Quest q = ed.createQuest(chapter.id, 0, 0);
        Chapter other = ed.createChapter("Other");
        String link = ed.createLink(other.id, q.id, 2.26, 3);
        assertEquals(1, other.links.size());
        assertEquals(2.5, other.links.get(0).x(), "links snap to the grid");
        assertEquals(q.id, other.links.get(0).linkedQuest());
        assertNull(ed.createLink(other.id, "nope", 0, 0));
        assertEquals(OK, ed.deleteLink(link));
        assertEquals(0, other.links.size());
        chapter.images.add(new Definitions.ChapterImage("IMG1", DataNode.Compound.empty().with("image", "minecraft:x").with("x", 0.0).with("y", 0.0)));
        DataNode.Compound props = DataNode.Compound.empty().with("width", 2.5).with("height", 1.25).with("rotation", 45.0).with("dev", "true");
        assertEquals(OK, ed.setImageProps("IMG1", props));
        DataNode.Compound raw = chapter.images.get(0).raw();
        assertEquals(2.5, raw.getDouble("width", 0));
        assertEquals(45.0, raw.getDouble("rotation", 0));
        assertEquals(true, raw.getBool("dev", false));
        assertEquals(INVALID, ed.setImageProps("IMG1", DataNode.Compound.empty().with("width", 0.0)));
        assertEquals(INVALID, ed.setImageProps("IMG1", DataNode.Compound.empty().with("mystery", "1")));
    }

    @Test
    void pictureKeysFromTheRealFilesAreKept() {
        Quest q = ed.createQuest(chapter.id, 0, 0);
        chapter.images.add(new Definitions.ChapterImage("IMG2", DataNode.Compound.empty().with("image", "minecraft:x").with("x", 0.0).with("y", 0.0)));
        DataNode.Compound props = DataNode.Compound.empty().with("color", "#8BA66B").with("click_type", "open_uri").with("click_data", "123")
                .with("corner", "true").with("dependency", q.id).with("position_locked", "true").with("text_on_image", "true")
                .with("text_inset", "1").with("text_h_align", "end").with("text_v_align", "end").with("tags", "a, b");
        assertEquals(OK, ed.setImageProps("IMG2", props));
        DataNode.Compound raw = chapter.images.get(0).raw();
        assertEquals(0x8BA66B, raw.getInt("color", 0));
        assertEquals("open_uri", raw.getCompound("click_action").getString("type", ""));
        assertEquals("123", raw.getCompound("click_action").getString("data", ""));
        assertEquals(true, raw.getBool("corner", false));
        assertEquals(q.id, raw.getString("dependency", ""));
        assertEquals(1, raw.getInt("text_inset", 0));
        assertEquals("end", raw.getString("text_h_align", ""));
        assertEquals(2, raw.getList("tags").size());
        assertEquals(INVALID, ed.setImageProps("IMG2", DataNode.Compound.empty().with("dependency", "NOPE")));
        assertEquals(OK, ed.setImageProps("IMG2", DataNode.Compound.empty().with("click_type", "").with("click_data", "")));
        assertEquals(false, chapter.images.get(0).raw().has("click_action"));
    }

    @Test
    void zPositionDefaultsToZeroAndIsKept() {
        Quest q = ed.createQuest(chapter.id, 0, 0);
        assertEquals(0.0, q.z);
        assertEquals(OK, ed.setProp(q.id, "z", "2.5"));
        assertEquals(2.5, q.z);
        assertEquals(2.5, DeepCopy.quest(q, "COPY").z);
        assertEquals(INVALID, ed.setProp(q.id, "z", "5000"));
    }

    @Test
    void questSizeGoesDownToASixteenthAndUpPastFive() {
        Quest q = ed.createQuest(chapter.id, 0, 0);
        assertEquals(OK, ed.setProp(q.id, "size", "0.0625"));
        assertEquals(0.0625, q.size);
        assertEquals(OK, ed.setProp(q.id, "size", "5.1615"));
        assertEquals(5.1615, q.size);
        assertEquals(INVALID, ed.setProp(q.id, "size", "0.03"));
        assertEquals(INVALID, ed.setProp(q.id, "size", "1000"));
    }

    @Test
    void createQuestSnapsAndValidates() {
        Quest q = ed.createQuest(chapter.id, 1.26, -2.74);
        assertEquals(1.5, q.x);
        assertEquals(-2.5, q.y);
        assertEquals("", q.title);
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
    void tasksAndRewardsTakeTagsAndAnIcon() {
        Quest q = ed.createQuest(chapter.id, 0, 0);
        String task = ed.addTask(q.id, "checkmark", DataNode.Compound.empty());
        assertEquals(OK, ed.setTaskProp(task, "tags", "easy, daily"));
        assertEquals(2, book.task(task).params().getList("tags").size());
        assertEquals(OK, ed.setTaskProp(task, "tags", ""));
        assertEquals(false, book.task(task).params().has("tags"), "no tags removes the key");
        assertEquals(INVALID, ed.setTaskProp(task, "tags", "x".repeat(41)));
        DataNode.Compound icon = DataNode.Compound.empty().with("id", "minecraft:diamond");
        assertEquals(OK, ed.setIcon("task", task, icon));
        assertEquals("minecraft:diamond", book.task(task).params().getCompound("icon").getString("id", ""));
        assertEquals(OK, ed.setIcon("task", task, DataNode.Compound.empty()));
        assertEquals(false, book.task(task).params().has("icon"));
        assertEquals(NOT_FOUND, ed.setIcon("task", "nope", icon));
        String kill = ed.addTask(q.id, "kill", DataNode.Compound.empty().with("entity", "minecraft:zombie"));
        assertEquals(OK, ed.setTaskProp(kill, "custom_name", "Bob"));
        assertEquals("Bob", book.task(kill).params().getString("custom_name", ""));
        assertEquals(OK, ed.setTaskProp(kill, "entityTypeTag", "minecraft:boats"));
        assertEquals(OK, ed.setTaskProp(kill, "custom_name", ""));
        assertEquals(false, book.task(kill).params().has("custom_name"));
        assertEquals(INVALID, ed.setTaskProp(task, "custom_name", "x"), "only kill tasks have them");
        String stat = ed.addTask(q.id, "stat", DataNode.Compound.empty().with("stat", "minecraft:mob_kills"));
        assertEquals(OK, ed.setTaskProp(stat, "stat", "minecraft:deaths"));
        assertEquals("minecraft:deaths", book.task(stat).params().getString("stat", ""));
        assertEquals(INVALID, ed.setTaskProp(stat, "stat", "minecraft:not_a_stat"));
        String adv = ed.addTask(q.id, "advancement", DataNode.Compound.empty().with("advancement", "minecraft:story/root"));
        assertEquals(OK, ed.setTaskProp(adv, "criterion", "crafting_table"));
        assertEquals("crafting_table", book.task(adv).params().getString("criterion", ""));
        assertEquals(INVALID, ed.setTaskProp(task, "criterion", "x"), "only advancement tasks");
        String obs = ed.addTask(q.id, "observation", DataNode.Compound.empty().with("to_observe", "minecraft:stone"));
        assertEquals(OK, ed.setTaskProp(obs, "timer", "40"));
        assertEquals(INVALID, ed.setTaskProp(obs, "timer", "-1"));
        assertEquals(OK, ed.setTaskProp(obs, "observation_type", "entity"));
        assertEquals(INVALID, ed.setTaskProp(obs, "observation_type", "sky"));
        assertEquals(OK, ed.setTaskProp(obs, "to_observe", "minecraft:cow"));
        assertEquals("minecraft:cow", book.task(obs).params().getString("to_observe", ""));
        assertEquals(INVALID, ed.setTaskProp(obs, "to_observe", " "), "empty target");
        assertEquals(INVALID, ed.setTaskProp(task, "to_observe", "minecraft:cow"), "only observation tasks");
        String loc = ed.addTask(q.id, "location", DataNode.Compound.empty());
        assertEquals(OK, ed.setTaskProp(loc, "dimension", "minecraft:the_nether"));
        assertEquals(OK, ed.setTaskProp(loc, "ignore_dimension", "true"));
        assertEquals(true, book.task(loc).params().getBool("ignore_dimension", false));
        String reward = ed.addReward(q.id, "xp", DataNode.Compound.empty().with("xp", 5));
        assertEquals(OK, ed.setRewardProp(reward, "tags", "boss"));
        String cmd = ed.addReward(q.id, "command", DataNode.Compound.empty().with("command", "/say Hi, @p!"));
        assertEquals(OK, ed.setRewardProp(cmd, "feedback_message", "hello"));
        assertEquals(INVALID, ed.setRewardProp(reward, "feedback_message", "x"), "only command rewards");
        DataNode.Compound dirt = DataNode.Compound.empty().with("item", DataNode.Compound.empty().with("id", "minecraft:dirt"));
        String itemReward = ed.addReward(q.id, "item", dirt);
        assertEquals(OK, ed.setRewardProp(itemReward, "random_bonus", "4"));
        assertEquals(INVALID, ed.setRewardProp(itemReward, "random_bonus", "-1"));
        assertEquals(INVALID, ed.setRewardProp(cmd, "random_bonus", "2"), "only item rewards");
        assertEquals(OK, ed.setIcon("reward", reward, icon));
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
        assertNull(ed.addTask(q.id, "energy", DataNode.Compound.empty()), "only editor task types");
        assertNull(ed.addTask("nope", "checkmark", DataNode.Compound.empty()));
        assertEquals(OK, ed.removeTask(taskId));
        assertNull(book.task(taskId));
        assertEquals(NOT_FOUND, ed.removeTask(taskId));
    }

    @Test
    void stageInteractionAndEnergyTasks() {
        Quest q = ed.createQuest(chapter.id, 0, 0);
        String stage = ed.addTask(q.id, "gamestage", DataNode.Compound.empty());
        assertEquals("myStage", book.task(stage).params().getString("stage", ""));
        assertEquals(OK, ed.setTaskProp(stage, "stage", "gold"));
        assertEquals(OK, ed.setTaskProp(stage, "team_stage", "true"));
        assertTrue(book.task(stage).params().getBool("team_stage", false));
        assertEquals(INVALID, ed.setTaskProp(stage, "stage", " "));
        assertEquals(INVALID, ed.setTaskProp(stage, "to_interact", "minecraft:chest"), "only interaction tasks");
        String act = ed.addTask(q.id, "interaction", DataNode.Compound.empty());
        assertEquals("minecraft:air", book.task(act).params().getString("to_interact", ""));
        assertEquals("block", book.task(act).params().getString("interaction_type", ""));
        assertEquals(OK, ed.setTaskProp(act, "to_interact", "minecraft:chest"));
        assertEquals(OK, ed.setTaskProp(act, "interaction_type", "entity_type"));
        assertEquals(INVALID, ed.setTaskProp(act, "interaction_type", "wall"));
        String fe = ed.addTask(q.id, "forge_energy", DataNode.Compound.empty().with("value", 500));
        assertEquals(500, book.task(fe).maxProgress());
        assertEquals(OK, ed.setTaskValue(fe, 750));
        assertEquals(750, book.task(fe).maxProgress());

        assertEquals(1000, book.task(fe).maxInput(), "FTB Quests' default Max Input Rate");
        assertFalse(book.task(fe).drainMax(), "Submit All Energy is off by default");
        assertEquals(OK, ed.setTaskProp(fe, "max_input", "250"));
        assertEquals(250, book.task(fe).maxInput());
        assertEquals(OK, ed.setTaskProp(fe, "max_input", "1000"));
        assertFalse(book.task(fe).params().has("max_input"), "the default isn't stored");
        assertEquals(INVALID, ed.setTaskProp(fe, "max_input", "0"));
        assertEquals(INVALID, ed.setTaskProp(fe, "max_input", "lots"));
        assertEquals(OK, ed.setTaskProp(fe, "drain_max", "true"));
        assertTrue(book.task(fe).drainMax());
        assertEquals(OK, ed.setTaskProp(fe, "drain_max", "false"));
        assertFalse(book.task(fe).params().has("drain_max"));
        assertEquals(INVALID, ed.setTaskProp(act, "max_input", "10"), "only Forge Energy tasks");
        assertEquals(INVALID, ed.setTaskProp(act, "drain_max", "true"), "only Forge Energy tasks");
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
