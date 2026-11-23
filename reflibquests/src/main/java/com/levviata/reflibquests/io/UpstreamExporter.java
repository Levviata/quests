/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.levviata.reflib.api.data.DataFiles;
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

public final class UpstreamExporter {

    public static final int FORMAT_VERSION = 13;
    private static final String EXT = ".json5";

    private UpstreamExporter() {}

    public static void export(QuestBook book, Path folder) throws IOException {
        Files.createDirectories(folder);
        writeData(book, folder);
        writeGroups(book, folder);
        Set<String> chapterFiles = writeChapters(book, folder);
        retire(folder.resolve("chapters"), chapterFiles);
        Set<String> tableFiles = writeTables(book, folder);
        retire(folder.resolve("reward_tables"), tableFiles);
        writeLang(book, folder);
    }

    private static DataNode.Compound ensure(DataNode.Compound c, String key, DataNode value) {
        return c.has(key) ? c : c.with(key, value);
    }

    private static void writeData(QuestBook book, Path folder) throws IOException {
        DataNode.Compound d = book.settings;

        d = d.without("tags");
        if (d.get("icon") instanceof DataNode.Compound ic && !ic.has("count")) {
            d = d.with("icon", ic.with("count", 1));
        }
        d = d.with("version", FORMAT_VERSION);
        d = ensure(d, "default_reward_team", new DataNode.Bool(false));
        d = ensure(d, "default_consume_items", new DataNode.Bool(false));
        d = ensure(d, "default_autoclaim_rewards", new DataNode.Str("disabled"));
        d = ensure(d, "default_quest_shape", new DataNode.Str(""));
        d = ensure(d, "default_quest_disable_jei", new DataNode.Bool(false));
        d = ensure(d, "drop_loot_crates", new DataNode.Bool(false));
        d = d.with("progression_mode", book.progressionMode.name().toLowerCase(Locale.ROOT));
        d = d.with("fallback_locale", book.fallbackLocale);
        DataFiles.writeJson5(folder.resolve("data" + EXT), d);
    }

    private static void writeGroups(QuestBook book, Path folder) throws IOException {
        List<DataNode> list = new ArrayList<>();
        for (ChapterGroup g : book.groups()) {
            list.add(g.raw.without("title").with("id", g.id));
        }
        DataFiles.writeJson5(folder.resolve("chapter_groups" + EXT), DataNode.Compound.empty().with("chapter_groups", new DataNode.Lst(list)));
    }

    private static Set<String> writeChapters(QuestBook book, Path folder) throws IOException {
        Set<String> used = new HashSet<>();
        Map<String, Integer> orderInGroup = new LinkedHashMap<>();
        for (Chapter c : book.chapters()) {
            int index = orderInGroup.merge(c.groupId, 1, Integer::sum) - 1;
            String name = uniqueFile(c.filename.isEmpty() ? c.id : c.filename, used);
            used.add(name);
            DataFiles.writeJson5(folder.resolve("chapters").resolve(name + EXT), chapterData(c, name, index));
        }
        return used;
    }

    private static DataNode.Compound chapterData(Chapter c, String filename, int orderIndex) {
        DataNode.Compound o = c.raw.without("title").without("quests").without("quest_links").without("images");
        o = o.with("id", c.id).with("group", c.groupId).with("order_index", orderIndex).with("filename", filename);
        o = setOrRemove(o, "icon", c.icon.entries().isEmpty() ? null : c.icon);
        o = flag(o, "always_invisible", c.alwaysInvisible);
        o = flag(o, "disable_toast", c.disableToast);
        o = flag(o, "hide_text_until_complete", c.hideTextUntilComplete);
        o = tri(o, "consume_items", c.defaultConsumeItems);
        o = setOrRemove(o, "progression_mode", c.progressionMode == ProgressionMode.DEFAULT ? null
                : new DataNode.Str(c.progressionMode.name().toLowerCase(Locale.ROOT)));
        o = flag(o, "require_sequential_tasks", c.requireSequentialTasks);
        o = flag(o, "hide_quest_details_until_startable", c.hideQuestDetailsUntilStartable);
        o = flag(o, "hide_quest_until_deps_visible", c.hideQuestUntilDepsVisible);
        o = flag(o, "hide_quest_until_deps_complete", c.hideQuestUntilDepsComplete);
        o = flag(o, "default_repeatable_quest", c.defaultRepeatable);
        o = flag(o, "default_hide_dependency_lines", c.defaultHideDependencyLines);
        o = setOrRemove(o, "default_quest_shape", c.defaultQuestShape == null || c.defaultQuestShape.isEmpty() ? null
                : new DataNode.Str(c.defaultQuestShape));
        o = setOrRemove(o, "default_quest_size", c.defaultQuestSize == 1.0 ? null : new DataNode.Dbl(c.defaultQuestSize));
        o = setOrRemove(o, "default_min_width", c.minWindowWidth == 0 ? null : new DataNode.Int(c.minWindowWidth));
        o = setOrRemove(o, "preset", c.preset == null || c.preset.isEmpty() ? null : new DataNode.Str(c.preset));
        List<DataNode> quests = new ArrayList<>();
        for (Quest q : c.quests) {
            quests.add(questData(q));
        }
        List<DataNode> links = new ArrayList<>();
        for (var l : c.links) {
            links.add(l.raw().with("id", l.id()).with("linked_quest", l.linkedQuest()).with("x", l.x()).with("y", l.y()));
        }
        List<DataNode> images = new ArrayList<>();
        for (var i : c.images) {
            images.add(i.raw().without("title").with("id", i.id()));
        }
        return o.with("quests", new DataNode.Lst(quests)).with("quest_links", new DataNode.Lst(links)).with("images", new DataNode.Lst(images));
    }

    private static DataNode.Compound questData(Quest q) {
        DataNode.Compound o = q.raw.without("title").without("subtitle").without("description").without("tasks").without("rewards");
        o = o.with("id", q.id).with("x", q.x).with("y", q.y);
        o = setOrRemove(o, "shape", q.shape.isEmpty() || q.shape.equals("default") ? null : new DataNode.Str(q.shape));
        o = setOrRemove(o, "size", q.size == 1.0 ? null : new DataNode.Dbl(q.size));
        o = setOrRemove(o, "icon", q.icon.entries().isEmpty() ? null : q.icon);
        List<DataNode> deps = new ArrayList<>();
        for (String d : q.dependencies) {
            deps.add(new DataNode.Str(d));
        }
        o = setOrRemove(o, "dependencies", deps.isEmpty() ? null : new DataNode.Lst(deps));
        o = setOrRemove(o, "dep_control_pts", q.depControlPts.isEmpty() ? null : com.levviata.reflibquests.model.ControlPoints.write(q.depControlPts));
        o = setOrRemove(o, "dependency_requirement", q.dependencyMode == DependencyMode.ALL_COMPLETED ? null
                : new DataNode.Str(q.dependencyMode.name().toLowerCase(Locale.ROOT)));
        o = setOrRemove(o, "min_required_dependencies", q.minRequiredDependencies == 0 ? null : new DataNode.Int(q.minRequiredDependencies));
        o = setOrRemove(o, "max_completable_dependents", q.maxCompletableDependents == 0 ? null : new DataNode.Int(q.maxCompletableDependents));
        o = setOrRemove(o, "tags", q.tags.isEmpty() ? null : new DataNode.Lst(q.tags.stream().<DataNode>map(DataNode.Str::new).toList()));
        o = setOrRemove(o, "icon_scale", q.iconScale == 1.0 ? null : new DataNode.Dbl(q.iconScale));
        o = setOrRemove(o, "guide_page", q.guidePage.isEmpty() ? null : new DataNode.Str(q.guidePage));
        o = setOrRemove(o, "preset", q.preset.isEmpty() ? null : new DataNode.Str(q.preset));
        o = flag(o, "optional", q.optional);
        o = flag(o, "invisible", q.invisible);
        o = setOrRemove(o, "invisible_until_tasks", q.invisibleUntilTasks == 0 ? null : new DataNode.Int(q.invisibleUntilTasks));
        o = setOrRemove(o, "repeat_cooldown", q.repeatCooldown == 0 ? null : new DataNode.Int(q.repeatCooldown));
        o = setOrRemove(o, "progression_mode", q.progressionMode == ProgressionMode.DEFAULT ? null
                : new DataNode.Str(q.progressionMode.name().toLowerCase(Locale.ROOT)));
        o = tri(o, "can_repeat", q.canRepeat);
        o = tri(o, "require_sequential_tasks", q.requireSequentialTasks);
        o = tri(o, "hide_details_until_startable", q.hideDetailsUntilStartable);
        o = tri(o, "hide_until_deps_visible", q.hideUntilDepsVisible);
        o = tri(o, "hide_until_deps_complete", q.hideUntilDepsComplete);
        o = tri(o, "hide_dependency_lines", q.hideDependencyLines);
        o = tri(o, "hide_dependent_lines", q.hideDependentLines);
        o = flag(o, "ignore_reward_blocking", q.ignoreRewardBlocking);
        o = flag(o, "disable_toast", q.disableToast);
        o = flag(o, "hide_lock_icon", q.hideLockIcon);
        o = tri(o, "hide_text_until_complete", q.hideTextUntilComplete);
        o = tri(o, "disable_jei", q.disableJei);
        o = flag(o, "or_tasks", q.orTasks);
        List<DataNode> tasks = new ArrayList<>();
        for (Task t : q.tasks) {
            tasks.add(DataNode.Compound.empty().with("id", t.id()).with("type", t.type()).with("__params", withoutOurs(t.params()).without("title")));
        }
        List<DataNode> rewards = new ArrayList<>();
        for (Reward r : q.rewards) {
            rewards.add(DataNode.Compound.empty().with("id", r.id()).with("type", r.type()).with("__params", withoutOurs(r.params())));
        }
        return o.with("tasks", flatten(tasks)).with("rewards", flatten(rewards));
    }

    private static final java.util.Set<String> OURS_ONLY = java.util.Set.of();

    private static DataNode.Compound withoutOurs(DataNode.Compound params) {
        DataNode.Compound out = params;
        for (String key : OURS_ONLY) {
            out = out.without(key);
        }
        return out;
    }

    private static DataNode.Lst flatten(List<DataNode> items) {
        List<DataNode> out = new ArrayList<>();
        for (DataNode n : items) {
            DataNode.Compound c = (DataNode.Compound) n;
            DataNode.Compound merged = c.without("__params");
            for (var e : c.getCompound("__params").entries().entrySet()) {
                if (!e.getKey().equals("id") && !e.getKey().equals("type")) {
                    merged = merged.with(e.getKey(), e.getValue());
                }
            }
            out.add(merged);
        }
        return new DataNode.Lst(out);
    }

    private static DataNode.Compound flag(DataNode.Compound o, String key, boolean value) {
        return value ? o.with(key, true) : o.without(key);
    }

    private static DataNode.Compound tri(DataNode.Compound o, String key, Tristate t) {
        return t == Tristate.DEFAULT ? o.without(key) : o.with(key, t == Tristate.TRUE);
    }

    private static DataNode.Compound setOrRemove(DataNode.Compound o, String key, DataNode value) {
        return value == null ? o.without(key) : o.with(key, value);
    }

    private static Set<String> writeTables(QuestBook book, Path folder) throws IOException {
        Set<String> used = new HashSet<>();
        int index = 0;
        for (RewardTable t : book.tables().values()) {
            String name = uniqueFile(t.filename.isEmpty() ? t.id : t.filename, used);
            used.add(name);
            DataNode.Compound o = t.raw.without("title").without("rewards").with("id", t.id).with("order_index", index++);
            o = o.with("empty_weight", (double) t.emptyWeight).with("loot_size", t.lootSize);
            List<DataNode> rewards = new ArrayList<>();
            for (WeightedReward w : t.entries) {
                DataNode.Compound r = DataNode.Compound.empty().with("id", w.reward().id());
                if (!w.reward().type().equals("item")) {
                    r = r.with("type", w.reward().type());
                }
                for (var e : withoutOurs(w.reward().params()).entries().entrySet()) {
                    r = r.with(e.getKey(), e.getValue());
                }
                if (w.weight() != 1f) {
                    r = r.with("weight", (double) w.weight());
                }
                rewards.add(r);
            }
            DataFiles.writeJson5(folder.resolve("reward_tables").resolve(name + EXT), o.with("rewards", new DataNode.Lst(rewards)));
        }
        return used;
    }

    private static void writeLang(QuestBook book, Path folder) throws IOException {
        Map<String, Map<String, DataNode>> tables = new LinkedHashMap<>();
        for (var e : book.translations().entrySet()) {
            tables.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }
        Map<String, DataNode> main = tables.computeIfAbsent(book.fallbackLocale, l -> new LinkedHashMap<>());

        Set<String> live = new HashSet<>();
        for (ChapterGroup g : book.groups()) {
            live.add("chapter_group." + g.id + ".");
        }
        for (Chapter c : book.chapters()) {
            live.add("chapter." + c.id + ".");
            for (Quest q : c.quests) {
                live.add("quest." + q.id + ".");
                for (Task t : q.tasks) {
                    live.add("task." + t.id() + ".");
                }
            }
            for (var i : c.images) {
                live.add("image." + i.id() + ".");
            }
        }
        for (RewardTable t : book.tables().values()) {
            live.add("reward_table." + t.id + ".");
        }
        for (Map<String, DataNode> t : tables.values()) {
            t.keySet().removeIf(key -> isObjectKey(key) && !live.contains(objectPrefix(key)));
        }
        for (ChapterGroup g : book.groups()) {
            text(main, "chapter_group." + g.id + ".title", g.title);
        }
        for (Chapter c : book.chapters()) {
            text(main, "chapter." + c.id + ".title", c.title);
            list(main, "chapter." + c.id + ".chapter_subtitle", c.subtitle);
            for (var i : c.images) {
                text(main, "image." + i.id() + ".title", i.raw().getString("title", ""));
            }
            for (Quest q : c.quests) {
                text(main, "quest." + q.id + ".title", q.title);
                text(main, "quest." + q.id + ".quest_subtitle", q.subtitle);
                list(main, "quest." + q.id + ".quest_desc", q.description);
                for (Task t : q.tasks) {
                    text(main, "task." + t.id() + ".title", t.params().getString("title", ""));
                }
            }
        }
        for (RewardTable t : book.tables().values()) {
            text(main, "reward_table." + t.id + ".title", t.title);
        }
        for (var e : tables.entrySet()) {
            Path dir = folder.resolve("lang").resolve(e.getKey());
            Map<String, Map<String, DataNode>> byFile = new LinkedHashMap<>();
            for (var entry : e.getValue().entrySet()) {
                String key = entry.getKey();
                int dot = key.indexOf('.');
                String file = dot > 0 ? key.substring(0, dot) : "other";
                byFile.computeIfAbsent(file, k -> new LinkedHashMap<>()).put(key, entry.getValue());
            }
            Set<String> written = new HashSet<>();
            for (var f : byFile.entrySet()) {
                DataFiles.writeJson5(dir.resolve(f.getKey() + EXT), new DataNode.Compound(f.getValue()));
                written.add(f.getKey());
            }
            retire(dir, written);
        }
    }

    private static String objectPrefix(String key) {
        int first = key.indexOf('.');
        int second = first < 0 ? -1 : key.indexOf('.', first + 1);
        return second < 0 ? key : key.substring(0, second + 1);
    }

    private static boolean isObjectKey(String key) {
        return key.startsWith("quest.") || key.startsWith("task.") || key.startsWith("image.") || key.startsWith("chapter.") || key.startsWith("chapter_group.") || key.startsWith("reward_table.");
    }

    private static void text(Map<String, DataNode> table, String key, String value) {
        if (value == null || value.isEmpty()) {
            table.remove(key);
        } else {
            table.put(key, new DataNode.Str(value));
        }
    }

    private static void list(Map<String, DataNode> table, String key, List<String> lines) {
        if (lines.isEmpty()) {
            table.remove(key);
        } else {
            List<DataNode> items = new ArrayList<>();
            for (String l : lines) {
                items.add(new DataNode.Str(l));
            }
            table.put(key, new DataNode.Lst(items));
        }
    }

    private static String uniqueFile(String wanted, Set<String> used) {
        String base = wanted.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        if (base.isEmpty()) {
            base = "unnamed";
        }
        String name = base;
        int n = 2;
        while (used.contains(name)) {
            name = base + "_" + n++;
        }
        return name;
    }

    private static void retire(Path dir, Set<String> keep) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(EXT)).forEach(files::add);
        }
        for (Path p : files) {
            String n = p.getFileName().toString();
            String stem = n.substring(0, n.length() - EXT.length());
            if (!keep.contains(stem)) {
                Files.move(p, dir.resolve(n + ".removed"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
