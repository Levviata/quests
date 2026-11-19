/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.model.Definitions.Tristate;

/**
 * Every change the in-game editor can make to a {@link QuestBook}, with its validation. Pure logic, so it can
 * be tested without the game; the server calls it after checking permission, and then saves and re-syncs. All
 * methods check their input (the data comes from clients) and never throw for bad input: they return
 * {@link Result#NOT_FOUND} or {@link Result#INVALID} and leave the book unchanged.
 */
public final class BookEditor {

    public enum Result { OK, NOT_FOUND, INVALID, CYCLE }

    public static final int MAX_TITLE = 120;
    public static final int MAX_LINES = 60;
    public static final int MAX_LINE = 300;
    public static final double MAX_COORD = 100_000;
    public static final long MAX_VALUE = 1_000_000_000L;

    /** Task types the editor may add. */
    public static final Set<String> TASK_TYPES = Set.of("item", "checkmark", "xp", "kill", "advancement", "dimension", "location", "custom");
    /** Reward types the editor may add. */
    public static final Set<String> REWARD_TYPES = Set.of("item", "xp", "xp_levels", "command", "toast");
    public static final Set<String> SHAPES = Set.of("", "circle", "square", "diamond", "hexagon", "octagon", "pentagon", "heart", "gear", "rsquare", "none");
    public static final Set<String> FLAGS = Set.of("optional", "invisible", "repeatable", "sequential", "hide_details", "hide_deps_visible", "hide_deps_complete");

    private final QuestBook book;
    private final Random random;

    public BookEditor(QuestBook book, Random random) {
        this.book = book;
        this.random = random;
    }

    // ------------------------------------------------------------------ ids and helpers

    private Set<String> allIds() {
        Set<String> ids = new HashSet<>();
        for (Chapter c : book.chapters()) {
            ids.add(c.id);
            for (Quest q : c.quests) {
                ids.add(q.id);
                q.tasks.forEach(t -> ids.add(t.id()));
                q.rewards.forEach(r -> ids.add(r.id()));
            }
        }
        book.groups().forEach(g -> ids.add(g.id));
        book.tables().forEach((id, t) -> {
            ids.add(id);
            t.entries.forEach(w -> ids.add(w.reward().id()));
        });
        return ids;
    }

    /** A new 16-digit upper-case hexadecimal id that no object in the book uses. */
    String newId() {
        Set<String> used = allIds();
        String id;
        do {
            id = String.format("%016X", random.nextLong());
        } while (used.contains(id) || id.equals("0000000000000000"));
        return id;
    }

    private static String clean(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.length() > max || t.indexOf('\n') >= 0 ? null : t;
    }

    private static double snap(double v) {
        return Math.round(v * 2.0) / 2.0;
    }

    private static boolean coordOk(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v) && Math.abs(v) <= MAX_COORD;
    }

    private static String slug(String title) {
        String s = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "chapter" : s.length() > 40 ? s.substring(0, 40) : s;
    }

    // ------------------------------------------------------------------ chapters

    /** @return the new chapter, or null if the title is invalid */
    public Chapter createChapter(String title) {
        String t = clean(title, MAX_TITLE);
        if (t == null || t.isEmpty()) {
            return null;
        }
        Chapter c = new Chapter(newId());
        c.title = t;
        Set<String> names = new HashSet<>();
        book.chapters().forEach(x -> names.add(x.filename.toLowerCase(Locale.ROOT)));
        String base = slug(t);
        String name = base;
        int n = 2;
        while (names.contains(name)) {
            name = base + "_" + n++;
        }
        c.filename = name;
        c.order = book.chapters().stream().mapToInt(x -> x.order).max().orElse(-1) + 1;
        book.chapters().add(c);
        book.reindex();
        return c;
    }

    public Result renameChapter(String id, String title) {
        Chapter c = book.chapter(id);
        String t = clean(title, MAX_TITLE);
        if (c == null) {
            return Result.NOT_FOUND;
        }
        if (t == null || t.isEmpty()) {
            return Result.INVALID;
        }
        c.title = t;
        return Result.OK;
    }

    public Result deleteChapter(String id) {
        Chapter c = book.chapter(id);
        if (c == null) {
            return Result.NOT_FOUND;
        }
        Set<String> gone = new HashSet<>();
        c.quests.forEach(q -> gone.add(q.id));
        book.chapters().remove(c);
        removeReferencesTo(gone);
        book.reindex();
        return Result.OK;
    }

    // ------------------------------------------------------------------ quests

    /** @return the new quest (with one checkmark task), or null if the chapter is unknown or the position invalid */
    public Quest createQuest(String chapterId, double x, double y) {
        Chapter c = book.chapter(chapterId);
        if (c == null || !coordOk(x) || !coordOk(y)) {
            return null;
        }
        Quest q = new Quest(newId());
        q.title = "New quest";
        q.x = snap(x);
        q.y = snap(y);
        q.tasks.add(new Task(newId(), "checkmark", DataNode.Compound.empty()));
        c.quests.add(q);
        book.reindex();
        return q;
    }

    public Result moveQuest(String id, double x, double y) {
        Quest q = book.quest(id);
        if (q == null) {
            return Result.NOT_FOUND;
        }
        if (!coordOk(x) || !coordOk(y)) {
            return Result.INVALID;
        }
        q.x = snap(x);
        q.y = snap(y);
        return Result.OK;
    }

    public Result deleteQuest(String id) {
        Chapter c = book.chapterOf(id);
        Quest q = book.quest(id);
        if (c == null || q == null) {
            return Result.NOT_FOUND;
        }
        c.quests.remove(q);
        removeReferencesTo(Set.of(id));
        book.reindex();
        return Result.OK;
    }

    private void removeReferencesTo(Set<String> questIds) {
        for (Chapter c : book.chapters()) {
            for (Quest q : c.quests) {
                q.dependencies.removeIf(questIds::contains);
            }
            c.links.removeIf(l -> questIds.contains(l.linkedQuest()));
        }
    }

    public Result setText(String questId, String title, String subtitle, List<String> description) {
        Quest q = book.quest(questId);
        if (q == null) {
            return Result.NOT_FOUND;
        }
        String t = clean(title, MAX_TITLE);
        String s = clean(subtitle, MAX_TITLE);
        if (t == null || s == null || description == null || description.size() > MAX_LINES) {
            return Result.INVALID;
        }
        List<String> lines = new ArrayList<>();
        for (String l : description) {
            if (l == null || l.length() > MAX_LINE || l.indexOf('\n') >= 0) {
                return Result.INVALID;
            }
            lines.add(l);
        }
        q.title = t;
        q.subtitle = s;
        q.description = lines;
        return Result.OK;
    }

    /** Make {@code questId} depend on {@code depId}, or remove that dependency if it exists. */
    public Result toggleDependency(String questId, String depId) {
        Quest q = book.quest(questId);
        Quest dep = book.quest(depId);
        if (q == null || dep == null) {
            return Result.NOT_FOUND;
        }
        if (questId.equals(depId)) {
            return Result.INVALID;
        }
        if (q.dependencies.remove(depId)) {
            return Result.OK;
        }
        if (dependsOn(dep, questId, new HashSet<>())) {
            return Result.CYCLE;
        }
        q.dependencies.add(depId);
        return Result.OK;
    }

    private boolean dependsOn(Quest from, String targetId, Set<String> seen) {
        if (!seen.add(from.id)) {
            return false;
        }
        for (String d : from.dependencies) {
            if (d.equals(targetId)) {
                return true;
            }
            Quest next = book.quest(d);
            if (next != null && dependsOn(next, targetId, seen)) {
                return true;
            }
        }
        return false;
    }

    public Result setShape(String questId, String shape) {
        Quest q = book.quest(questId);
        if (q == null) {
            return Result.NOT_FOUND;
        }
        String s = shape == null ? "" : shape.toLowerCase(Locale.ROOT);
        if (!SHAPES.contains(s)) {
            return Result.INVALID;
        }
        q.shape = s;
        return Result.OK;
    }

    public Result setFlag(String questId, String flag, boolean value) {
        Quest q = book.quest(questId);
        if (q == null) {
            return Result.NOT_FOUND;
        }
        Tristate tri = value ? Tristate.TRUE : Tristate.DEFAULT;
        switch (flag) {
            case "optional":
                q.optional = value;
                break;
            case "invisible":
                q.invisible = value;
                break;
            case "repeatable":
                q.canRepeat = tri;
                break;
            case "sequential":
                q.requireSequentialTasks = tri;
                break;
            case "hide_details":
                q.hideDetailsUntilStartable = tri;
                break;
            case "hide_deps_visible":
                q.hideUntilDepsVisible = tri;
                break;
            case "hide_deps_complete":
                q.hideUntilDepsComplete = tri;
                break;
            default:
                return Result.INVALID;
        }
        return Result.OK;
    }

    // ------------------------------------------------------------------ tasks and rewards

    /** @return the new task's id, or null if the quest is unknown or the type/parameters are not allowed */
    public String addTask(String questId, String type, DataNode.Compound params) {
        Quest q = book.quest(questId);
        if (q == null || !TASK_TYPES.contains(type) || params == null || q.tasks.size() >= 20) {
            return null;
        }
        DataNode.Compound p = params;
        if (type.equals("item") && !p.has("item")) {
            return null;
        }
        if ((type.equals("xp") || type.equals("kill")) && !p.has("value")) {
            p = p.with("value", 1);
        }
        String id = newId();
        q.tasks.add(new Task(id, type, p));
        book.reindex();
        return id;
    }

    public Result removeTask(String taskId) {
        Quest q = book.questOfTask(taskId);
        if (q == null) {
            return Result.NOT_FOUND;
        }
        q.tasks.removeIf(t -> t.id().equals(taskId));
        book.reindex();
        return Result.OK;
    }

    /** Set the amount of a counted task: the item count, or the value of an xp, kill, stat or energy task. */
    public Result setTaskValue(String taskId, long value) {
        Quest q = book.questOfTask(taskId);
        if (q == null) {
            return Result.NOT_FOUND;
        }
        for (int i = 0; i < q.tasks.size(); i++) {
            Task t = q.tasks.get(i);
            if (!t.id().equals(taskId)) {
                continue;
            }
            String key = t.type().equals("item") ? "count" : List.of("xp", "kill", "stat", "energy").contains(t.type()) ? "value" : null;
            if (key == null || value < 1 || value > MAX_VALUE) {
                return Result.INVALID;
            }
            q.tasks.set(i, new Task(t.id(), t.type(), t.params().with(key, value > Integer.MAX_VALUE ? (DataNode) new DataNode.Lng(value) : new DataNode.Int((int) value))));
            book.reindex();
            return Result.OK;
        }
        return Result.NOT_FOUND;
    }

    /** @return the new reward's id, or null if the quest is unknown or the type is not allowed */
    public String addReward(String questId, String type, DataNode.Compound params) {
        Quest q = book.quest(questId);
        if (q == null || !REWARD_TYPES.contains(type) || params == null || q.rewards.size() >= 20) {
            return null;
        }
        if (type.equals("item") && !params.has("item")) {
            return null;
        }
        String id = newId();
        q.rewards.add(new Reward(id, type, params));
        return id;
    }

    public Result removeReward(String rewardId) {
        for (Chapter c : book.chapters()) {
            for (Quest q : c.quests) {
                if (q.rewards.removeIf(r -> r.id().equals(rewardId))) {
                    return Result.OK;
                }
            }
        }
        return Result.NOT_FOUND;
    }
}
