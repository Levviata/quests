/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.DependencyMode;
import com.levviata.reflibquests.model.Definitions.ProgressionMode;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Tristate;

/**
 * The sample folders below are written by hand from the field list in docs/upstream-quest-format.md; they are
 * NOT copies of any real quest pack.
 */
class UpstreamImporterTest {

    @TempDir
    Path dir;

    private void write(String rel, String text) throws IOException {
        Path p = dir.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    private void json5Pack() throws IOException {
        write("data.json5", """
                {
                  version: 13,
                  progression_mode: "flexible",
                  fallback_locale: "en_us",
                  detection_delay: 20,
                }
                """);
        write("chapter_groups.json5", """
                { chapter_groups: [ { id: "00000000000000AA" } ] }
                """);
        write("chapters/getting_started.json5", """
                {
                  id: "0000000000000001",
                  group: "",
                  order_index: 1,
                  filename: "getting_started",
                  icon: { id: "minecraft:book" },
                  require_sequential_tasks: true,
                  quests: [
                    {
                      id: "00000000000000A1", x: 0.0, y: 0.0,
                      tasks: [ { id: "00000000000000B1", type: "item", item: { id: "minecraft:stone" }, count: 8 } ],
                      rewards: [ { id: "00000000000000C1", type: "xp", xp: 20 }, { id: "00000000000000C2", item: { id: "minecraft:apple" }, count: 3 } ],
                    },
                    {
                      id: "00000000000000A2", x: 2.5, y: 1.0, shape: "circle", optional: false,
                      dependencies: ["00000000000000A1", "#00000000000000A1"],
                      dependency_requirement: "one_started",
                      min_required_dependencies: 1,
                      can_repeat: true, repeat_cooldown: 100,
                      hide_details_until_startable: true,
                      invisible_until_tasks: 1,
                      tasks: [ { id: "00000000000000B2", type: "ftbquests:checkmark" }, { id: "00000000000000B3", type: "kill", entity: "minecraft:zombie", value: 10, optional_task: true } ],
                    },
                  ],
                  quest_links: [ { id: "00000000000000D1", linked_quest: "00000000000000A1", x: 5, y: 5 } ],
                  images: [ { id: "00000000000000E1", x: 0, y: 0, width: 4, height: 4, rotation: 0, image: "minecraft:textures/gui/x.png" } ],
                }
                """);
        write("reward_tables/loot.json5", """
                {
                  id: "00000000000000F1", order_index: 0, empty_weight: 2.0, loot_size: 1,
                  rewards: [ { id: "00000000000000F2", item: { id: "minecraft:diamond" }, weight: 5 }, { id: "00000000000000F3", type: "xp", xp: 5 } ],
                }
                """);
        write("lang/en_us/chapter.json5", """
                { "chapter.0000000000000001.title": "Getting started", "chapter.0000000000000001.chapter_subtitle": ["Line one", "Line two"] }
                """);
        write("lang/en_us/quest.json5", """
                {
                  "quest.00000000000000A1.title": "Gather stone",
                  "quest.00000000000000A1.quest_subtitle": "Dig dig dig",
                  "quest.00000000000000A1.quest_desc": ["First line", "Second line"],
                  "quest.00000000000000A2.title": "Next step",
                }
                """);
        write("lang/de_de/quest.json5", """
                { "quest.00000000000000A1.title": "Stein sammeln" }
                """);
    }

    @Test
    void importsLatestGeneration() throws IOException {
        json5Pack();
        UpstreamImporter.Result r = UpstreamImporter.load(dir);
        assertEquals(java.util.List.of(), r.problems());
        assertEquals(2, r.quests());
        assertEquals(3, r.tasks());
        assertEquals(2, r.rewards() - 2, "two quest rewards plus two table rewards are read");

        var book = r.book();
        assertEquals(ProgressionMode.FLEXIBLE, book.progressionMode);
        assertEquals(1, book.groups().size());
        assertEquals("00000000000000AA", book.groups().get(0).id);

        Chapter c = book.chapter("0000000000000001");
        assertNotNull(c);
        assertEquals("Getting started", c.title);
        assertEquals(java.util.List.of("Line one", "Line two"), c.subtitle);
        assertTrue(c.requireSequentialTasks);
        assertEquals("minecraft:book", c.icon.getString("id", null));
        assertEquals(1, c.links.size());
        assertEquals("00000000000000A1", c.links.get(0).linkedQuest());
        assertEquals(1, c.images.size());

        Quest a1 = book.quest("00000000000000A1");
        assertEquals("Gather stone", a1.title);
        assertEquals("Dig dig dig", a1.subtitle);
        assertEquals(java.util.List.of("First line", "Second line"), a1.description);
        assertEquals(8, a1.tasks.get(0).maxProgress());
        assertEquals("item", a1.tasks.get(0).type());
        assertEquals("minecraft:stone", a1.tasks.get(0).params().getCompound("item").getString("id", null));
        assertEquals("xp", a1.rewards.get(0).type());
        assertEquals("item", a1.rewards.get(1).type(), "item is the default reward type");
        assertEquals(3, a1.rewards.get(1).params().getInt("count", 0));

        Quest a2 = book.quest("00000000000000A2");
        assertEquals(2.5, a2.x);
        assertEquals("circle", a2.shape);
        assertEquals(DependencyMode.ONE_STARTED, a2.dependencyMode);
        assertEquals(java.util.List.of("00000000000000A1", "00000000000000A1"), a2.dependencies, "hash prefix is dropped");
        assertEquals(1, a2.minRequiredDependencies);
        assertEquals(Tristate.TRUE, a2.canRepeat);
        assertEquals(Tristate.TRUE, a2.hideDetailsUntilStartable);
        assertEquals(Tristate.DEFAULT, a2.requireSequentialTasks);
        assertEquals(100, a2.repeatCooldown);
        assertEquals("checkmark", a2.tasks.get(0).type(), "namespace is dropped from type names");
        assertTrue(a2.tasks.get(1).optionalTask());
        assertEquals(10, a2.tasks.get(1).maxProgress());
        assertEquals("Next step", a2.title);

        assertEquals(1, book.tables().size());
        var table = book.tables().get("00000000000000F1");
        assertEquals(2, table.entries.size());
        assertEquals(5.0f, table.entries.get(0).weight());
        assertEquals(1.0f, table.entries.get(1).weight());
        assertEquals(2.0f, table.emptyWeight);

        assertEquals(2, book.translations().size());
        assertEquals("Stein sammeln", ((com.levviata.reflib.api.data.DataNode.Str)
                book.translations().get("de_de").get("quest.00000000000000A1.title")).value());
    }

    @Test
    void importsOlderSnbtGeneration() throws IOException {
        write("data.snbt", "{ version: 11, progression_mode: \"linear\" }");
        write("chapters/ch.snbt", """
                {
                  id: "0000000000000010"
                  group: ""
                  order_index: 0
                  filename: "ch"
                  title: "Old chapter"
                  quests: [{
                    id: "0000000000000011"
                    x: 1.0d
                    y: -2.0d
                    title: "Old quest"
                    description: ["old text"]
                    optional: 1b
                    tasks: [{ id: "0000000000000012", type: "item", item: "minecraft:log", count: 4L }]
                    rewards: [{ id: "0000000000000013", type: "xp_levels", xp_levels: 2 }]
                  }]
                }
                """);
        UpstreamImporter.Result r = UpstreamImporter.load(dir);
        assertEquals(java.util.List.of(), r.problems());
        Quest q = r.book().quest("0000000000000011");
        assertNotNull(q);
        assertEquals("Old quest", q.title);
        assertEquals("Old chapter", r.book().chapter("0000000000000010").title);
        assertEquals(-2.0, q.y);
        assertTrue(q.optional, "1b reads as true");
        assertEquals(4, q.tasks.get(0).maxProgress());
        assertEquals("xp_levels", q.rewards.get(0).type());
    }

    @Test
    void readsTranslationsInSubfoldersAndToleratesDuplicateKeys() throws IOException {
        write("data.snbt", "{ version: 13 }");
        write("chapters/ch.snbt", "{ id: \"0000000000000010\" group: \"\" order_index: 0 filename: \"ch\" quests: [{ id: \"0000000000000011\" x: 0.0d y: 0.0d tasks: [{ id: \"0000000000000012\", type: \"checkmark\" }] }] }");
        write("lang/en_us/chapter.snbt", "{ chapter.0000000000000010.title: \"Top level\" }");
        write("lang/en_us/chapters/ch.snbt", "{ quest.0000000000000011.title: \"Nested\" quest.0000000000000011.title: \"Nested again\" quest.0000000000000011.quest_desc: [\"a\"] }");
        UpstreamImporter.Result r = UpstreamImporter.load(dir);
        assertEquals(java.util.List.of(), r.problems());
        assertEquals("Top level", r.book().chapter("0000000000000010").title);
        assertEquals("Nested again", r.book().quest("0000000000000011").title, "last duplicate wins");
        assertEquals(java.util.List.of("a"), r.book().quest("0000000000000011").description);
    }

    @Test
    void readsOneTranslationFilePerLanguage() throws IOException {
        write("data.snbt", "{ version: 13 }");
        write("chapters/ch.snbt", "{ id: \"0000000000000010\" group: \"\" order_index: 0 filename: \"ch\" quests: [{ id: \"0000000000000011\" x: 0.0d y: 0.0d tasks: [{ id: \"0000000000000012\", type: \"checkmark\" }] }] }");
        write("lang/en_us.snbt", "{ chapter.0000000000000010.title: \"From the single file\" quest.0000000000000011.title: \"Quest title\" }");
        write("lang/en_us/chapter.snbt_merged", "{ chapter.0000000000000010.title: \"ignored split file\" }");
        UpstreamImporter.Result r = UpstreamImporter.load(dir);
        assertEquals(java.util.List.of(), r.problems());
        assertEquals("From the single file", r.book().chapter("0000000000000010").title);
        assertEquals("Quest title", r.book().quest("0000000000000011").title);
    }

    @Test
    void typedArraysInQuestDataLoadInsteadOfFailingTheChapter() throws IOException {
        write("data.snbt", "{ version: 13 }");
        write("chapters/ch.snbt", "{ id: \"0000000000000010\" group: \"\" order_index: 0 filename: \"ch\" quests: [{ id: \"0000000000000011\" x: 0.0d y: 0.0d tasks: [{ id: \"0000000000000012\", type: \"item\", item: { id: \"minecraft:player_head\", components: { owner: [I; 1, 2, 3, 4] } } }] }] }");
        UpstreamImporter.Result r = UpstreamImporter.load(dir);
        assertEquals(java.util.List.of(), r.problems());
        assertEquals(1, r.quests());
    }

    @Test
    void badFilesAreReportedAndTheRestStillLoads() throws IOException {
        json5Pack();
        write("chapters/broken.json5", "{ id: \"0000000000000002\", quests: [ { ");
        write("chapters/noid.json5", "{ filename: \"noid\", quests: [] }");
        write("chapters/noquestid.json5", "{ id: \"0000000000000003\", quests: [ { x: 1, y: 2 } ] }");
        UpstreamImporter.Result r = UpstreamImporter.load(dir);
        assertEquals(2, r.quests(), "good chapter still imported");
        assertTrue(r.problems().stream().anyMatch(p -> p.contains("broken.json5")), r.problems().toString());
        assertTrue(r.problems().stream().anyMatch(p -> p.contains("chapter without id")));
        assertTrue(r.problems().stream().anyMatch(p -> p.contains("quest without id")));
    }

    @Test
    void missingFolderYieldsEmptyBookNotAnException() {
        UpstreamImporter.Result r = UpstreamImporter.load(dir.resolve("does-not-exist"));
        assertEquals(0, r.quests());
        assertFalse(r.problems().isEmpty());
    }

    @Test
    void idNormalization() {
        assertEquals("00AB", Ids.normalize("#00ab"));
        assertEquals("", Ids.normalize("-"));
        assertEquals("", Ids.normalize(null));
        assertEquals("my_quest", Ids.normalize("my_quest"));
        assertEquals("item", Ids.stripNamespace("ftbquests:item"));
    }
}
