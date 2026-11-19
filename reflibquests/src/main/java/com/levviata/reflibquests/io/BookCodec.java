/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import java.util.ArrayList;
import java.util.List;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.ChapterGroup;
import com.levviata.reflibquests.model.Definitions.DependencyMode;
import com.levviata.reflibquests.model.Definitions.ProgressionMode;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.RewardTable;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.model.Definitions.Tristate;
import com.levviata.reflibquests.model.Definitions.WeightedReward;
import com.levviata.reflibquests.model.QuestBook;

/**
 * Compact wire form of a {@link QuestBook} for sending to clients (our own schema, not the upstream file
 * format). Carries what the client screen needs: texts, positions, tasks and rewards with their parameters,
 * dependencies, flags and reward tables, plus the server's id map so the client resolves items identically.
 * Chapter images and quest links are not carried yet. Defaults are omitted to keep the data small.
 */
public final class BookCodec {

    public static final int VERSION = 1;

    private BookCodec() {}

    public record Decoded(QuestBook book, DataNode.Compound idMap) {}

    // ------------------------------------------------------------------ write

    public static DataNode.Compound write(QuestBook b, DataNode.Compound idMap) {
        List<DataNode> groups = new ArrayList<>();
        for (ChapterGroup g : b.groups()) {
            groups.add(DataNode.Compound.empty().with("id", g.id).with("title", g.title));
        }
        List<DataNode> chapters = new ArrayList<>();
        for (Chapter c : b.chapters()) {
            chapters.add(writeChapter(c));
        }
        List<DataNode> tables = new ArrayList<>();
        for (RewardTable t : b.tables().values()) {
            List<DataNode> entries = new ArrayList<>();
            for (WeightedReward w : t.entries) {
                entries.add(writeReward(w.reward()).with("weight", (double) w.weight()));
            }
            tables.add(DataNode.Compound.empty().with("id", t.id).with("title", t.title)
                    .with("empty", (double) t.emptyWeight).with("entries", new DataNode.Lst(entries)));
        }
        return DataNode.Compound.empty()
                .with("v", VERSION)
                .with("mode", b.progressionMode.name())
                .with("locale", b.fallbackLocale)
                .with("settings", b.settings)
                .with("idMap", idMap)
                .with("groups", new DataNode.Lst(groups))
                .with("chapters", new DataNode.Lst(chapters))
                .with("tables", new DataNode.Lst(tables));
    }

    private static DataNode.Compound writeChapter(Chapter c) {
        List<DataNode> quests = new ArrayList<>();
        for (Quest q : c.quests) {
            quests.add(writeQuest(q));
        }
        DataNode.Compound o = DataNode.Compound.empty().with("id", c.id).with("title", c.title);
        o = putList(o, "sub", c.subtitle);
        o = putIf(o, "icon", c.icon);
        o = putIf(o, "group", c.groupId);
        o = putIf(o, "order", c.order);
        o = putIf(o, "invisible", c.alwaysInvisible);
        if (c.progressionMode != ProgressionMode.DEFAULT) {
            o = o.with("mode", c.progressionMode.name());
        }
        o = putIf(o, "seq", c.requireSequentialTasks);
        o = putIf(o, "hideDetails", c.hideQuestDetailsUntilStartable);
        o = putIf(o, "hideDepsVisible", c.hideQuestUntilDepsVisible);
        o = putIf(o, "hideDepsComplete", c.hideQuestUntilDepsComplete);
        o = putIf(o, "repeatable", c.defaultRepeatable);
        return o.with("quests", new DataNode.Lst(quests));
    }

    private static DataNode.Compound writeQuest(Quest q) {
        DataNode.Compound o = DataNode.Compound.empty().with("id", q.id).with("x", q.x).with("y", q.y);
        o = putIf(o, "title", q.title);
        o = putIf(o, "sub", q.subtitle);
        o = putList(o, "desc", q.description);
        o = putIf(o, "shape", q.shape);
        if (q.size != 1.0) {
            o = o.with("size", q.size);
        }
        o = putIf(o, "icon", q.icon);
        List<DataNode> tasks = new ArrayList<>();
        for (Task t : q.tasks) {
            tasks.add(DataNode.Compound.empty().with("id", t.id()).with("type", t.type()).with("p", t.params()));
        }
        if (!tasks.isEmpty()) {
            o = o.with("tasks", new DataNode.Lst(tasks));
        }
        List<DataNode> rewards = new ArrayList<>();
        for (Reward r : q.rewards) {
            rewards.add(writeReward(r));
        }
        if (!rewards.isEmpty()) {
            o = o.with("rewards", new DataNode.Lst(rewards));
        }
        o = putList(o, "deps", q.dependencies);
        if (q.dependencyMode != DependencyMode.ALL_COMPLETED) {
            o = o.with("depMode", q.dependencyMode.name());
        }
        o = putIf(o, "minDeps", q.minRequiredDependencies);
        o = putIf(o, "optional", q.optional);
        o = putIf(o, "invisible", q.invisible);
        o = putIf(o, "invisibleTasks", q.invisibleUntilTasks);
        o = putIf(o, "cooldown", q.repeatCooldown);
        if (q.progressionMode != ProgressionMode.DEFAULT) {
            o = o.with("mode", q.progressionMode.name());
        }
        o = putTri(o, "repeat", q.canRepeat);
        o = putTri(o, "seq", q.requireSequentialTasks);
        o = putTri(o, "hideDetails", q.hideDetailsUntilStartable);
        o = putTri(o, "hideDepsVisible", q.hideUntilDepsVisible);
        o = putTri(o, "hideDepsComplete", q.hideUntilDepsComplete);
        o = putIf(o, "ignoreBlock", q.ignoreRewardBlocking);
        return o;
    }

    private static DataNode.Compound writeReward(Reward r) {
        return DataNode.Compound.empty().with("id", r.id()).with("type", r.type()).with("p", r.params());
    }

    private static DataNode.Compound putIf(DataNode.Compound o, String key, String v) {
        return v == null || v.isEmpty() ? o : o.with(key, v);
    }

    private static DataNode.Compound putIf(DataNode.Compound o, String key, int v) {
        return v == 0 ? o : o.with(key, v);
    }

    private static DataNode.Compound putIf(DataNode.Compound o, String key, boolean v) {
        return v ? o.with(key, true) : o;
    }

    private static DataNode.Compound putIf(DataNode.Compound o, String key, DataNode.Compound v) {
        return v.entries().isEmpty() ? o : o.with(key, v);
    }

    private static DataNode.Compound putList(DataNode.Compound o, String key, List<String> v) {
        if (v.isEmpty()) {
            return o;
        }
        List<DataNode> items = new ArrayList<>();
        for (String s : v) {
            items.add(new DataNode.Str(s));
        }
        return o.with(key, new DataNode.Lst(items));
    }

    private static DataNode.Compound putTri(DataNode.Compound o, String key, Tristate t) {
        return t == Tristate.DEFAULT ? o : o.with(key, t == Tristate.TRUE);
    }

    // ------------------------------------------------------------------ read

    /**
     * @throws IllegalArgumentException if the data is from a newer version
     */
    public static Decoded read(DataNode.Compound c) {
        if (c.getInt("v", 1) > VERSION) {
            throw new IllegalArgumentException("quest book wire version " + c.getInt("v", 1) + " is newer than supported " + VERSION);
        }
        QuestBook b = new QuestBook();
        b.progressionMode = ProgressionMode.parse(c.getString("mode", "LINEAR"), ProgressionMode.LINEAR);
        b.fallbackLocale = c.getString("locale", "en_us");
        b.settings = c.getCompound("settings");
        for (DataNode n : c.getList("groups")) {
            if (n instanceof DataNode.Compound g) {
                ChapterGroup group = new ChapterGroup(g.getString("id", ""));
                group.title = g.getString("title", "");
                b.groups().add(group);
            }
        }
        for (DataNode n : c.getList("chapters")) {
            if (n instanceof DataNode.Compound cc) {
                b.chapters().add(readChapter(cc));
            }
        }
        for (DataNode n : c.getList("tables")) {
            if (n instanceof DataNode.Compound t) {
                RewardTable table = new RewardTable(t.getString("id", ""));
                table.title = t.getString("title", "");
                table.emptyWeight = (float) t.getDouble("empty", 0);
                for (DataNode e : t.getList("entries")) {
                    if (e instanceof DataNode.Compound ec) {
                        table.entries.add(new WeightedReward((float) ec.getDouble("weight", 1), readReward(ec)));
                    }
                }
                b.tables().put(table.id, table);
            }
        }
        b.reindex();
        return new Decoded(b, c.getCompound("idMap"));
    }

    private static Chapter readChapter(DataNode.Compound c) {
        Chapter ch = new Chapter(c.getString("id", ""));
        ch.title = c.getString("title", "");
        ch.subtitle = strings(c.getList("sub"));
        ch.icon = c.getCompound("icon");
        ch.groupId = c.getString("group", "");
        ch.order = c.getInt("order", 0);
        ch.alwaysInvisible = c.getBool("invisible", false);
        ch.progressionMode = ProgressionMode.parse(c.getString("mode", "DEFAULT"), ProgressionMode.DEFAULT);
        ch.requireSequentialTasks = c.getBool("seq", false);
        ch.hideQuestDetailsUntilStartable = c.getBool("hideDetails", false);
        ch.hideQuestUntilDepsVisible = c.getBool("hideDepsVisible", false);
        ch.hideQuestUntilDepsComplete = c.getBool("hideDepsComplete", false);
        ch.defaultRepeatable = c.getBool("repeatable", false);
        for (DataNode n : c.getList("quests")) {
            if (n instanceof DataNode.Compound qc) {
                ch.quests.add(readQuest(qc));
            }
        }
        return ch;
    }

    private static Quest readQuest(DataNode.Compound c) {
        Quest q = new Quest(c.getString("id", ""));
        q.x = c.getDouble("x", 0);
        q.y = c.getDouble("y", 0);
        q.title = c.getString("title", "");
        q.subtitle = c.getString("sub", "");
        q.description = strings(c.getList("desc"));
        q.shape = c.getString("shape", "");
        q.size = c.getDouble("size", 1.0);
        q.icon = c.getCompound("icon");
        for (DataNode n : c.getList("tasks")) {
            if (n instanceof DataNode.Compound t) {
                q.tasks.add(new Task(t.getString("id", ""), t.getString("type", ""), t.getCompound("p")));
            }
        }
        for (DataNode n : c.getList("rewards")) {
            if (n instanceof DataNode.Compound r) {
                q.rewards.add(readReward(r));
            }
        }
        q.dependencies.addAll(strings(c.getList("deps")));
        q.dependencyMode = DependencyMode.parse(c.getString("depMode", "ALL_COMPLETED"));
        q.minRequiredDependencies = c.getInt("minDeps", 0);
        q.optional = c.getBool("optional", false);
        q.invisible = c.getBool("invisible", false);
        q.invisibleUntilTasks = c.getInt("invisibleTasks", 0);
        q.repeatCooldown = c.getInt("cooldown", 0);
        q.progressionMode = ProgressionMode.parse(c.getString("mode", "DEFAULT"), ProgressionMode.DEFAULT);
        q.canRepeat = tri(c, "repeat");
        q.requireSequentialTasks = tri(c, "seq");
        q.hideDetailsUntilStartable = tri(c, "hideDetails");
        q.hideUntilDepsVisible = tri(c, "hideDepsVisible");
        q.hideUntilDepsComplete = tri(c, "hideDepsComplete");
        q.ignoreRewardBlocking = c.getBool("ignoreBlock", false);
        return q;
    }

    private static Reward readReward(DataNode.Compound c) {
        return new Reward(c.getString("id", ""), c.getString("type", "item"), c.getCompound("p"));
    }

    private static Tristate tri(DataNode.Compound c, String key) {
        return Tristate.read(c, key);
    }

    private static List<String> strings(List<DataNode> list) {
        List<String> out = new ArrayList<>();
        for (DataNode n : list) {
            if (n instanceof DataNode.Str s) {
                out.add(s.value());
            }
        }
        return out;
    }
}
