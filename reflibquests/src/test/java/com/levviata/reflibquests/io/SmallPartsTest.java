/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflib.api.data.Snbt;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.model.Definitions.WeightedReward;
import com.levviata.reflibquests.model.ProgressCodec;
import com.levviata.reflibquests.model.ProgressEngine;
import com.levviata.reflibquests.model.QuestBook;
import com.levviata.reflibquests.model.TeamProgress;

class SmallPartsTest {

    @Test
    void idMapMapsItemsWithMetaAndLeavesUnknownAlone() {
        IdMap m = IdMap.from(Snbt.parse("""
                { items: { "minecraft:oak_log": "minecraft:log@0", "minecraft:red_wool": "minecraft:wool@14", "a:b": "c:d" }
                  entities: { "minecraft:zombified_piglin": "minecraft:zombie_pigman" } }
                """));
        assertEquals(4, m.size());
        assertEquals(new IdMap.ItemTarget("minecraft:wool", 14, true), m.item("minecraft:red_wool"));
        assertEquals(new IdMap.ItemTarget("c:d", 0, false), m.item("a:b"));
        assertEquals(new IdMap.ItemTarget("minecraft:stone", 0, false), m.item("minecraft:stone"));
        assertEquals("minecraft:zombie_pigman", m.entity("minecraft:zombified_piglin"));
        assertEquals("minecraft:cow", m.entity("minecraft:cow"));
    }

    @Test
    void dimensionNames() {
        assertEquals(0, Names.dimensionId("minecraft:overworld").getAsInt());
        assertEquals(-1, Names.dimensionId("minecraft:the_nether").getAsInt());
        assertEquals(1, Names.dimensionId("the_end").getAsInt());
        assertEquals(7, Names.dimensionId("7").getAsInt());
        assertTrue(Names.dimensionId("modded:thing").isEmpty());
    }

    @Test
    void weightedPickRespectsWeightsAndEmpty() {
        Reward a = new Reward("A", "xp", DataNode.Compound.empty());
        Reward b = new Reward("B", "xp", DataNode.Compound.empty());
        List<WeightedReward> entries = List.of(new WeightedReward(1, a), new WeightedReward(3, b));
        int[] counts = new int[3];
        Random rnd = new Random(42);
        for (int i = 0; i < 10_000; i++) {
            WeightedReward w = Names.pick(entries, 4, rnd);
            counts[w == null ? 2 : w.reward() == a ? 0 : 1]++;
        }
        // weights 1 : 3 : 4 (empty) out of 8
        assertEquals(1250, counts[0], 200);
        assertEquals(3750, counts[1], 300);
        assertEquals(5000, counts[2], 300);
        assertNull(Names.pick(List.of(), 0, rnd));
        assertNull(Names.pick(List.of(new WeightedReward(0, a)), 0, rnd));
    }

    @Test
    void progressCodecRoundTripThroughText() {
        QuestBook book = new QuestBook();
        Chapter c = new Chapter("C");
        Quest q = new Quest("Q1");
        q.tasks.add(new Task("T1", "item", DataNode.Compound.empty().with("count", 3)));
        Reward r = new Reward("R1", "xp", DataNode.Compound.empty());
        q.rewards.add(r);
        c.quests.add(q);
        book.chapters().add(c);
        book.reindex();
        ProgressEngine engine = new ProgressEngine(book);
        UUID team = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        TeamProgress p = new TeamProgress(team);
        engine.setTaskProgress(p, "T1", 3, 12345L);
        engine.claim(p, player, r, 20000L);
        p.setLocked(true);
        p.setRewardsBlocked(true);

        String text = Snbt.print(ProgressCodec.write(p));
        TeamProgress back = ProgressCodec.read(team, Snbt.parse(text));
        assertEquals(3, back.taskProgress("T1"));
        assertEquals(12345L, back.completedAt("Q1"));
        assertEquals(12345L, back.startedAt("Q1"));
        assertEquals(1, back.completionCount("Q1"));
        assertTrue(back.isClaimed("R1", player));
        assertTrue(back.isLocked());
        assertTrue(back.areRewardsBlocked());
    }

    @Test
    void progressCodecRefusesNewerData() {
        DataNode.Compound c = DataNode.Compound.empty().with("dataVersion", 99);
        assertThrows(IllegalArgumentException.class, () -> ProgressCodec.read(UUID.randomUUID(), c));
    }

    @Test
    void emptyDataReadsAsEmptyProgress() {
        TeamProgress p = ProgressCodec.read(UUID.randomUUID(), DataNode.Compound.empty());
        assertEquals(0, p.taskProgress("x"));
        assertFalse(p.isLocked());
    }
}
