/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.DependencyMode;
import com.levviata.reflibquests.model.Definitions.ProgressionMode;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.RewardTable;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.model.Definitions.Tristate;
import com.levviata.reflibquests.model.Definitions.WeightedReward;
import com.levviata.reflibquests.model.QuestBook;

class WireTest {

    private static QuestBook sample() {
        QuestBook b = new QuestBook();
        b.progressionMode = ProgressionMode.FLEXIBLE;
        b.fallbackLocale = "de_de";
        b.settings = DataNode.Compound.empty().with("detection_delay", 10).with("default_consume_items", true);
        Chapter c = new Chapter("C1");
        c.title = "First";
        c.subtitle.add("sub line");
        c.order = 2;
        c.requireSequentialTasks = true;
        c.icon = DataNode.Compound.empty().with("id", "minecraft:book");
        Quest q = new Quest("Q1");
        q.title = "Quest \"one\"";
        q.subtitle = "tip";
        q.description.add("line 1");
        q.description.add("line\n2");
        q.x = 1.5;
        q.y = -2;
        q.shape = "circle";
        q.size = 1.5;
        q.tasks.add(new Task("T1", "item", DataNode.Compound.empty().with("count", 4).with("item", DataNode.Compound.empty().with("id", "minecraft:dirt"))));
        q.rewards.add(new Reward("R1", "xp", DataNode.Compound.empty().with("xp", 5)));
        q.dependencies.add("Q0");
        q.dependencyMode = DependencyMode.ONE_STARTED;
        q.minRequiredDependencies = 1;
        q.optional = true;
        q.invisibleUntilTasks = 2;
        q.repeatCooldown = 40;
        q.canRepeat = Tristate.TRUE;
        q.hideDetailsUntilStartable = Tristate.FALSE;
        q.progressionMode = ProgressionMode.LINEAR;
        q.ignoreRewardBlocking = true;
        c.quests.add(q);
        Quest plain = new Quest("Q2");
        c.quests.add(plain);
        b.chapters().add(c);
        RewardTable t = new RewardTable("TB");
        t.title = "Table";
        t.emptyWeight = 2.5f;
        t.entries.add(new WeightedReward(3f, new Reward("TR1", "item", DataNode.Compound.empty().with("count", 2))));
        b.tables().put("TB", t);
        b.reindex();
        return b;
    }

    @Test
    void bookRoundTripIsLossless() {
        QuestBook b = sample();
        DataNode.Compound idMap = DataNode.Compound.empty().with("items", DataNode.Compound.empty().with("a:b", "c:d"));
        DataNode.Compound wire = BookCodec.write(b, idMap);
        BookCodec.Decoded d = BookCodec.read(Gz.unpack(Gz.pack(wire)));
        assertEquals(wire, BookCodec.write(d.book(), d.idMap()), "write(read(write(x))) == write(x)");
        assertEquals(idMap, d.idMap());
        Quest q = d.book().quest("Q1");
        assertEquals("Quest \"one\"", q.title);
        assertEquals(java.util.List.of("line 1", "line\n2"), q.description);
        assertEquals(ProgressionMode.FLEXIBLE, d.book().progressionMode);
        assertEquals(DependencyMode.ONE_STARTED, q.dependencyMode);
        assertEquals(Tristate.TRUE, q.canRepeat);
        assertEquals(Tristate.FALSE, q.hideDetailsUntilStartable);
        assertEquals(Tristate.DEFAULT, q.requireSequentialTasks);
        assertEquals(4, q.tasks.get(0).maxProgress());
        assertEquals("de_de", d.book().fallbackLocale);
        assertEquals(1, d.book().tables().get("TB").entries.size());
        assertEquals(2.5f, d.book().tables().get("TB").emptyWeight);
        assertSame(d.book().chapter("C1"), d.book().chapterOf("Q1"));
    }

    @Test
    void defaultsAreOmitted() {
        QuestBook b = new QuestBook();
        Chapter c = new Chapter("C");
        c.quests.add(new Quest("Q"));
        b.chapters().add(c);
        b.reindex();
        String text = com.levviata.reflib.api.data.Snbt.printCompact(BookCodec.write(b, DataNode.Compound.empty()));
        assertFalse(text.contains("optional"));
        assertFalse(text.contains("depMode"));
        assertFalse(text.contains("shape"));
    }

    @Test
    void refusesNewerWireVersion() {
        assertThrows(IllegalArgumentException.class, () -> BookCodec.read(DataNode.Compound.empty().with("v", 99)));
    }

    @Test
    void largeBookCompressesAndRoundTrips() {
        QuestBook b = new QuestBook();
        Random rnd = new Random(1);
        for (int ci = 0; ci < 10; ci++) {
            Chapter c = new Chapter("CH" + ci);
            c.title = "Chapter " + ci;
            for (int i = 0; i < 400; i++) {
                Quest q = new Quest("C" + ci + "Q" + i);
                q.title = "Quest number " + i + " of chapter " + ci;
                q.description.add("Some description text that repeats a bit " + (i % 7));
                q.x = rnd.nextInt(60);
                q.y = rnd.nextInt(60);
                q.tasks.add(new Task("C" + ci + "T" + i, "item", DataNode.Compound.empty().with("count", 1 + i % 9)
                        .with("item", DataNode.Compound.empty().with("id", "minecraft:dirt"))));
                q.rewards.add(new Reward("C" + ci + "R" + i, "xp", DataNode.Compound.empty().with("xp", 5)));
                if (i > 0) {
                    q.dependencies.add("C" + ci + "Q" + (i - 1));
                }
                c.quests.add(q);
            }
            b.chapters().add(c);
        }
        b.reindex();
        byte[] packed = Gz.pack(BookCodec.write(b, DataNode.Compound.empty()));
        assertTrue(packed.length < 600_000, "4000 quests packed to " + packed.length + " bytes");
        assertEquals(4000, BookCodec.read(Gz.unpack(packed)).book().allQuests().size());
    }

    @Test
    void unpackRejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> Gz.unpack(new byte[] {1, 2, 3, 4}));
        assertThrows(IllegalArgumentException.class, () -> Gz.unpack(new byte[0]));
    }
}
