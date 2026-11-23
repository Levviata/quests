/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.DependencyMode;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Tristate;
import com.levviata.reflibquests.model.QuestBook;
import com.levviata.reflibquests.model.QuestRules;
import com.levviata.reflibquests.model.TeamProgress;

class LegacyImporterTest {

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private Path folder(Path root) throws IOException {
        write(root.resolve("file.snbt"), "{\n\tversion: 9,\n\tdefault_quest_shape: \"circle\"\n}\n");
        write(root.resolve("chapters/index.snbt"), "{ index: [\"bbbb2222\", \"aaaa1111\"] }");
        write(root.resolve("chapters/aaaa1111/chapter.snbt"), "{ title: \"First\", icon: \"minecraft:apple\", default_quest_shape: \"\","
                + " images: [{ x: 1.0d, y: 2.0d, width: 2.0d, height: 3.0d, rotation: 0.0d, image: \"a:b.png\", hover: [\"Wiki\", \"more\"], click: \"https://x\", dev: false }] }");
        write(root.resolve("chapters/bbbb2222/chapter.snbt"), "{ title: \"Second\" }");
        write(root.resolve("chapters/aaaa1111/0077d27c.snbt"), "{ x: -19.0d, y: 6.5d, description: \"Short\", text: [\"One\", \"\", \"Two\"],"
                + " tasks: [{ uid: \"2903666d\", type: \"item\", items: [{ item: \"hbm:cinnabar 1 6\" }], count: 32L, ignore_damage: false, consume_items: true }],"
                + " rewards: [{ uid: \"7c958f36\", type: \"loot\", table: 1 }, { uid: \"a69a664c\", type: \"random\", table: 0 },"
                + " { uid: \"1a487fdb\", type: \"item\", item: \"hbm:ingot_copper\", count: 8 }, { uid: \"0b3e864c\", type: \"xp\", xp: 50 },"
                + " { uid: \"23da2cad\", type: \"command\", auto: \"invisible\", command: \"/ftbquests change_progress complete @p 0f29a16e\" }] }");
        write(root.resolve("chapters/aaaa1111/0f29a16e.snbt"), "{ x: 1.0d, y: 1.0d, hide: true, or_tasks: true, dependencies: [\"0077d27c\"], dependency_requirement: \"one_completed\","
                + " tasks: [{ uid: \"11111111\", type: \"checkmark\" }, { uid: \"22222222\", type: \"dimension\", dim: 1 },"
                + " { uid: \"33333333\", type: \"item\", items: [{ item: \"minecraft:apple\" }, { item: \"minecraft:bread\" }] }] }");
        write(root.resolve("chapters/bbbb2222/5555aaaa.snbt"), "{ x: 0.0d, y: 0.0d, tasks: [{ uid: \"44444444\", type: \"observation\", match_type: \"block_id\", match: \"minecraft:stone\", timer: 0L }] }");
        write(root.resolve("reward_tables/index.snbt"), "{ index: [\"cccc0001\", \"cccc0002\"] }");
        write(root.resolve("reward_tables/cccc0001.snbt"), "{ title: \"Fuel\", loot_size: 2, use_title: true, rewards: [{ item: \"minecraft:coal\", count: 32, weight: 4 }, { item: \"minecraft:lava_bucket\" }] }");
        write(root.resolve("reward_tables/cccc0002.snbt"), "{ title: \"Other\", loot_size: 1, rewards: [] }");
        return root;
    }

    @Test
    void aChapterFolderTheIndexDoesNotListIsSkipped(@TempDir Path root) throws IOException {
        folder(root);
        write(root.resolve("chapters/cccc3333/chapter.snbt"), "{ title: \"Orphan\" }");
        write(root.resolve("chapters/cccc3333/7777bbbb.snbt"), "{ x: 0.0d, y: 0.0d, tasks: [{ uid: \"55555555\", type: \"checkmark\" }] }");
        UpstreamImporter.Result r = LegacyImporter.load(root);
        assertEquals(2, r.book().chapters().size(), "only the chapters the index lists are loaded");
        assertTrue(r.book().chapters().stream().noneMatch(c -> c.title.equals("Orphan")));
        assertTrue(r.problems().stream().anyMatch(p -> p.contains("cccc3333") && p.contains("not listed")));
    }

    @Test
    void theOlderLayoutIsRecognisedOnlyUntilItIsConverted(@TempDir Path root) throws IOException {
        assertFalse(LegacyImporter.applies(root));
        folder(root);
        assertTrue(LegacyImporter.applies(root));
        write(root.resolve("data.json5"), "{}");
        assertFalse(LegacyImporter.applies(root), "once the newer files exist they are the ones that are read");
    }

    @Test
    void aChapterGroupIsTheChapterItIsListedUnder(@TempDir Path root) throws IOException {
        folder(root);

        write(root.resolve("chapters/aaaa1111/chapter.snbt"), "{ title: \"First\", group: " + (-1145363934) + " }");
        write(root.resolve("chapters/index.snbt"), "{ index: [\"bbbb2222\", \"aaaa1111\", \"cccc3333\"] }");
        write(root.resolve("chapters/cccc3333/chapter.snbt"), "{ title: \"Third\", group: 123 }");
        write(root.resolve("chapters/cccc3333/aaaaaaaa.snbt"), "{ x: 0.0d, y: 0.0d, tasks: [{ uid: \"55555555\", type: \"checkmark\" }] }");
        QuestBook book = UpstreamImporter.load(root).book();
        assertEquals(2, book.groups().size());
        var parentGroup = book.groups().get(0);
        assertEquals("BBBB2222", parentGroup.id);
        assertEquals("Second", parentGroup.title, "it takes the parent chapter's name");
        assertEquals("BBBB2222", parentGroup.raw.getString("chapter", ""));
        assertEquals("BBBB2222", book.chapter("AAAA1111").groupId);
        assertEquals("", book.chapter("BBBB2222").groupId, "the parent chapter itself is not inside its group");
        var plain = book.groups().get(1);
        assertEquals("Group 1", plain.title, "a number that names no chapter becomes a plain group");
        assertEquals(plain.id, book.chapter("CCCC3333").groupId);
    }

    @Test
    void chaptersFollowTheIndexAndKeepTheirDetails(@TempDir Path root) throws IOException {
        QuestBook book = UpstreamImporter.load(folder(root)).book();
        assertEquals(2, book.chapters().size());
        assertEquals("Second", book.chapters().get(0).title, "the index decides the order");
        Chapter first = book.chapters().get(1);
        assertEquals("First", first.title);
        assertEquals("minecraft:apple", first.icon.getString("id", ""));
        assertEquals(1, first.images.size());
        assertEquals(2, first.images.get(0).raw().getList("hover").size(), "every line of the hover text is kept");
        assertEquals("Wiki", ((com.levviata.reflib.api.data.DataNode.Str) first.images.get(0).raw().getList("hover").get(0)).value());
        assertEquals("https://x", first.images.get(0).raw().getString("click", ""));
        assertEquals(2, first.quests.size());
    }

    @Test
    void questsKeepTextDependenciesAndFlags(@TempDir Path root) throws IOException {
        QuestBook book = UpstreamImporter.load(folder(root)).book();
        Quest a = book.quest("0077D27C");
        assertNotNull(a);
        assertEquals("Short", a.subtitle);
        assertEquals(List.of("One", "", "Two"), a.description);
        assertEquals(-19.0, a.x);
        Quest b = book.quest("0F29A16E");
        assertEquals(List.of("0077D27C"), b.dependencies);
        assertEquals(DependencyMode.ONE_COMPLETED, b.dependencyMode);
        assertEquals(Tristate.TRUE, b.hideUntilDepsComplete);
        assertTrue(b.orTasks);
    }

    @Test
    void tasksBecomeTheCurrentTaskTypes(@TempDir Path root) throws IOException {
        QuestBook book = UpstreamImporter.load(folder(root)).book();
        var item = book.quest("0077D27C").tasks.get(0);
        assertEquals("item", item.type());
        assertEquals("hbm:cinnabar", item.params().getCompound("item").getString("id", ""));
        assertEquals(6, item.params().getCompound("item").getInt("damage", -1));
        assertEquals(32, item.maxProgress());
        assertTrue(item.params().getBool("consume_items", false));
        Quest b = book.quest("0F29A16E");
        assertEquals("checkmark", b.tasks.get(0).type());
        assertEquals("1", b.tasks.get(1).params().getString("dimension", ""));
        assertTrue(b.tasks.get(2).params().getCompound("item").getString("id", "").contains("smart_filter"),
                "several items make one any-of-these filter");
        var observe = book.quest("5555AAAA").tasks.get(0);
        assertEquals("block", observe.params().getString("observation_type", ""));
        assertEquals("minecraft:stone", observe.params().getString("to_observe", ""));
    }

    @Test
    void anOreDictionaryFilterItemBecomesAnOreFilter(@TempDir Path root) throws IOException {
        folder(root);
        write(root.resolve("chapters/bbbb2222/6666bbbb.snbt"), "{ x: 0.0d, y: 0.0d, tasks: [{ uid: \"77777777\", type: \"item\","
                + " items: [{ id: \"itemfilters:filter\", tag: { display: { Name: \"Any stoneResource\" } },"
                + " ForgeCaps: { Parent: { id: \"ore\", data: \"stoneResource\" } } }] }] }");
        var task = UpstreamImporter.load(root).book().quest("6666BBBB").tasks.get(0);
        var item = task.params().getCompound("item");
        assertEquals("ftbfiltersystem:smart_filter", item.getString("id", ""));
        assertEquals("ore(stoneResource)", item.getCompound("components").getString("ftbfiltersystem:filter", ""));
        assertEquals("Any stoneResource", task.params().getString("title", ""), "the name the filter was given");
    }

    @Test
    void anItemKeepsItsExtraData(@TempDir Path root) throws IOException {
        folder(root);
        write(root.resolve("chapters/bbbb2222/7777cccc.snbt"), "{ x: 0.0d, y: 0.0d, icon: { id: \"minecraft:spawn_egg\", tag: { EntityTag: { id: \"minecraft:villager\" } } },"
                + " tasks: [{ uid: \"88888888\", type: \"item\", items: [{ id: \"minecraft:spawn_egg\", tag: { EntityTag: { id: \"minecraft:villager\" } } }] },"
                + " { uid: \"99999999\", type: \"item\", ignore_nbt: 1b, items: [{ id: \"minecraft:spawn_egg\", tag: { EntityTag: { id: \"minecraft:cow\" } } }] }],"
                + " rewards: [{ uid: \"aaaaaaa1\", type: \"item\", item: { id: \"minecraft:spawn_egg\", tag: { EntityTag: { id: \"hbm:entity_elder_one\" } } } }] }");
        Quest q = UpstreamImporter.load(root).book().quest("7777CCCC");
        assertEquals("minecraft:villager", q.icon.getCompound("nbt").getCompound("EntityTag").getString("id", ""));
        assertEquals("minecraft:villager", q.tasks.get(0).params().getCompound("item").getCompound("nbt").getCompound("EntityTag").getString("id", ""));
        assertFalse(q.tasks.get(1).params().getCompound("item").has("nbt"), "a task that ignores nbt does not ask for any");
        assertEquals("hbm:entity_elder_one", q.rewards.get(0).params().getCompound("item").getCompound("nbt").getCompound("EntityTag").getString("id", ""));
    }

    @Test
    void rewardsAndTablesAreLinkedByTheirPosition(@TempDir Path root) throws IOException {
        QuestBook book = UpstreamImporter.load(folder(root)).book();
        Quest a = book.quest("0077D27C");
        assertEquals("CCCC0002", a.rewards.get(0).params().getString("table_id", ""), "table 1 is the second one in the index");
        assertEquals("CCCC0001", a.rewards.get(1).params().getString("table_id", ""));
        assertEquals("loot", a.rewards.get(0).type());
        assertEquals(8, a.rewards.get(2).params().getInt("count", 0));
        assertEquals(50, a.rewards.get(3).params().getInt("xp", 0));
        var fuel = book.tables().get("CCCC0001");
        assertEquals("Fuel", fuel.title);
        assertEquals(2, fuel.lootSize);
        assertEquals(2, fuel.entries.size());
        assertEquals(4f, fuel.entries.get(0).weight());
        assertEquals(1f, fuel.entries.get(1).weight(), "an entry without a weight counts as 1");
        assertEquals(32, fuel.entries.get(0).reward().params().getInt("count", 0));
    }

    @Test
    void theUnlockCommandIsWrittenForThisMod(@TempDir Path root) throws IOException {
        QuestBook book = UpstreamImporter.load(folder(root)).book();
        var command = book.quest("0077D27C").rewards.get(4);
        assertEquals("/rquests complete 0F29A16E @p", command.params().getString("command", ""));
        assertEquals(2, command.params().getInt("permission_level", 0));
        assertEquals("invisible", command.params().getString("auto", ""));
    }

    @Test
    void aQuestWithOrTasksCompletesWithAnyTask(@TempDir Path root) throws IOException {
        QuestBook book = UpstreamImporter.load(folder(root)).book();
        Quest b = book.quest("0F29A16E");
        b.dependencies.clear();
        book.reindex();
        TeamProgress p = new TeamProgress(UUID.randomUUID());
        assertFalse(QuestRules.allRequiredTasksDone(p, b));
        new com.levviata.reflibquests.model.ProgressEngine(book).setTaskProgress(p, b.tasks.get(0).id(), 1, 1000);
        assertTrue(QuestRules.allRequiredTasksDone(p, b), "one finished task is enough");
        assertTrue(p.isCompleted(b.id), "and the quest completes");
    }
}
