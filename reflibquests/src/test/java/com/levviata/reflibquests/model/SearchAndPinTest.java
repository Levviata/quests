/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflib.api.data.Snbt;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.model.Definitions.Tristate;

class SearchAndPinTest {

    private QuestBook book;
    private TeamProgress p = new TeamProgress(UUID.randomUUID());

    private Quest quest(Chapter c, String id, String title, String... desc) {
        Quest q = new Quest(id);
        q.title = title;
        q.description.addAll(List.of(desc));
        q.tasks.add(new Task("T" + id, "checkmark", DataNode.Compound.empty()));
        c.quests.add(q);
        return q;
    }

    private void setUp() {
        book = new QuestBook();
        Chapter a = new Chapter("A");
        a.title = "Mining";
        a.order = 0;
        Chapter b = new Chapter("B");
        b.title = "Farming";
        b.order = 1;
        quest(a, "A1", "Stone age", "Dig some stone");
        quest(a, "A2", "Iron tools", "Smelt iron and make a pickaxe");
        quest(b, "B1", "Wheat fields", "Plant wheat near water");
        quest(b, "B2", "Bread", "Bake bread from wheat");
        book.chapters().add(a);
        book.chapters().add(b);
        book.reindex();
    }

    @Test
    void blankQueryAndLimit() {
        setUp();
        assertTrue(QuestSearch.search(book, p, "   ", 10).isEmpty());
        assertTrue(QuestSearch.search(book, p, "stone", 0).isEmpty());
        assertEquals(1, QuestSearch.search(book, p, "wheat", 1).size());
    }

    @Test
    void matchesTitleDescriptionAndIdCaseInsensitively() {
        setUp();
        assertEquals(List.of("A1"), ids(QuestSearch.search(book, p, "STONE AGE", 10)));
        assertEquals(List.of("A2"), ids(QuestSearch.search(book, p, "pickaxe", 10)));
        assertEquals(List.of("B2"), ids(QuestSearch.search(book, p, "b2", 10)));
        assertTrue(QuestSearch.search(book, p, "nothing here", 10).isEmpty());
    }

    @Test
    void allWordsMustMatch() {
        setUp();
        assertEquals(List.of("B1"), ids(QuestSearch.search(book, p, "wheat water", 10)));
        assertEquals(List.of("B1", "B2"), ids(QuestSearch.search(book, p, "wheat", 10)));
    }

    @Test
    void titleMatchesRankFirst() {
        setUp();

        assertEquals("B1", QuestSearch.search(book, p, "wheat", 10).get(0).questId());

        QuestSearch.Result r = QuestSearch.search(book, p, "iron", 10).get(0);
        assertEquals("Mining", r.chapterTitle());
        assertEquals("Iron tools", r.title());
    }

    @Test
    void hiddenQuestsAreNotSearchable() {
        setUp();
        Quest secret = quest(book.chapter("A"), "A3", "Secret cave", "Find the secret");
        secret.invisible = true;
        Quest locked = quest(book.chapter("A"), "A4", "Hidden details", "locked text");
        locked.dependencies.add("A1");
        locked.hideDetailsUntilStartable = Tristate.TRUE;
        book.reindex();
        assertTrue(QuestSearch.search(book, p, "secret", 10).isEmpty());
        assertTrue(QuestSearch.search(book, p, "locked text", 10).isEmpty(), "details are hidden until startable");
        new ProgressEngine(book).forceComplete(p, "A1", 1);
        assertEquals(List.of("A4"), ids(QuestSearch.search(book, p, "locked text", 10)));
    }

    @Test
    void alwaysInvisibleChaptersAreSkipped() {
        setUp();
        book.chapter("B").alwaysInvisible = true;
        assertTrue(QuestSearch.search(book, p, "wheat", 10).isEmpty());
    }

    private static List<String> ids(List<QuestSearch.Result> r) {
        return r.stream().map(QuestSearch.Result::questId).toList();
    }

    @Test
    void pinToggleAndOrder() {
        PinStore s = new PinStore();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(PinStore.Result.PINNED, s.toggle(a, "Q1"));
        assertEquals(PinStore.Result.PINNED, s.toggle(a, "Q2"));
        assertEquals(List.of("Q1", "Q2"), s.pinsOf(a));
        assertEquals(List.of(), s.pinsOf(b), "pins are per player");
        assertEquals(PinStore.Result.UNPINNED, s.toggle(a, "Q1"));
        assertEquals(List.of("Q2"), s.pinsOf(a));
    }

    @Test
    void pinLimit() {
        PinStore s = new PinStore();
        UUID a = UUID.randomUUID();
        for (int i = 0; i < PinStore.MAX_PER_PLAYER; i++) {
            assertEquals(PinStore.Result.PINNED, s.toggle(a, "Q" + i));
        }
        assertEquals(PinStore.Result.FULL, s.toggle(a, "extra"));
        assertEquals(PinStore.MAX_PER_PLAYER, s.pinsOf(a).size());
        assertEquals(PinStore.Result.UNPINNED, s.toggle(a, "Q0"), "an existing pin can still be removed when full");
    }

    @Test
    void pruneRemovesUnknownQuests() {
        setUp();
        PinStore s = new PinStore();
        UUID a = UUID.randomUUID();
        s.toggle(a, "A1");
        s.toggle(a, "GONE");
        s.clearDirty();
        assertTrue(s.prune(book));
        assertEquals(List.of("A1"), s.pinsOf(a));
        assertTrue(s.isDirty());
        assertFalse(s.prune(book));
    }

    @Test
    void pinsSurviveTextRoundTripAndDamagedEntriesAreSkipped() {
        PinStore s = new PinStore();
        UUID a = UUID.randomUUID();
        s.toggle(a, "Q1");
        s.toggle(a, "Q2");
        PinStore back = PinStore.fromData(Snbt.parse(Snbt.print(s.toData())));
        assertEquals(List.of("Q1", "Q2"), back.pinsOf(a));
        PinStore damaged = PinStore.fromData(Snbt.parse("{ dataVersion: 1, pins: { \"not-a-uuid\": [\"x\"], \"" + a + "\": [\"ok\", 5] } }"));
        assertEquals(List.of("ok"), damaged.pinsOf(a));
        assertThrows(IllegalArgumentException.class, () -> PinStore.fromData(Snbt.parse("{ dataVersion: 9 }")));
    }
}
