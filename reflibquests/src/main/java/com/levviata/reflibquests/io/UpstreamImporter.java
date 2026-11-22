/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import com.levviata.reflib.api.data.DataFiles;
import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.ChapterGroup;
import com.levviata.reflibquests.model.Definitions.ChapterImage;
import com.levviata.reflibquests.model.Definitions.DependencyMode;
import com.levviata.reflibquests.model.Definitions.ProgressionMode;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.QuestLink;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.RewardTable;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.model.Definitions.Tristate;
import com.levviata.reflibquests.model.Definitions.WeightedReward;
import com.levviata.reflibquests.model.QuestBook;

/**
 * Reads a quest folder written by the upstream quest mod (latest {@code .json5} generation or the older
 * {@code .snbt} generation) into a {@link QuestBook}. The layout and field names are documented in
 * {@code docs/upstream-quest-format.md}. Problems (unreadable files, missing required fields) are collected
 * and the rest of the book still loads; nothing here throws for bad data.
 */
public final class UpstreamImporter {

    /** Result of an import. */
    public record Result(QuestBook book, List<String> problems, int quests, int tasks, int rewards) {}

    private final List<String> problems = new ArrayList<>();
    private String ext = ".json5";
    private int quests;
    private int tasks;
    private int rewards;

    private UpstreamImporter() {}

    public static Result load(Path folder) {
        return new UpstreamImporter().run(folder);
    }

    private Result run(Path folder) {
        if (LegacyImporter.applies(folder)) {
            return LegacyImporter.load(folder); // the older layout: one file per quest
        }
        QuestBook book = new QuestBook();
        if (Files.exists(folder.resolve("data.json5"))) {
            ext = ".json5";
        } else if (Files.exists(folder.resolve("data.snbt"))) {
            ext = ".snbt";
        }
        readBookData(folder, book);
        readGroups(folder, book);
        readChapters(folder, book);
        readTables(folder, book);
        Translations.load(folder.resolve("lang"), ext, book, problems);
        applyTranslations(book);
        book.reindex();
        return new Result(book, problems, quests, tasks, rewards);
    }

    private DataNode.Compound readFile(Path file) {
        try {
            return DataFiles.readLenient(file);
        } catch (IOException ex) {
            problems.add(ex.getMessage());
            return null;
        }
    }

    private void readBookData(Path folder, QuestBook book) {
        Path file = folder.resolve("data" + ext);
        if (!Files.exists(file)) {
            problems.add("missing " + file);
            return;
        }
        DataNode.Compound data = readFile(file);
        if (data == null) {
            return;
        }
        book.settings = data;
        book.progressionMode = ProgressionMode.parse(data.getString("progression_mode", "linear"), ProgressionMode.LINEAR);
        String fallback = data.getString("fallback_locale", "en_us");
        book.fallbackLocale = fallback.isBlank() ? "en_us" : fallback; // an empty setting means the default language
    }

    private void readGroups(Path folder, QuestBook book) {
        Path file = folder.resolve("chapter_groups" + ext);
        if (!Files.exists(file)) {
            return;
        }
        DataNode.Compound data = readFile(file);
        if (data == null) {
            return;
        }
        for (DataNode n : data.getList("chapter_groups")) {
            if (n instanceof DataNode.Compound g) {
                String id = Ids.normalize(g.getString("id", ""));
                if (id.isEmpty()) {
                    problems.add("chapter group without id in " + file);
                    continue;
                }
                ChapterGroup group = new ChapterGroup(id);
                group.title = g.getString("title", "");
                group.raw = g;
                book.groups().add(group);
            }
        }
    }

    private void readChapters(Path folder, QuestBook book) {
        Path dir = folder.resolve("chapters");
        if (!Files.isDirectory(dir)) {
            return;
        }
        for (Path file : listFiles(dir)) {
            DataNode.Compound c = readFile(file);
            if (c == null) {
                continue;
            }
            String id = Ids.normalize(c.getString("id", ""));
            if (id.isEmpty()) {
                problems.add("chapter without id: " + file);
                continue;
            }
            Chapter chapter = new Chapter(id);
            chapter.filename = c.getString("filename", stem(file));
            chapter.groupId = Ids.normalize(c.getString("group", ""));
            chapter.order = c.getInt("order_index", 0);
            chapter.title = c.getString("title", "");
            chapter.icon = c.getCompound("icon");
            chapter.alwaysInvisible = c.getBool("always_invisible", false);
            chapter.disableToast = c.getBool("disable_toast", false);
            chapter.hideTextUntilComplete = c.getBool("hide_text_until_complete", false);
            chapter.defaultConsumeItems = Tristate.read(c, "consume_items");
            chapter.progressionMode = ProgressionMode.parse(c.getString("progression_mode", "default"), ProgressionMode.DEFAULT);
            chapter.requireSequentialTasks = c.getBool("require_sequential_tasks", false);
            chapter.hideQuestDetailsUntilStartable = c.getBool("hide_quest_details_until_startable", false);
            chapter.hideQuestUntilDepsVisible = c.getBool("hide_quest_until_deps_visible", false);
            chapter.hideQuestUntilDepsComplete = c.getBool("hide_quest_until_deps_complete", false);
            chapter.defaultHideDependencyLines = c.getBool("default_hide_dependency_lines", false);
            chapter.defaultQuestShape = c.getString("default_quest_shape", "");
            chapter.defaultRepeatable = c.getBool("default_repeatable_quest", false);
            chapter.raw = c.without("quests").without("quest_links").without("images");
            for (DataNode qn : c.getList("quests")) {
                if (qn instanceof DataNode.Compound qc) {
                    Quest q = readQuest(qc, file);
                    if (q != null) {
                        chapter.quests.add(q);
                    }
                }
            }
            for (DataNode ln : c.getList("quest_links")) {
                if (ln instanceof DataNode.Compound lc) {
                    String lid = Ids.normalize(lc.getString("id", ""));
                    String target = Ids.normalize(lc.getString("linked_quest", ""));
                    if (lid.isEmpty() || target.isEmpty()) {
                        problems.add("incomplete quest link in " + file);
                        continue;
                    }
                    chapter.links.add(new QuestLink(lid, target, lc.getDouble("x", 0), lc.getDouble("y", 0), lc));
                }
            }
            for (DataNode in : c.getList("images")) {
                if (in instanceof DataNode.Compound ic) {
                    chapter.images.add(new ChapterImage(Ids.normalize(ic.getString("id", "")), ic));
                }
            }
            book.chapters().add(chapter);
        }
    }

    private Quest readQuest(DataNode.Compound c, Path file) {
        String id = Ids.normalize(c.getString("id", ""));
        if (id.isEmpty()) {
            problems.add("quest without id in " + file);
            return null;
        }
        if (!c.has("x") || !c.has("y")) {
            problems.add("quest " + id + " has no position in " + file);
        }
        Quest q = new Quest(id);
        q.title = c.getString("title", "");
        q.subtitle = c.getString("subtitle", "");
        for (DataNode line : c.getList("description")) {
            if (line instanceof DataNode.Str s) {
                q.description.add(s.value());
            }
        }
        q.x = c.getDouble("x", 0);
        q.y = c.getDouble("y", 0);
        q.shape = c.getString("shape", "");
        q.size = c.getDouble("size", 1.0);
        q.z = c.getDouble("z", 0);
        q.icon = c.getCompound("icon");
        for (DataNode d : c.getList("dependencies")) {
            if (d instanceof DataNode.Str s && !Ids.normalize(s.value()).isEmpty()) {
                q.dependencies.add(Ids.normalize(s.value()));
            }
        }
        q.depControlPts = com.levviata.reflibquests.model.ControlPoints.read(c.getCompound("dep_control_pts"));
        q.dependencyMode = DependencyMode.parse(c.getString("dependency_requirement", "all_completed"));
        q.minRequiredDependencies = c.getInt("min_required_dependencies", 0);
        q.maxCompletableDependents = c.getInt("max_completable_dependents", 0);
        for (DataNode t : c.getList("tags")) {
            if (t instanceof DataNode.Str s && !s.value().isBlank()) {
                q.tags.add(s.value());
            }
        }
        q.iconScale = c.getDouble("icon_scale", 1.0);
        q.guidePage = c.getString("guide_page", "");
        q.preset = c.getString("preset", "");
        q.optional = c.getBool("optional", false);
        q.invisible = c.getBool("invisible", false);
        q.invisibleUntilTasks = c.getInt("invisible_until_tasks", 0);
        q.repeatCooldown = c.getInt("repeat_cooldown", 0);
        q.progressionMode = ProgressionMode.parse(c.getString("progression_mode", "default"), ProgressionMode.DEFAULT);
        q.canRepeat = Tristate.read(c, "can_repeat");
        q.requireSequentialTasks = Tristate.read(c, "require_sequential_tasks");
        q.hideDetailsUntilStartable = Tristate.read(c, "hide_details_until_startable");
        q.hideUntilDepsVisible = Tristate.read(c, "hide_until_deps_visible");
        q.hideUntilDepsComplete = Tristate.read(c, "hide_until_deps_complete");
        q.hideDependencyLines = Tristate.read(c, "hide_dependency_lines");
        q.hideDependentLines = Tristate.read(c, "hide_dependent_lines");
        q.ignoreRewardBlocking = c.getBool("ignore_reward_blocking", false);
        q.disableToast = c.getBool("disable_toast", false);
        q.hideLockIcon = c.getBool("hide_lock_icon", false);
        q.hideTextUntilComplete = Tristate.read(c, "hide_text_until_complete");
        q.disableJei = Tristate.read(c, "disable_jei");
        q.orTasks = c.getBool("or_tasks", false);
        q.raw = c.without("tasks").without("rewards");
        for (DataNode tn : c.getList("tasks")) {
            if (tn instanceof DataNode.Compound tc) {
                String tid = Ids.normalize(tc.getString("id", ""));
                if (tid.isEmpty()) {
                    problems.add("task without id in quest " + id);
                    continue;
                }
                q.tasks.add(new Task(tid, Ids.stripNamespace(tc.getString("type", "")), tc.without("id").without("type")));
                tasks++;
            }
        }
        for (DataNode rn : c.getList("rewards")) {
            if (rn instanceof DataNode.Compound rc) {
                Reward r = readReward(rc, id);
                if (r != null) {
                    q.rewards.add(r);
                }
            }
        }
        quests++;
        return q;
    }

    private Reward readReward(DataNode.Compound rc, String owner) {
        String rid = Ids.normalize(rc.getString("id", ""));
        if (rid.isEmpty()) {
            problems.add("reward without id in " + owner);
            return null;
        }
        String type = rc.has("type") ? Ids.stripNamespace(rc.getString("type", "")) : "item";
        rewards++;
        return new Reward(rid, type, rc.without("id").without("type"));
    }

    private void readTables(Path folder, QuestBook book) {
        Path dir = folder.resolve("reward_tables");
        if (!Files.isDirectory(dir)) {
            return;
        }
        for (Path file : listFiles(dir)) {
            DataNode.Compound c = readFile(file);
            if (c == null) {
                continue;
            }
            String id = Ids.normalize(c.getString("id", ""));
            if (id.isEmpty()) {
                problems.add("reward table without id: " + file);
                continue;
            }
            RewardTable t = new RewardTable(id);
            t.filename = stem(file);
            t.title = c.getString("title", "");
            t.order = c.getInt("order_index", 0);
            t.emptyWeight = (float) c.getDouble("empty_weight", 0);
            t.lootSize = c.getInt("loot_size", 0);
            t.raw = c.without("rewards");
            for (DataNode rn : c.getList("rewards")) {
                if (rn instanceof DataNode.Compound rc) {
                    Reward r = readReward(rc, "reward table " + id);
                    if (r != null) {
                        // the weight belongs to the table entry, not to the reward itself
                        Reward clean = new Reward(r.id(), r.type(), r.params().without("weight"));
                        t.entries.add(new WeightedReward((float) rc.getDouble("weight", 1.0), clean));
                    }
                }
            }
            book.tables().put(id, t);
        }
    }

    private void applyTranslations(QuestBook book) {
        Map<String, DataNode> table = book.translations().get(book.fallbackLocale);
        if (table == null && !book.translations().isEmpty()) {
            table = book.translations().values().iterator().next();
        }
        if (table == null) {
            return;
        }
        for (ChapterGroup g : book.groups()) {
            g.title = text(table, "chapter_group." + g.id + ".title", g.title);
        }
        for (Chapter c : book.chapters()) {
            c.title = text(table, "chapter." + c.id + ".title", c.title);
            List<String> sub = list(table, "chapter." + c.id + ".chapter_subtitle");
            if (!sub.isEmpty()) {
                c.subtitle = sub;
            }
            for (int ii = 0; ii < c.images.size(); ii++) {
                var img = c.images.get(ii);
                String imageTitle = text(table, "image." + img.id() + ".title", "");
                if (!imageTitle.isEmpty()) {
                    c.images.set(ii, new com.levviata.reflibquests.model.Definitions.ChapterImage(img.id(), img.raw().with("title", imageTitle)));
                }
            }
            for (Quest q : c.quests) {
                q.title = text(table, "quest." + q.id + ".title", q.title);
                q.subtitle = text(table, "quest." + q.id + ".quest_subtitle", q.subtitle);
                for (int ti = 0; ti < q.tasks.size(); ti++) {
                    Task old = q.tasks.get(ti);
                    String taskTitle = text(table, "task." + old.id() + ".title", "");
                    if (!taskTitle.isEmpty()) {
                        q.tasks.set(ti, new Task(old.id(), old.type(), old.params().with("title", taskTitle)));
                    }
                }
                List<String> desc = list(table, "quest." + q.id + ".quest_desc");
                if (!desc.isEmpty()) {
                    q.description = desc;
                }
            }
        }
        for (RewardTable t : book.tables().values()) {
            t.title = text(table, "reward_table." + t.id + ".title", t.title);
        }
    }

    private static String text(Map<String, DataNode> table, String key, String fallback) {
        return table.get(key) instanceof DataNode.Str s ? s.value() : fallback;
    }

    private static List<String> list(Map<String, DataNode> table, String key) {
        List<String> out = new ArrayList<>();
        if (table.get(key) instanceof DataNode.Lst l) {
            for (DataNode n : l.items()) {
                if (n instanceof DataNode.Str s) {
                    out.add(s.value());
                }
            }
        }
        return out;
    }

    private List<Path> listFiles(Path dir) {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ext)).sorted().forEach(out::add);
        } catch (IOException ex) {
            problems.add("cannot list " + dir + ": " + ex.getMessage());
        }
        return out;
    }

    private String stem(Path file) {
        String n = file.getFileName().toString();
        return n.endsWith(ext) ? n.substring(0, n.length() - ext.length()) : n;
    }
}
