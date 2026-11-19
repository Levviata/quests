/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonParser;
import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.QuestBook;

/** Sample packs are hand-written from docs/upstream-quest-format.md, not copied from any real pack. */
class ExporterTest {

    @TempDir
    Path dir;

    private void write(Path root, String rel, String text) throws IOException {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    private Path samplePack(String name) throws IOException {
        Path root = dir.resolve(name);
        write(root, "data.json5", "{ version: 13, progression_mode: \"flexible\", fallback_locale: \"en_us\", detection_delay: 7, default_consume_items: true, grid_scale: 0.75 }");
        write(root, "chapter_groups.json5", "{ chapter_groups: [ { id: \"00000000000000AA\" } ] }");
        write(root, "chapters/first.json5", """
                {
                  id: "0000000000000001", group: "", order_index: 0, filename: "first",
                  icon: { id: "minecraft:book" }, require_sequential_tasks: true, tags: ["intro"],
                  quests: [
                    { id: "00000000000000A1", x: 0.5, y: -1.0, shape: "square", size: 1.5, tags: ["t1"], hide_lock_icon: true,
                      tasks: [ { id: "00000000000000B1", type: "item", item: { id: "minecraft:stone" }, count: 8, consume_items: false } ],
                      rewards: [ { id: "00000000000000C1", type: "xp", xp: 20 }, { id: "00000000000000C2", item: { id: "minecraft:apple" }, count: 3 } ] },
                    { id: "00000000000000A2", x: 2, y: 1, dependencies: ["00000000000000A1"], dependency_requirement: "one_started",
                      min_required_dependencies: 1, can_repeat: true, repeat_cooldown: 100, hide_details_until_startable: true,
                      optional: true, invisible_until_tasks: 1,
                      tasks: [ { id: "00000000000000B2", type: "checkmark" }, { id: "00000000000000B3", type: "kill", entity: "minecraft:zombie", value: 10, optional_task: true } ] }
                  ],
                  quest_links: [ { id: "00000000000000D1", linked_quest: "00000000000000A1", x: 5, y: 5 } ],
                  images: [ { id: "00000000000000E1", x: 0, y: 0, width: 4, height: 4, rotation: 0, image: "minecraft:textures/gui/x.png" } ]
                }
                """);
        write(root, "chapters/second.json5", "{ id: \"0000000000000002\", group: \"00000000000000AA\", order_index: 0, filename: \"second\", quests: [] }");
        write(root, "reward_tables/loot.json5", """
                { id: "00000000000000F1", order_index: 0, empty_weight: 2.0, loot_size: 1, hide_tooltip: false,
                  rewards: [ { id: "00000000000000F2", item: { id: "minecraft:diamond" }, weight: 5 }, { id: "00000000000000F3", type: "xp", xp: 5 } ] }
                """);
        write(root, "lang/en_us/chapter.json5", "{ \"chapter.0000000000000001.title\": \"First\", \"chapter.0000000000000001.chapter_subtitle\": [\"a\", \"b\"], \"chapter.0000000000000002.title\": \"Second\" }");
        write(root, "lang/en_us/quest.json5", """
                { "quest.00000000000000A1.title": "Gather stone", "quest.00000000000000A1.quest_subtitle": "dig",
                  "quest.00000000000000A1.quest_desc": ["line 1", "line \\"2\\""], "quest.00000000000000A2.title": "Next" }
                """);
        write(root, "lang/en_us/reward_table.json5", "{ \"reward_table.00000000000000F1.title\": \"Loot\" }");
        write(root, "lang/de_de/quest.json5", "{ \"quest.00000000000000A1.title\": \"Stein\" }");
        return root;
    }

    private static DataNode.Compound wire(QuestBook b) {
        return BookCodec.write(b, DataNode.Compound.empty());
    }

    @Test
    void importExportImportKeepsEverythingTheModelUnderstands() throws IOException {
        Path src = samplePack("src");
        UpstreamImporter.Result first = UpstreamImporter.load(src);
        assertEquals(List.of(), first.problems());
        Path out = dir.resolve("out");
        UpstreamExporter.export(first.book(), out);
        UpstreamImporter.Result second = UpstreamImporter.load(out);
        assertEquals(List.of(), second.problems());

        // book-wide settings are compared separately: the exporter adds the required keys that were missing
        assertEquals(wire(first.book()).without("settings"), wire(second.book()).without("settings"), "book content is identical after a round trip");
        for (var e : first.book().settings.entries().entrySet()) {
            assertEquals(e.getValue(), second.book().settings.get(e.getKey()), "setting " + e.getKey() + " is kept");
        }
        assertEquals(first.book().translations(), second.book().translations(), "all locales survive");
        assertEquals(first.book().settings.getInt("detection_delay", 0), second.book().settings.getInt("detection_delay", 0));
        assertEquals(0.75, second.book().settings.getDouble("grid_scale", 0));

        // values the model does not interpret are kept
        Chapter c = second.book().chapter("0000000000000001");
        assertEquals(1, c.links.size());
        assertEquals(1, c.images.size());
        assertEquals("minecraft:textures/gui/x.png", c.images.get(0).raw().getString("image", null));
        assertEquals("t1", second.book().quest("00000000000000A1").raw.getList("tags").get(0) instanceof DataNode.Str s ? s.value() : null);
        assertTrue(second.book().quest("00000000000000A1").raw.getBool("hide_lock_icon", false));
    }

    @Test
    void writtenFilesAreStrictJsonAndNamedAsUpstreamExpects() throws IOException {
        UpstreamExporter.export(UpstreamImporter.load(samplePack("src")).book(), dir.resolve("out"));
        Path out = dir.resolve("out");
        assertTrue(Files.exists(out.resolve("data.json5")));
        assertTrue(Files.exists(out.resolve("chapter_groups.json5")));
        assertTrue(Files.exists(out.resolve("chapters/first.json5")));
        assertTrue(Files.exists(out.resolve("reward_tables/loot.json5")));
        assertTrue(Files.exists(out.resolve("lang/en_us/quest.json5")));
        try (Stream<Path> s = Files.walk(out)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".json5")).toList()) {
                String text = Files.readString(p);
                assertDoesNotThrow(() -> JsonParser.parseString(text), p + " must be valid JSON (commas, quoted keys)");
            }
        }
        var data = DataFilesRead.read(out.resolve("data.json5"));
        assertEquals(13, data.getInt("version", 0));
        for (String required : List.of("default_reward_team", "default_consume_items", "default_autoclaim_rewards",
                "default_quest_shape", "default_quest_disable_jei", "drop_loot_crates")) {
            assertTrue(data.has(required), "required book setting " + required);
        }
    }

    @Test
    void titlesMoveToLanguageFilesAndInlineOnesAreNotDuplicated() throws IOException {
        Path root = dir.resolve("old");
        write(root, "data.snbt", "{ version: 11 }");
        write(root, "chapters/ch.snbt", "{ id: \"0000000000000010\" group: \"\" order_index: 0 filename: \"ch\" title: \"Old chapter\" quests: [{ id: \"0000000000000011\" x: 1.0d y: 2.0d title: \"Old quest\" description: [\"old text\"] tasks: [{ id: \"0000000000000012\", type: \"checkmark\" }] }] }");
        QuestBook book = UpstreamImporter.load(root).book();
        Path out = dir.resolve("migrated");
        UpstreamExporter.export(book, out);
        String chapter = Files.readString(out.resolve("chapters/ch.json5"));
        assertFalse(chapter.contains("Old quest"), "titles are not written inline");
        String lang = Files.readString(out.resolve("lang/en_us/quest.json5"));
        assertTrue(lang.contains("Old quest"));
        assertTrue(lang.contains("old text"));
        assertEquals("Old quest", UpstreamImporter.load(out).book().quest("0000000000000011").title);
    }

    @Test
    void removedObjectsAreRenamedNotDeletedAndDoNotComeBack() throws IOException {
        Path out = dir.resolve("out");
        QuestBook book = UpstreamImporter.load(samplePack("src")).book();
        UpstreamExporter.export(book, out);
        book.chapters().removeIf(c -> c.id.equals("0000000000000002"));
        book.chapter("0000000000000001").quests.removeIf(q -> q.id.equals("00000000000000A2"));
        book.reindex();
        UpstreamExporter.export(book, out);
        assertFalse(Files.exists(out.resolve("chapters/second.json5")));
        assertTrue(Files.exists(out.resolve("chapters/second.json5.removed")), "kept as a backup");
        QuestBook again = UpstreamImporter.load(out).book();
        assertNull(again.chapter("0000000000000002"));
        assertNull(again.quest("00000000000000A2"));
        assertFalse(again.translations().get("en_us").containsKey("quest.00000000000000A2.title"), "its texts are gone too");
        assertEquals("Stein", ((DataNode.Str) again.translations().get("de_de").get("quest.00000000000000A1.title")).value(), "other locales are kept");
    }

    @Test
    void newObjectsWithoutFileNamesGetSafeUniqueOnes() throws IOException {
        QuestBook book = new QuestBook();
        for (String title : List.of("My Chapter!", "my chapter?", "")) {
            Chapter c = new Chapter("ID" + book.chapters().size());
            c.title = title;
            Quest q = new Quest("Q" + book.chapters().size());
            c.quests.add(q);
            book.chapters().add(c);
        }
        book.reindex();
        UpstreamExporter.export(book, dir.resolve("out"));
        try (Stream<Path> s = Files.list(dir.resolve("out/chapters"))) {
            assertEquals(3, s.count(), "three distinct files");
        }
        assertEquals(3, UpstreamImporter.load(dir.resolve("out")).book().chapters().size());
    }

    private static final class DataFilesRead {
        static DataNode.Compound read(Path p) throws IOException {
            return com.levviata.reflib.api.data.DataFiles.read(p);
        }
    }
}
