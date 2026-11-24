/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Task;

class ExcludedQuestsTest {

    private QuestBook book;
    private TeamProgress team;
    private Quest root;
    private Quest first;
    private Quest second;
    private ProgressEngine engine;

    private Quest quest(Chapter c, String id, String... deps) {
        Quest q = new Quest(id);
        q.tasks.add(new Task(id + "t", "checkmark", DataNode.Compound.empty()));
        q.dependencies.addAll(List.of(deps));
        c.quests.add(q);
        return q;
    }

    @BeforeEach
    void setUp() {
        book = new QuestBook();
        Chapter c = new Chapter("C1");
        book.chapters().add(c);
        root = quest(c, "ROOT");
        first = quest(c, "A", "ROOT");
        second = quest(c, "B", "ROOT");
        root.maxCompletableDependents = 1;
        book.reindex();
        engine = new ProgressEngine(book);
        team = new TeamProgress(UUID.randomUUID());
    }

    @Test
    void nothingIsExcludedBeforeTheLimitIsUsed() {
        assertFalse(QuestRules.excluded(book, team, first));
        assertFalse(QuestRules.excluded(book, team, second));
    }

    @Test
    void theOtherBranchIsExcludedOnceTheLimitIsReached() {
        engine.forceComplete(team, "ROOT", 1);
        engine.forceComplete(team, "A", 2);
        assertFalse(QuestRules.excluded(book, team, first), "a finished quest is never excluded");
        assertTrue(QuestRules.excluded(book, team, second));
        assertFalse(QuestRules.dependenciesMet(book, team, second));
    }

    @Test
    void zeroMeansNoLimit() {
        root.maxCompletableDependents = 0;
        engine.forceComplete(team, "ROOT", 1);
        engine.forceComplete(team, "A", 2);
        assertFalse(QuestRules.excluded(book, team, second));
    }

    @Test
    void theBookSettingHidesExcludedQuests() {
        engine.forceComplete(team, "ROOT", 1);
        engine.forceComplete(team, "A", 2);
        assertTrue(QuestRules.isVisible(book, team, second));
        book.settings = book.settings.with("hide_excluded_quests", true);
        assertFalse(QuestRules.isVisible(book, team, second));
        assertTrue(QuestRules.isVisible(book, team, first));
    }
}
