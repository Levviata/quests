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
import com.levviata.reflibquests.model.Definitions.ChapterGroup;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.RewardTable;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.model.Definitions.DependencyMode;
import com.levviata.reflibquests.model.Definitions.Tristate;

/**
 * Every change the in-game editor can make to a {@link QuestBook}, with its validation. Pure logic, so it can
 * be tested without the game; the server calls it after checking permission, and then saves and re-syncs. All
 * methods check their input (the data comes from clients) and never throw for bad input: they return
 * {@link Result#NOT_FOUND} or {@link Result#INVALID} and leave the book unchanged.
 */
public final class BookEditor {

    public enum Result { OK, NOT_FOUND, INVALID, CYCLE }

    public static final int MAX_TITLE = 1000;
    public static final int MAX_LINES = 200;
    public static final int MAX_LINE = 4000;
    public static final double MAX_COORD = 100_000;
    public static final long MAX_VALUE = 1_000_000_000L;

    /** Task types the editor may add. */
    public static final Set<String> TASK_TYPES = Set.of("item", "checkmark", "xp", "kill", "advancement", "dimension", "location", "custom", "biome", "structure", "observation");
    /** Reward types the editor may add. */
    public static final Set<String> REWARD_TYPES = Set.of("item", "xp", "xp_levels", "command", "toast", "advancement", "custom");
    /** Reward types that draw from a reward table (given as {@code table_id}). */
    public static final Set<String> TABLE_REWARD_TYPES = Set.of("choice", "random", "loot", "all_table");
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

    /** Puts an icon on a quest or a chapter; an empty icon clears it. {@code kind} is {@code quest} or {@code chapter}. */
    public Result setIcon(String kind, String id, DataNode.Compound icon) {
        if (kind.equals("book")) {
            book.settings = icon.entries().isEmpty() ? book.settings.without("icon") : book.settings.with("icon", icon);
            return Result.OK;
        }
        if (kind.equals("quest")) {
            Quest q = book.quest(id);
            if (q == null) {
                return Result.NOT_FOUND;
            }
            q.icon = icon;
            return Result.OK;
        }
        if (kind.equals("chapter")) {
            Chapter c = book.chapter(id);
            if (c == null) {
                return Result.NOT_FOUND;
            }
            c.icon = icon;
            return Result.OK;
        }
        return Result.INVALID;
    }

    /** Sets one chapter-wide property; keys match the Chapter Properties screen. */
    public Result setChapterProp(String id, String key, String value) {
        Chapter c = book.chapter(id);
        if (c == null) {
            return Result.NOT_FOUND;
        }
        if (key == null || value == null) {
            return Result.INVALID;
        }
        boolean on = Boolean.parseBoolean(value);
        switch (key) {
            case "shape" -> {
                String s = value.toLowerCase(Locale.ROOT);
                if (!SHAPES.contains(s)) {
                    return Result.INVALID;
                }
                c.defaultQuestShape = s;
            }
            case "always_invisible" -> c.alwaysInvisible = on;
            case "hide_details" -> c.hideQuestDetailsUntilStartable = on;
            case "hide_deps_visible" -> c.hideQuestUntilDepsVisible = on;
            case "hide_deps_complete" -> c.hideQuestUntilDepsComplete = on;
            case "progression_mode" -> {
                Definitions.ProgressionMode m = Definitions.ProgressionMode.parse(value, null);
                if (m == null) {
                    return Result.INVALID;
                }
                c.progressionMode = m;
            }
            case "repeatable" -> c.defaultRepeatable = on;
            case "sequential" -> c.requireSequentialTasks = on;
            case "hide_dep_lines" -> c.defaultHideDependencyLines = on;
            case "subtitle" -> {
                // lines are separated by a literal \n; an empty text removes the subtitle
                List<String> lines = new ArrayList<>();
                if (!value.isBlank()) {
                    for (String part : value.split("\\\\n")) {
                        String l = clean(part.strip(), 200);
                        if (l == null) {
                            return Result.INVALID;
                        }
                        lines.add(l);
                    }
                }
                if (lines.size() > 10) {
                    return Result.INVALID;
                }
                c.subtitle = lines;
            }
            case "disable_toast" -> c.disableToast = on;
            case "hide_text" -> c.hideTextUntilComplete = on;
            case "consume_items" -> {
                if (!List.of("default", "true", "false").contains(value)) {
                    return Result.INVALID;
                }
                c.defaultConsumeItems = value.equals("default") ? Tristate.DEFAULT : value.equals("true") ? Tristate.TRUE : Tristate.FALSE;
            }
            case "quest_size" -> {
                double d;
                try {
                    d = Double.parseDouble(value);
                } catch (NumberFormatException ex) {
                    return Result.INVALID;
                }
                if (!(d >= 0.0625 && d <= 64.0)) {
                    return Result.INVALID;
                }
                c.defaultQuestSize = d;
            }
            case "min_width" -> {
                int n;
                try {
                    n = Integer.parseInt(value.strip());
                } catch (NumberFormatException ex) {
                    return Result.INVALID;
                }
                if (n < 0 || n > 1000) {
                    return Result.INVALID;
                }
                c.minWindowWidth = n;
            }
            case "tags" -> {
                List<String> tags = new ArrayList<>();
                for (String part : value.split(",")) {
                    String t = part.strip();
                    if (!t.isEmpty()) {
                        if (t.length() > 40) {
                            return Result.INVALID;
                        }
                        tags.add(t);
                    }
                }
                if (tags.size() > 20) {
                    return Result.INVALID;
                }
                c.tags = tags;
            }
            case "autofocus" -> {
                if (!value.isEmpty() && c.quests.stream().noneMatch(q -> q.id.equals(value))) {
                    return Result.INVALID;
                }
                c.autofocusId = value;
            }
            default -> {
                return Result.INVALID;
            }
        }
        return Result.OK;
    }

    // ------------------------------------------------------------------ book settings

    /** Keys of the whole book: the ones that change how the book behaves, named as in the book's own file. */
    public Result setBookProp(String key, String value) {
        if (key == null || value == null) {
            return Result.INVALID;
        }
        String v = value.strip();
        DataNode.Compound s = book.settings;
        switch (key) {
            case "title" -> {
                String t = clean(v, MAX_TITLE);
                if (t == null || t.isEmpty()) {
                    return Result.INVALID;
                }
                s = s.with("title", t);
            }
            case "default_quest_shape" -> {
                if (!SHAPES.contains(v.toLowerCase(Locale.ROOT))) {
                    return Result.INVALID;
                }
                s = s.with(key, v.toLowerCase(Locale.ROOT));
            }
            case "default_consume_items", "default_quest_disable_jei", "default_reward_team", "show_lock_icons" -> {
                if (!v.equals("true") && !v.equals("false")) {
                    return Result.INVALID;
                }
                s = s.with(key, v.equals("true"));
            }
            case "default_autoclaim_rewards" -> {
                if (!AUTO_CLAIM.contains(v) || v.equals("default")) {
                    return Result.INVALID;
                }
                s = s.with(key, v);
            }
            case "disable_gui", "drop_book_on_death", "drop_loot_crates", "hide_excluded_quests", "pause_game",
                    "suppress_all_autoclaiming", "disable_toast" -> {
                if (!v.equals("true") && !v.equals("false")) {
                    return Result.INVALID;
                }
                s = s.with(key, v.equals("true"));
            }
            case "emergency_items_cooldown" -> {
                int n;
                try {
                    n = Integer.parseInt(v);
                } catch (NumberFormatException ex) {
                    return Result.INVALID;
                }
                if (n < 0 || n > 10_000_000) {
                    return Result.INVALID;
                }
                s = s.with(key, n);
            }
            case "grid_scale" -> {
                double d;
                try {
                    d = Double.parseDouble(v);
                } catch (NumberFormatException ex) {
                    return Result.INVALID;
                }
                if (!(d >= 0.0625 && d <= 16.0)) {
                    return Result.INVALID;
                }
                s = s.with(key, d);
            }
            case "lock_message" -> {
                String t = clean(v, 200);
                if (t == null) {
                    return Result.INVALID;
                }
                s = s.with(key, t);
            }
            case "tags" -> {
                List<DataNode> tags = new ArrayList<>();
                for (String part : v.split(",")) {
                    String t = part.strip();
                    if (!t.isEmpty()) {
                        if (t.length() > 40) {
                            return Result.INVALID;
                        }
                        tags.add(new DataNode.Str(t));
                    }
                }
                if (tags.size() > 20) {
                    return Result.INVALID;
                }
                s = s.with(key, new DataNode.Lst(tags));
            }
            case "loot_boss", "loot_monster", "loot_passive" -> {
                int n;
                try {
                    n = Integer.parseInt(v);
                } catch (NumberFormatException ex) {
                    return Result.INVALID;
                }
                if (n < 0 || n > 1_000_000) {
                    return Result.INVALID;
                }
                DataNode.Compound weights = s.getCompound("loot_crate_no_drop");
                s = s.with("loot_crate_no_drop", weights.with(key.substring(5), n)); // boss, monster, passive
            }
            case "detection_delay" -> {
                int n;
                try {
                    n = Integer.parseInt(v);
                } catch (NumberFormatException ex) {
                    return Result.INVALID;
                }
                if (n < 1 || n > 12_000) {
                    return Result.INVALID;
                }
                s = s.with(key, n);
            }
            case "progression_mode" -> {
                Definitions.ProgressionMode m = Definitions.ProgressionMode.parse(v, null);
                if (m == null || m == Definitions.ProgressionMode.DEFAULT) {
                    return Result.INVALID;
                }
                book.progressionMode = m;
                return Result.OK;
            }
            case "fallback_locale" -> {
                if (!v.matches("[a-z]{2}_[a-z]{2}")) {
                    return Result.INVALID;
                }
                book.fallbackLocale = v;
                return Result.OK;
            }
            default -> {
                return Result.INVALID;
            }
        }
        book.settings = s;
        return Result.OK;
    }

    // ------------------------------------------------------------------ chapter groups

    /** @return the new group, or null if the title is invalid; {@code wantedId} lets a client show it before the server answers */
    public ChapterGroup createGroup(String title, String wantedId) {
        String t = clean(title, MAX_TITLE);
        if (t == null || t.isEmpty()) {
            return null;
        }
        boolean usable = wantedId != null && wantedId.matches("[0-9A-F]{16}") && !wantedId.equals("0000000000000000")
                && !allIds().contains(wantedId);
        ChapterGroup g = new ChapterGroup(usable ? wantedId : newId());
        g.title = t;
        book.groups().add(g);
        return g;
    }

    private ChapterGroup group(String id) {
        for (ChapterGroup g : book.groups()) {
            if (g.id.equals(id)) {
                return g;
            }
        }
        return null;
    }

    public Result renameGroup(String id, String title) {
        ChapterGroup g = group(id);
        String t = clean(title, MAX_TITLE);
        if (g == null) {
            return Result.NOT_FOUND;
        }
        if (t == null || t.isEmpty()) {
            return Result.INVALID;
        }
        g.title = t;
        return Result.OK;
    }

    /** Its chapters stay; they just no longer belong to a group. */
    public Result deleteGroup(String id) {
        ChapterGroup g = group(id);
        if (g == null) {
            return Result.NOT_FOUND;
        }
        for (Chapter c : book.chapters()) {
            if (c.groupId.equals(id)) {
                c.groupId = "";
            }
        }
        book.groups().remove(g);
        return Result.OK;
    }

    /**
     * Makes a group "held" by a chapter: the chapter's row in the list opens the group, with the group's chapters nested below.
     * An empty chapter id lets the group go. A chapter cannot hold a group it is itself in.
     */
    public Result setGroupChapter(String groupId, String chapterId) {
        ChapterGroup g = group(groupId);
        if (g == null) {
            return Result.NOT_FOUND;
        }
        if (chapterId == null) {
            return Result.INVALID;
        }
        if (chapterId.isEmpty()) {
            g.raw = g.raw.without("chapter");
            return Result.OK;
        }
        Chapter c = book.chapter(chapterId);
        if (c == null) {
            return Result.NOT_FOUND;
        }
        if (c.groupId.equals(groupId)) {
            return Result.INVALID;
        }
        for (ChapterGroup other : book.groups()) {
            if (other != g && chapterId.equals(other.raw.getString("chapter", ""))) {
                other.raw = other.raw.without("chapter"); // a chapter holds one group
            }
        }
        g.raw = g.raw.with("chapter", chapterId);
        return Result.OK;
    }

    /** @param groupId a group's id, or empty for no group */
    public Result setChapterGroup(String chapterId, String groupId) {
        Chapter c = book.chapter(chapterId);
        if (c == null) {
            return Result.NOT_FOUND;
        }
        if (groupId == null || (!groupId.isEmpty() && group(groupId) == null)) {
            return Result.INVALID;
        }
        c.groupId = groupId;
        return Result.OK;
    }

    /** Move a chapter one place up (-1) or down (+1) among the chapters of its own group. */
    public Result moveChapter(String chapterId, int delta) {
        Chapter c = book.chapter(chapterId);
        if (c == null) {
            return Result.NOT_FOUND;
        }
        if (delta != -1 && delta != 1) {
            return Result.INVALID;
        }
        List<Chapter> all = book.chapters();
        int at = all.indexOf(c);
        int to = at + delta;
        while (to >= 0 && to < all.size() && !all.get(to).groupId.equals(c.groupId)) {
            to += delta;
        }
        if (to < 0 || to >= all.size()) {
            return Result.OK; // already first or last
        }
        Chapter other = all.get(to);
        all.set(at, other);
        all.set(to, c);
        for (int i = 0; i < all.size(); i++) {
            all.get(i).order = i;
        }
        return Result.OK;
    }

    public Result moveGroup(String id, int delta) {
        ChapterGroup g = group(id);
        if (g == null) {
            return Result.NOT_FOUND;
        }
        if (delta != -1 && delta != 1) {
            return Result.INVALID;
        }
        List<ChapterGroup> all = book.groups();
        int at = all.indexOf(g);
        int to = at + delta;
        if (to >= 0 && to < all.size()) {
            all.set(at, all.get(to));
            all.set(to, g);
        }
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
        q.size = c.defaultQuestSize; // new quests start with the chapter's size
        q.title = "New quest";
        q.x = snap(x);
        q.y = snap(y);
        q.tasks.add(new Task(newId(), "checkmark", DataNode.Compound.empty()));
        c.quests.add(q);
        book.reindex();
        return q;
    }

    /**
     * Sets the curve of a quest's dependency lines: points maps a dependency id to [x1, y1, x2, y2]; lines that are
     * not named go back to straight. Ids that are not dependencies of the quest are refused.
     */
    public Result setBezier(String questId, DataNode.Compound points) {
        Quest q = book.quest(questId);
        if (q == null) {
            return Result.NOT_FOUND;
        }
        java.util.Map<String, List<Double>> next = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, DataNode> e : points.entries().entrySet()) {
            List<Double> pts = ControlPoints.parse(e.getValue());
            if (pts == null || !q.dependencies.contains(e.getKey())) {
                return Result.INVALID;
            }
            next.put(e.getKey(), pts);
        }
        q.depControlPts = next;
        return Result.OK;
    }

    public static final int MAX_PASTE = 200;

    /**
     * Copies quests (with their tasks and rewards, under new ids) into a chapter. The group keeps its layout: the top left
     * quest of the sources lands on {@code x, y}. Dependencies between the copied quests point at the copies; the others are
     * kept, or dropped when {@code keepDependencies} is false.
     *
     * @return the new quest ids in the order of the sources, or null if nothing could be pasted
     */
    public List<String> pasteQuests(String chapterId, List<String> sourceIds, double x, double y, boolean keepDependencies) {
        Chapter target = book.chapter(chapterId);
        if (target == null || !coordOk(x) || !coordOk(y) || sourceIds.isEmpty() || sourceIds.size() > MAX_PASTE) {
            return null;
        }
        List<Quest> sources = new ArrayList<>();
        for (String id : sourceIds) {
            Quest src = book.quest(id);
            if (src != null && sources.stream().noneMatch(o -> o.id.equals(id))) {
                sources.add(src);
            }
        }
        if (sources.isEmpty()) {
            return null;
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        for (Quest src : sources) {
            minX = Math.min(minX, src.x);
            minY = Math.min(minY, src.y);
        }
        java.util.Map<String, String> renamed = new java.util.LinkedHashMap<>();
        for (Quest src : sources) {
            renamed.put(src.id, newId());
        }
        List<String> created = new ArrayList<>();
        for (Quest src : sources) {
            Quest cp = DeepCopy.quest(src, renamed.get(src.id));
            cp.x = snap(x + (src.x - minX));
            cp.y = snap(y + (src.y - minY));
            List<Task> tasks = new ArrayList<>(cp.tasks);
            cp.tasks.clear();
            for (Task t : tasks) {
                cp.tasks.add(new Task(newId(), t.type(), t.params()));
            }
            List<Reward> rewards = new ArrayList<>(cp.rewards);
            cp.rewards.clear();
            for (Reward r : rewards) {
                cp.rewards.add(new Reward(newId(), r.type(), r.params()));
            }
            List<String> deps = new ArrayList<>(cp.dependencies);
            cp.dependencies.clear();
            for (String d : deps) {
                if (renamed.containsKey(d)) {
                    cp.dependencies.add(renamed.get(d));
                } else if (keepDependencies && book.quest(d) != null) {
                    cp.dependencies.add(d);
                }
            }
            target.quests.add(cp);
            created.add(cp.id);
        }
        book.reindex();
        return created;
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

    /** Several moves as one edit: {@code moves} is a list of {@code {id, x, y}}. Nothing moves unless all of them are valid. */
    public Result moveQuests(DataNode.Compound a) {
        List<DataNode> moves = a.getList("moves");
        if (moves.isEmpty() || moves.size() > MAX_PASTE) {
            return Result.INVALID;
        }
        for (DataNode n : moves) {
            if (!(n instanceof DataNode.Compound m)) {
                return Result.INVALID;
            }
            if (book.quest(m.getString("id", "")) == null) {
                return Result.NOT_FOUND;
            }
            if (!coordOk(m.getDouble("x", Double.NaN)) || !coordOk(m.getDouble("y", Double.NaN))) {
                return Result.INVALID;
            }
        }
        for (DataNode n : moves) {
            DataNode.Compound m = (DataNode.Compound) n;
            moveQuest(m.getString("id", ""), m.getDouble("x", Double.NaN), m.getDouble("y", Double.NaN));
        }
        return Result.OK;
    }

    /** Several deletions as one edit: {@code ids} is a list of quest ids; unknown ones are skipped. */
    public Result deleteQuests(DataNode.Compound a) {
        List<DataNode> ids = a.getList("ids");
        if (ids.isEmpty() || ids.size() > 1000) {
            return Result.INVALID;
        }
        boolean any = false;
        for (DataNode n : ids) {
            if (n instanceof DataNode.Str s && deleteQuest(s.value()) == Result.OK) {
                any = true;
            }
        }
        return any ? Result.OK : Result.NOT_FOUND;
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
                q.depControlPts.keySet().removeIf(questIds::contains);
            }
            c.links.removeIf(l -> questIds.contains(l.linkedQuest()));
        }
    }

    public Result setTitleOnly(String questId, String title) {
        Quest q = book.quest(questId);
        if (q == null) {
            return Result.NOT_FOUND;
        }
        String t = clean(title, MAX_TITLE);
        if (t == null) {
            return Result.INVALID;
        }
        q.title = t;
        return Result.OK;
    }

    public Result setSubtitleOnly(String questId, String subtitle) {
        Quest q = book.quest(questId);
        if (q == null) {
            return Result.NOT_FOUND;
        }
        String s = clean(subtitle, MAX_TITLE);
        if (s == null) {
            return Result.INVALID;
        }
        q.subtitle = s;
        return Result.OK;
    }

    /** Adds lines at the end of the description; works on the server's current text, so quick edits cannot overwrite each other. */
    public Result addLines(String questId, List<String> lines) {
        Quest q = book.quest(questId);
        if (q == null) {
            return Result.NOT_FOUND;
        }
        if (lines == null || lines.isEmpty() || q.description.size() + lines.size() > MAX_LINES) {
            return Result.INVALID;
        }
        for (String l : lines) {
            if (l == null || l.length() > MAX_LINE || l.indexOf((char) 10) >= 0) {
                return Result.INVALID;
            }
        }
        List<String> next = new ArrayList<>(q.description);
        next.addAll(lines);
        q.description = next;
        return Result.OK;
    }

    /** Moves a picture of a chapter to a new position (in map units). */
    public Result moveImage(String imageId, double x, double y) {
        if (!coordOk(x) || !coordOk(y)) {
            return Result.INVALID;
        }
        for (Chapter c : book.chapters()) {
            for (int i = 0; i < c.images.size(); i++) {
                Definitions.ChapterImage img = c.images.get(i);
                if (img.id().equals(imageId)) {
                    c.images.set(i, new Definitions.ChapterImage(img.id(), img.raw().with("x", Math.round(x * 4.0) / 4.0).with("y", Math.round(y * 4.0) / 4.0)));
                    return Result.OK;
                }
            }
        }
        return Result.NOT_FOUND;
    }

    /** Several properties of a picture as one edit: x, y, width, height, rotation, alpha, order, dev (true/false) and image. */
    public Result setImageProps(String imageId, DataNode.Compound props) {
        if (props == null || props.entries().isEmpty()) {
            return Result.INVALID;
        }
        for (Chapter c : book.chapters()) {
            for (int i = 0; i < c.images.size(); i++) {
                Definitions.ChapterImage img = c.images.get(i);
                if (!img.id().equals(imageId)) {
                    continue;
                }
                DataNode.Compound raw = img.raw();
                for (java.util.Map.Entry<String, DataNode> e : props.entries().entrySet()) {
                    String key = e.getKey();
                    String v = e.getValue() instanceof DataNode.Str s ? s.value().strip()
                            : e.getValue() instanceof DataNode.Dbl d ? String.valueOf(d.value())
                            : e.getValue() instanceof DataNode.Int n ? String.valueOf(n.value()) : null;
                    if (v == null) {
                        return Result.INVALID;
                    }
                    try {
                        switch (key) {
                            case "x", "y" -> {
                                double d = Double.parseDouble(v);
                                if (!coordOk(d)) {
                                    return Result.INVALID;
                                }
                                raw = raw.with(key, d);
                            }
                            case "width", "height" -> {
                                double d = Double.parseDouble(v);
                                if (!(d >= 0.0625 && d <= 200)) {
                                    return Result.INVALID;
                                }
                                raw = raw.with(key, d);
                            }
                            case "rotation" -> {
                                double d = Double.parseDouble(v);
                                if (!Double.isFinite(d) || Math.abs(d) > 100_000) {
                                    return Result.INVALID;
                                }
                                raw = raw.with(key, d);
                            }
                            case "alpha" -> {
                                int n = Integer.parseInt(v);
                                if (n < 0 || n > 255) {
                                    return Result.INVALID;
                                }
                                raw = raw.with(key, n);
                            }
                            case "order" -> {
                                int n = Integer.parseInt(v);
                                if (Math.abs(n) > 1000) {
                                    return Result.INVALID;
                                }
                                raw = raw.with(key, n);
                            }
                            case "dev" -> {
                                if (!v.equals("true") && !v.equals("false")) {
                                    return Result.INVALID;
                                }
                                raw = v.equals("true") ? raw.with(key, true) : raw.without(key);
                            }
                            case "image" -> {
                                String t = clean(v, 200);
                                if (t == null || t.isEmpty()) {
                                    return Result.INVALID;
                                }
                                raw = raw.with(key, t);
                            }
                            case "color" -> {
                                // the tint: #RRGGBB or a plain number; empty removes it
                                if (v.isEmpty()) {
                                    raw = raw.without(key);
                                } else {
                                    int n = v.startsWith("#") ? Integer.parseInt(v.substring(1), 16) : Integer.parseInt(v);
                                    if (n < 0 || n > 0xFFFFFF) {
                                        return Result.INVALID;
                                    }
                                    raw = n == 0xFFFFFF ? raw.without(key) : raw.with(key, n); // white is the default
                                }
                            }
                            case "title" -> {
                                String t = clean(v, 200);
                                if (t == null) {
                                    return Result.INVALID;
                                }
                                raw = t.isEmpty() ? raw.without(key) : raw.with(key, t); // written to the language file, not the image
                            }
                            case "corner", "position_locked", "text_on_image" -> {
                                if (!v.equals("true") && !v.equals("false")) {
                                    return Result.INVALID;
                                }
                                raw = v.equals("true") ? raw.with(key, true) : raw.without(key);
                            }
                            case "dependency" -> {
                                if (!v.isEmpty() && book.quest(com.levviata.reflibquests.io.Ids.normalize(v)) == null) {
                                    return Result.INVALID;
                                }
                                raw = v.isEmpty() ? raw.without(key) : raw.with(key, com.levviata.reflibquests.io.Ids.normalize(v));
                            }
                            case "text_inset" -> {
                                int n = Integer.parseInt(v);
                                if (n < 0 || n > 50) {
                                    return Result.INVALID;
                                }
                                raw = n == 0 ? raw.without(key) : raw.with(key, n);
                            }
                            case "text_h_align", "text_v_align" -> {
                                if (!List.of("start", "middle", "end").contains(v)) {
                                    return Result.INVALID;
                                }
                                raw = v.equals("middle") ? raw.without(key) : raw.with(key, v); // middle is the default
                            }
                            case "click_type", "click_data" -> {
                                String t = clean(v, 500);
                                if (t == null) {
                                    return Result.INVALID;
                                }
                                DataNode.Compound click = raw.getCompound("click_action");
                                String field = key.equals("click_type") ? "type" : "data";
                                click = t.isEmpty() ? click.without(field) : click.with(field, t);
                                raw = click.entries().isEmpty() ? raw.without("click_action") : raw.with("click_action", click);
                            }
                            case "tags" -> {
                                List<DataNode> list = new ArrayList<>();
                                for (String part : v.split(",")) {
                                    String t = clean(part, 60);
                                    if (t == null) {
                                        return Result.INVALID;
                                    }
                                    if (!t.isEmpty()) {
                                        list.add(new DataNode.Str(t));
                                    }
                                }
                                raw = list.isEmpty() ? raw.without(key) : raw.with(key, new DataNode.Lst(list));
                            }
                            default -> {
                                return Result.INVALID;
                            }
                        }
                    } catch (NumberFormatException ex) {
                        return Result.INVALID;
                    }
                }
                c.images.set(i, new Definitions.ChapterImage(img.id(), raw));
                return Result.OK;
            }
        }
        return Result.NOT_FOUND;
    }

    /** A second placement (a "link") of a quest on a chapter's map; @return its id, or null if the chapter or quest is unknown */
    public String createLink(String chapterId, String questId, double x, double y) {
        Chapter c = book.chapter(chapterId);
        if (c == null || book.quest(questId) == null || !coordOk(x) || !coordOk(y) || c.links.size() >= 1000) {
            return null;
        }
        String id = newId();
        c.links.add(new Definitions.QuestLink(id, questId, snap(x), snap(y), DataNode.Compound.empty()));
        return id;
    }

    public Result deleteLink(String linkId) {
        for (Chapter c : book.chapters()) {
            if (c.links.removeIf(l -> l.id().equals(linkId))) {
                return Result.OK;
            }
        }
        return Result.NOT_FOUND;
    }

    public Result deleteImage(String imageId) {
        for (Chapter c : book.chapters()) {
            if (c.images.removeIf(img -> img.id().equals(imageId))) {
                return Result.OK;
            }
        }
        return Result.NOT_FOUND;
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

    /** Sets one property of a quest by name; values are text ({@code true}/{@code false}/{@code default}, numbers, names). */
    public Result setProp(String questId, String key, String value) {
        Quest q = book.quest(questId);
        if (q == null) {
            return Result.NOT_FOUND;
        }
        String v = value == null ? "" : value.trim();
        try {
            switch (key) {
                case "shape":
                    return setShape(questId, v);
                case "size": {
                    double d = Double.parseDouble(v);
                    if (!(d >= 0.0625 && d <= 64.0)) {
                        return Result.INVALID;
                    }
                    q.size = d;
                    return Result.OK;
                }
                case "z": {
                    double d = Double.parseDouble(v);
                    if (!(d >= -1000 && d <= 1000)) {
                        return Result.INVALID;
                    }
                    q.z = d;
                    return Result.OK;
                }
                case "dependency_requirement":
                    q.dependencyMode = DependencyMode.valueOf(v.toUpperCase(Locale.ROOT));
                    return Result.OK;
                case "min_required_dependencies": {
                    int n = Integer.parseInt(v);
                    if (n < 0 || n > 50) {
                        return Result.INVALID;
                    }
                    q.minRequiredDependencies = n;
                    return Result.OK;
                }
                case "invisible_until_tasks": {
                    int n = Integer.parseInt(v);
                    if (n < 0 || n > 20) {
                        return Result.INVALID;
                    }
                    q.invisibleUntilTasks = n;
                    return Result.OK;
                }
                case "repeat_cooldown": {
                    long n = Long.parseLong(v);
                    if (n < 0 || n > 31_536_000L) {
                        return Result.INVALID;
                    }
                    q.repeatCooldown = (int) n;
                    return Result.OK;
                }
                case "optional":
                    q.optional = Boolean.parseBoolean(v);
                    return Result.OK;
                case "invisible":
                    q.invisible = Boolean.parseBoolean(v);
                    return Result.OK;
                case "disable_toast":
                    q.disableToast = Boolean.parseBoolean(v);
                    return Result.OK;
                case "hide_lock_icon":
                    q.hideLockIcon = Boolean.parseBoolean(v);
                    return Result.OK;
                case "ignore_reward_blocking":
                    q.ignoreRewardBlocking = Boolean.parseBoolean(v);
                    return Result.OK;
                case "progression_mode":
                    q.progressionMode = Definitions.ProgressionMode.valueOf(v.toUpperCase(Locale.ROOT));
                    return Result.OK;
                default:
                    break;
            }
        } catch (IllegalArgumentException ex) {
            return Result.INVALID;
        }
        Tristate tri = v.equalsIgnoreCase("true") ? Tristate.TRUE : v.equalsIgnoreCase("false") ? Tristate.FALSE
                : v.equalsIgnoreCase("default") ? Tristate.DEFAULT : null;
        if (tri == null) {
            return Result.INVALID;
        }
        switch (key) {
            case "repeatable":
                q.canRepeat = tri;
                return Result.OK;
            case "sequential":
                q.requireSequentialTasks = tri;
                return Result.OK;
            case "hide_details":
                q.hideDetailsUntilStartable = tri;
                return Result.OK;
            case "hide_deps_visible":
                q.hideUntilDepsVisible = tri;
                return Result.OK;
            case "hide_deps_complete":
                q.hideUntilDepsComplete = tri;
                return Result.OK;
            case "hide_dep_lines":
                q.hideDependencyLines = tri;
                return Result.OK;
            case "hide_dependent_lines":
                q.hideDependentLines = tri;
                return Result.OK;
            case "hide_text_until_complete":
                q.hideTextUntilComplete = tri;
                return Result.OK;
            case "disable_jei":
                q.disableJei = tri;
                return Result.OK;
            default:
                return Result.INVALID;
        }
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

    /** Moves a task one place up (-1) or down (+1) in its quest; false at the ends. */
    public Result moveTask(String taskId, int by) {
        Quest q = book.questOfTask(taskId);
        if (q == null) {
            return Result.NOT_FOUND;
        }
        int at = -1;
        for (int i = 0; i < q.tasks.size(); i++) {
            if (q.tasks.get(i).id().equals(taskId)) {
                at = i;
            }
        }
        int to = at + Integer.signum(by);
        if (at < 0 || to < 0 || to >= q.tasks.size()) {
            return Result.INVALID;
        }
        java.util.Collections.swap(q.tasks, at, to);
        book.reindex();
        return Result.OK;
    }

    public Result moveReward(String rewardId, int by) {
        for (Chapter c : book.chapters()) {
            for (Quest q : c.quests) {
                for (int i = 0; i < q.rewards.size(); i++) {
                    if (q.rewards.get(i).id().equals(rewardId)) {
                        int to = i + Integer.signum(by);
                        if (to < 0 || to >= q.rewards.size()) {
                            return Result.INVALID;
                        }
                        java.util.Collections.swap(q.rewards, i, to);
                        return Result.OK;
                    }
                }
            }
        }
        return Result.NOT_FOUND;
    }

    /** The quest's icon becomes the item of one of its tasks. */
    public Result setIconFromTask(String questId, String taskId) {
        Quest q = book.quest(questId);
        if (q == null) {
            return Result.NOT_FOUND;
        }
        for (Task t : q.tasks) {
            if (t.id().equals(taskId)) {
                DataNode item = t.params().get("item");
                if (item instanceof DataNode.Compound c) {
                    q.icon = c.without("count");
                    return Result.OK;
                }
                if (item instanceof DataNode.Str s) {
                    q.icon = DataNode.Compound.empty().with("id", s.value());
                    return Result.OK;
                }
                return Result.INVALID;
            }
        }
        return Result.NOT_FOUND;
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
        boolean tableType = TABLE_REWARD_TYPES.contains(type);
        if (q == null || !(REWARD_TYPES.contains(type) || tableType) || params == null || q.rewards.size() >= 20) {
            return null;
        }
        if (type.equals("item") && !params.has("item")) {
            return null;
        }
        if (tableType) {
            String tid = params.getString("table_id", "");
            if (!book.tables().containsKey(tid)) {
                return null;
            }
            params = DataNode.Compound.empty().with("table_id", tid);
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

    // ------------------------------------------------------------------ task and reward settings

    public static final Set<String> AUTO_CLAIM = Set.of("default", "disabled", "enabled", "no_toast", "invisible");

    /** An empty or blank text removes the key; text with a line break or over the limit is refused (null). */
    private static DataNode.Compound withText(DataNode.Compound p, String key, String value, int max) {
        String t = clean(value, max);
        if (t == null) {
            return null;
        }
        return t.isEmpty() ? p.without(key) : p.with(key, t);
    }

    /** The parameter that names what a task is about (entity, dimension, biome, structure, advancement), or null. */
    public static String targetKey(String type) {
        return switch (type) {
            case "kill" -> "entity";
            case "dimension" -> "dimension";
            case "biome" -> "biome";
            case "structure" -> "structure";
            case "advancement" -> "advancement";
            default -> null;
        };
    }

    /** Keys: {@code title}, {@code optional_task} (true/false), {@code consume_items} (item tasks: default, true or false). */
    public Result setTaskProp(String taskId, String key, String value) {
        Quest q = book.questOfTask(taskId);
        if (q == null || key == null || value == null) {
            return Result.NOT_FOUND;
        }
        for (int i = 0; i < q.tasks.size(); i++) {
            Task t = q.tasks.get(i);
            if (!t.id().equals(taskId)) {
                continue;
            }
            DataNode.Compound p = t.params();
            switch (key) {
                case "title" -> p = withText(p, "title", value, 200);
                case "optional_task" -> {
                    if (!value.equals("true") && !value.equals("false")) {
                        return Result.INVALID;
                    }
                    p = value.equals("true") ? p.with("optional_task", true) : p.without("optional_task");
                }
                case "consume_items" -> {
                    if (!t.type().equals("item") || !List.of("default", "true", "false").contains(value)) {
                        return Result.INVALID;
                    }
                    p = value.equals("default") ? p.without("consume_items") : p.with("consume_items", value.equals("true"));
                }
                case "disable_toast", "task_screen_only" -> {
                    if (!value.equals("true") && !value.equals("false")) {
                        return Result.INVALID;
                    }
                    p = value.equals("true") ? p.with(key, true) : p.without(key);
                }
                case "only_from_crafting" -> {
                    if (!t.type().equals("item") || !List.of("default", "true", "false").contains(value)) {
                        return Result.INVALID;
                    }
                    p = value.equals("default") ? p.without(key) : p.with(key, value.equals("true"));
                }
                case "match_components" -> {
                    if (!t.type().equals("item") || !List.of("default", "fuzzy", "exact").contains(value)) {
                        return Result.INVALID;
                    }
                    p = value.equals("default") ? p.without(key) : p.with(key, value);
                }
                case "box" -> {
                    // "x, y, z, width, height, depth" of a location task
                    String[] parts = value.split("[,\\s]+");
                    if (!t.type().equals("location") || parts.length != 6) {
                        return Result.INVALID;
                    }
                    int[] n = new int[6];
                    for (int k = 0; k < 6; k++) {
                        try {
                            n[k] = Integer.parseInt(parts[k].strip());
                        } catch (NumberFormatException ex) {
                            return Result.INVALID;
                        }
                        if (Math.abs(n[k]) > MAX_COORD || k >= 3 && n[k] < 1) {
                            return Result.INVALID;
                        }
                    }
                    p = p.with("position", new DataNode.Lst(List.of(new DataNode.Int(n[0]), new DataNode.Int(n[1]), new DataNode.Int(n[2]))))
                            .with("size", new DataNode.Lst(List.of(new DataNode.Int(n[3]), new DataNode.Int(n[4]), new DataNode.Int(n[5]))));
                }
                case "target" -> {
                    String pk = targetKey(t.type());
                    String v = value.strip();
                    if (pk == null || !v.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                        return Result.INVALID;
                    }
                    p = p.with(pk, v);
                }
                default -> {
                    return Result.INVALID;
                }
            }
            if (p == null) {
                return Result.INVALID;
            }
            q.tasks.set(i, new Task(t.id(), t.type(), p));
            book.reindex();
            return Result.OK;
        }
        return Result.NOT_FOUND;
    }

    /**
     * Keys: {@code title}, {@code auto} (default, disabled, enabled, no_toast, invisible) and the amount of the reward:
     * {@code count} (item), {@code xp}, {@code xp_levels}.
     */
    public Result setRewardProp(String rewardId, String key, String value) {
        if (key == null || value == null) {
            return Result.INVALID;
        }
        for (Chapter c : book.chapters()) {
            for (Quest q : c.quests) {
                for (int i = 0; i < q.rewards.size(); i++) {
                    Reward r = q.rewards.get(i);
                    if (!r.id().equals(rewardId)) {
                        continue;
                    }
                    DataNode.Compound p = r.params();
                    switch (key) {
                        case "title" -> p = withText(p, "title", value, 200);
                        case "auto" -> {
                            if (!AUTO_CLAIM.contains(value)) {
                                return Result.INVALID;
                            }
                            p = value.equals("default") ? p.without("auto") : p.with("auto", value);
                        }
                        case "count", "xp", "xp_levels" -> {
                            boolean fits = key.equals("count") ? r.type().equals("item") : r.type().equals(key);
                            long n;
                            try {
                                n = Long.parseLong(value.strip());
                            } catch (NumberFormatException ex) {
                                return Result.INVALID;
                            }
                            if (!fits || n < 1 || n > (key.equals("xp_levels") ? 10_000 : MAX_VALUE)) {
                                return Result.INVALID;
                            }
                            p = p.with(key, (int) Math.min(n, Integer.MAX_VALUE));
                        }
                        case "command", "description", "advancement", "criterion" -> {
                            String need = key.equals("command") ? "command" : key.equals("description") ? "toast" : "advancement";
                            if (!r.type().equals(need)) {
                                return Result.INVALID;
                            }
                            String v = value.strip();
                            if (key.equals("advancement") && !v.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                                return Result.INVALID;
                            }
                            p = withText(p, key, v, 500);
                        }
                        case "exclude_from_claim_all", "ignore_reward_blocking", "disable_reward_screen_blur", "only_one" -> {
                            if (!value.equals("true") && !value.equals("false")) {
                                return Result.INVALID;
                            }
                            p = value.equals("true") ? p.with(key, true) : p.without(key);
                        }
                        case "team_reward" -> {
                            if (!List.of("default", "true", "false").contains(value)) {
                                return Result.INVALID;
                            }
                            p = value.equals("default") ? p.without(key) : p.with(key, value.equals("true"));
                        }
                        case "permission_level" -> {
                            if (!r.type().equals("command") || !value.matches("[0-4]")) {
                                return Result.INVALID;
                            }
                            p = p.with("permission_level", Integer.parseInt(value));
                        }
                        case "silent" -> {
                            if (!r.type().equals("command") || !(value.equals("true") || value.equals("false"))) {
                                return Result.INVALID;
                            }
                            p = value.equals("true") ? p.with("silent", true) : p.without("silent");
                        }
                        default -> {
                            return Result.INVALID;
                        }
                    }
                    if (p == null) {
                        return Result.INVALID;
                    }
                    q.rewards.set(i, new Reward(r.id(), r.type(), p));
                    book.reindex();
                    return Result.OK;
                }
            }
        }
        return Result.NOT_FOUND;
    }

    // ------------------------------------------------------------------ reward tables

    public static final int MAX_TABLE_ENTRIES = 200;
    public static final float MAX_WEIGHT = 1_000_000f;

    /** @return the new, empty table, or null if the title is invalid */
    public RewardTable createTable(String title) {
        return createTable(title, "");
    }

    /** As above, with an id chosen by the caller (16 upper-case hex digits, unused) so a client can show the table at once. */
    public RewardTable createTable(String title, String wantedId) {
        String t = clean(title, MAX_TITLE);
        if (t == null || t.isEmpty()) {
            return null;
        }
        boolean usable = wantedId != null && wantedId.matches("[0-9A-F]{16}") && !wantedId.equals("0000000000000000")
                && !allIds().contains(wantedId);
        RewardTable table = new RewardTable(usable ? wantedId : newId());
        table.title = t;
        Set<String> names = new HashSet<>();
        book.tables().values().forEach(x -> names.add(x.filename.toLowerCase(Locale.ROOT)));
        String base = slug(t);
        String name = base;
        int n = 2;
        while (names.contains(name)) {
            name = base + "_" + n++;
        }
        table.filename = name;
        table.order = book.tables().values().stream().mapToInt(x -> x.order).max().orElse(-1) + 1;
        book.tables().put(table.id, table);
        return table;
    }

    public Result renameTable(String id, String title) {
        RewardTable t = book.tables().get(id);
        String s = clean(title, MAX_TITLE);
        if (t == null) {
            return Result.NOT_FOUND;
        }
        if (s == null || s.isEmpty()) {
            return Result.INVALID;
        }
        t.title = s;
        return Result.OK;
    }

    /** A table that a reward still points at is kept: deleting it would leave that reward with nothing to give. */
    public Result deleteTable(String id) {
        if (book.tables().get(id) == null) {
            return Result.NOT_FOUND;
        }
        for (Chapter c : book.chapters()) {
            for (Quest q : c.quests) {
                for (Reward r : q.rewards) {
                    if (id.equalsIgnoreCase(r.params().getString("table_id", ""))) {
                        return Result.INVALID;
                    }
                }
            }
        }
        book.tables().remove(id);
        return Result.OK;
    }

    /** Keys: {@code empty_weight} (chance of nothing, in the same units as the entry weights) and {@code loot_size}. */
    public Result setTableProp(String id, String key, String value) {
        RewardTable t = book.tables().get(id);
        if (t == null) {
            return Result.NOT_FOUND;
        }
        try {
            switch (key) {
                case "empty_weight" -> {
                    float v = Float.parseFloat(value);
                    if (!(v >= 0 && v <= MAX_WEIGHT)) {
                        return Result.INVALID;
                    }
                    t.emptyWeight = v;
                }
                case "loot_size" -> {
                    int v = Integer.parseInt(value);
                    if (v < 0 || v > 100) {
                        return Result.INVALID;
                    }
                    t.lootSize = v;
                }
                default -> {
                    return Result.INVALID;
                }
            }
        } catch (NumberFormatException ex) {
            return Result.INVALID;
        }
        return Result.OK;
    }

    /** @return the new entry's reward id, or null if the table is unknown, full, or the entry is not allowed */
    public String addTableEntry(String tableId, String type, DataNode.Compound params, float weight) {
        return addTableEntry(tableId, type, params, weight, "");
    }

    /** As above, with an id chosen by the caller (16 upper-case hex digits, unused), so a client can show the entry at once. */
    public String addTableEntry(String tableId, String type, DataNode.Compound params, float weight, String wantedId) {
        RewardTable t = book.tables().get(tableId);
        if (t == null || !REWARD_TYPES.contains(type) || params == null || t.entries.size() >= MAX_TABLE_ENTRIES
                || !(weight > 0 && weight <= MAX_WEIGHT)) {
            return null;
        }
        if (type.equals("item") && !params.has("item")) {
            return null;
        }
        String id = wantedId != null && wantedId.matches("[0-9A-F]{16}") && !wantedId.equals("0000000000000000")
                && !allIds().contains(wantedId) ? wantedId : newId();
        t.entries.add(new Definitions.WeightedReward(weight, new Reward(id, type, params)));
        return id;
    }

    public Result setEntryWeight(String tableId, String rewardId, float weight) {
        RewardTable t = book.tables().get(tableId);
        if (t == null) {
            return Result.NOT_FOUND;
        }
        if (!(weight > 0 && weight <= MAX_WEIGHT)) {
            return Result.INVALID;
        }
        for (int i = 0; i < t.entries.size(); i++) {
            if (t.entries.get(i).reward().id().equals(rewardId)) {
                t.entries.set(i, new Definitions.WeightedReward(weight, t.entries.get(i).reward()));
                return Result.OK;
            }
        }
        return Result.NOT_FOUND;
    }

    public Result removeTableEntry(String tableId, String rewardId) {
        RewardTable t = book.tables().get(tableId);
        if (t == null) {
            return Result.NOT_FOUND;
        }
        return t.entries.removeIf(w -> w.reward().id().equals(rewardId)) ? Result.OK : Result.NOT_FOUND;
    }
}
