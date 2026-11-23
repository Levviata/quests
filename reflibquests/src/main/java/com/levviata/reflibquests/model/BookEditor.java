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
    public static final Set<String> TASK_TYPES = Set.of("item", "checkmark", "xp", "kill", "advancement", "dimension", "location", "custom", "biome", "structure", "observation", "stat", "fluid",
            "gamestage", "interaction", "forge_energy");
    /** FTB's Interaction types. */
    public static final List<String> INTERACTION_TYPES = List.of("block", "block_tag", "block_state", "entity_type", "entity_type_tag");
    /** Reward types the editor may add. */
    public static final Set<String> REWARD_TYPES = Set.of("item", "xp", "xp_levels", "command", "toast", "advancement", "custom", "gamestage");
    /** Reward types that draw from a reward table (given as {@code table_id}). */
    public static final Set<String> TABLE_REWARD_TYPES = Set.of("choice", "random", "loot", "all_table");
    /** The statistics a stat task can name (the ones this game keeps; names as FTB Quests 26.1.2 writes them, without {@code minecraft:}). */
    public static final List<String> STATS = List.of("mob_kills", "player_kills", "deaths", "jump", "damage_dealt", "damage_taken",
            "play_one_minute", "time_since_death", "walk_one_cm", "sprint_one_cm", "crouch_one_cm", "swim_one_cm", "fall_one_cm",
            "climb_one_cm", "fly_one_cm", "boat_one_cm", "horse_one_cm", "minecart_one_cm", "pig_one_cm", "animals_bred",
            "fish_caught", "talked_to_villager", "traded_with_villager", "leave_game", "drop");
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

    /** The book's Grid Scale: quests and images snap to a multiple of it (0.5 unless the book says otherwise). */
    public static double gridStep(DataNode.Compound settings) {
        double step = settings.getDouble("grid_scale", 0.5);
        return step >= 0.0625 && step <= 16.0 ? step : 0.5;
    }

    private double snap(double v) {
        double step = gridStep(book.settings);
        return Math.round(v / step) * step;
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
        if (kind.equals("task")) {
            Quest q = book.questOfTask(id);
            if (q == null) {
                return Result.NOT_FOUND;
            }
            for (int i = 0; i < q.tasks.size(); i++) {
                Task t = q.tasks.get(i);
                if (t.id().equals(id)) {
                    q.tasks.set(i, new Task(t.id(), t.type(), icon.entries().isEmpty() ? t.params().without("icon") : t.params().with("icon", icon)));
                    book.reindex();
                    return Result.OK;
                }
            }
            return Result.NOT_FOUND;
        }
        if (kind.equals("reward")) {
            for (Chapter c : book.chapters()) {
                for (Quest q : c.quests) {
                    for (int i = 0; i < q.rewards.size(); i++) {
                        Reward r = q.rewards.get(i);
                        if (r.id().equals(id)) {
                            q.rewards.set(i, new Reward(r.id(), r.type(), icon.entries().isEmpty() ? r.params().without("icon") : r.params().with("icon", icon)));
                            book.reindex();
                            return Result.OK;
                        }
                    }
                }
            }
            for (RewardTable tbl : book.tables().values()) {
                for (int ti = 0; ti < tbl.entries.size(); ti++) {
                    Definitions.WeightedReward entry = tbl.entries.get(ti);
                    if (entry.reward().id().equals(id)) {
                        Reward r = entry.reward();
                        tbl.entries.set(ti, new Definitions.WeightedReward(entry.weight(),
                                new Reward(r.id(), r.type(), icon.entries().isEmpty() ? r.params().without("icon") : r.params().with("icon", icon))));
                        return Result.OK;
                    }
                }
            }
            return Result.NOT_FOUND;
        }
        return Result.INVALID;
    }

    /** SNBT text as data, or null when it is not valid NBT (or is absurdly long). */
    public static DataNode.Compound parseNbt(String text) {
        if (text == null || text.length() > 4000) {
            return null;
        }
        try {
            DataNode.Compound c = com.levviata.reflib.api.data.Snbt.parse(text);
            net.minecraft.nbt.JsonToNBT.getTagFromJson(com.levviata.reflib.api.data.Snbt.printCompact(c)); // must be NBT the game can read
            return c;
        } catch (RuntimeException | net.minecraft.nbt.NBTException ex) {
            return null;
        }
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
            case "preset" -> {
                if (value.length() > 64) {
                    return Result.INVALID;
                }
                c.preset = value.equals("None") ? "" : value;
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
            case "reward_team_item", "reward_team_choice", "reward_team_all_table", "reward_team_random", "reward_team_loot",
                    "reward_team_command", "reward_team_custom", "reward_team_xp", "reward_team_xp_levels",
                    "reward_team_advancement", "reward_team_toast", "reward_team_gamestage", "reward_team_currency" -> {
                // "Per-Team Reward by Type": a switch per reward type, kept like FTB 26.1.2 keeps it
                if (!List.of("default", "true", "false").contains(v)) {
                    return Result.INVALID;
                }
                String typeKey = "ftbquests:" + key.substring("reward_team_".length());
                DataNode.Compound byType = s.getCompound("default_reward_team_by_type");
                byType = v.equals("default") ? byType.without(typeKey) : byType.with(typeKey, v.equals("true"));
                s = byType.entries().isEmpty() ? s.without("default_reward_team_by_type") : s.with("default_reward_team_by_type", byType);
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
        q.title = ""; // FTB Quests names a new quest after its first task or its item; the client does that for a quest without a title
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
                    if (img.raw().getBool("position_locked", false)) {
                        return Result.INVALID; // "Lock Position": the image cannot be moved
                    }
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

    /**
     * A new picture on a chapter's map, written the way FTB Quests 26.1.2 writes one: x, y, width, height, rotation, image, id.
     *
     * @return the new image's id, or null if the chapter, the name or the place is not acceptable
     */
    public String createImage(String chapterId, String image, double x, double y, double width, double height) {
        Chapter c = book.chapter(chapterId);
        String name = clean(image, 200);
        if (c == null || name == null || name.isEmpty() || !coordOk(x) || !coordOk(y) || c.images.size() >= 500
                || !(width >= 0.0625 && width <= 200) || !(height >= 0.0625 && height <= 200)) {
            return null;
        }
        String id = newId();
        DataNode.Compound raw = DataNode.Compound.empty().with("x", snap(x)).with("y", snap(y))
                .with("width", width).with("height", height).with("rotation", 0.0).with("image", name).with("id", id);
        c.images.add(new Definitions.ChapterImage(id, raw));
        return id;
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
                case "tags": {
                    List<String> tags = new ArrayList<>();
                    for (String part : v.split(",")) {
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
                    q.tags = tags;
                    return Result.OK;
                }
                case "max_completable_dependents": {
                    int n = Integer.parseInt(v);
                    if (n < 0 || n > 50) {
                        return Result.INVALID;
                    }
                    q.maxCompletableDependents = n;
                    return Result.OK;
                }
                case "icon_scale": {
                    double d = Double.parseDouble(v);
                    if (!(d >= 0.0625 && d <= 16.0)) {
                        return Result.INVALID;
                    }
                    q.iconScale = d;
                    return Result.OK;
                }
                case "guide_page":
                    if (v.length() > 200) {
                        return Result.INVALID;
                    }
                    q.guidePage = v;
                    return Result.OK;
                case "preset":
                    if (v.length() > 64) {
                        return Result.INVALID;
                    }
                    q.preset = v.equals("None") ? "" : v;
                    return Result.OK;
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
        if (type.equals("gamestage") && !p.has("stage")) {
            p = p.with("stage", "myStage"); // FTB's default
        }
        if (type.equals("interaction")) {
            if (!p.has("to_interact")) {
                p = p.with("to_interact", "minecraft:air");
            }
            if (!p.has("interaction_type")) {
                p = p.with("interaction_type", "block");
            }
        }
        if ((type.equals("xp") || type.equals("kill") || type.equals("stat") || type.equals("forge_energy")) && !p.has("value")) {
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
                // the other task types show a picture of their own; the quest takes the same item (FTB lists this entry for every task)
                DataNode.Compound own = t.params().getCompound("icon");
                if (!own.entries().isEmpty()) {
                    q.icon = own;
                    return Result.OK;
                }
                String id;
                switch (t.type()) {
                    case "xp" -> id = "minecraft:experience_bottle";
                    case "dimension" -> id = "minecraft:ender_eye";
                    case "stat" -> id = "minecraft:iron_sword";
                    case "location" -> id = "minecraft:compass";
                    case "advancement" -> id = t.params().getString("advancement", "").endsWith("/root") ? "minecraft:grass" : "minecraft:wheat";
                    case "biome" -> id = "minecraft:sapling";
                    case "structure" -> id = "minecraft:paper";
                    case "fluid" -> id = "minecraft:bucket";
                    case "gamestage" -> id = "minecraft:lever";
                    case "interaction" -> id = "minecraft:diamond_pickaxe";
                    case "forge_energy" -> id = "minecraft:redstone_block";
                    case "kill" -> {
                        String entity = t.params().getString("entity", "");
                        if (entity.isEmpty()) {
                            return Result.INVALID;
                        }
                        q.icon = DataNode.Compound.empty().with("id", "minecraft:spawn_egg").with("tag", DataNode.Compound.empty()
                                .with("EntityTag", DataNode.Compound.empty().with("id", entity.contains(":") ? entity : "minecraft:" + entity)));
                        return Result.OK;
                    }
                    default -> {
                        return Result.INVALID; // checkmark, custom and observation have no item to take
                    }
                }
                q.icon = DataNode.Compound.empty().with("id", id);
                return Result.OK;
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
            String key = t.type().equals("item") ? "count" : List.of("xp", "kill", "stat", "energy", "forge_energy").contains(t.type()) ? "value" : null;
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
        if (type.equals("gamestage")) {
            if (params.getString("stage", "").isBlank()) {
                return null;
            }
            // as FTB 26.1.2 writes a new Stage reward: claimed by itself, whatever blocks rewards
            if (!params.has("auto")) {
                params = params.with("auto", "invisible");
            }
            if (!params.has("ignore_reward_blocking")) {
                params = params.with("ignore_reward_blocking", true);
            }
        }
        if (tableType) {
            String tid = com.levviata.reflibquests.io.Ids.tableId(params);
            if (!book.tables().containsKey(tid)) {
                return null;
            }
            // written the way FTB Quests 26.1.2 writes it: the id as a number, and left out of "claim all"
            DataNode tableRef = tid.matches("[0-9A-F]{1,16}") ? new DataNode.Lng(Long.parseUnsignedLong(tid, 16)) : new DataNode.Str(tid);
            params = DataNode.Compound.empty().with("table_id", tableRef).with("exclude_from_claim_all", true);
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
                case "tags" -> {
                                    List<DataNode> tagList = new ArrayList<>();
                                    for (String part : value.split(",")) {
                                        String tg = part.strip();
                                        if (!tg.isEmpty()) {
                                            if (tg.length() > 40) {
                                                return Result.INVALID;
                                            }
                                            tagList.add(new DataNode.Str(tg));
                                        }
                                    }
                                    if (tagList.size() > 20) {
                                        return Result.INVALID;
                                    }
                                    p = tagList.isEmpty() ? p.without("tags") : p.with("tags", new DataNode.Lst(tagList));
                                }
                case "item" -> {
                    // an item picked for an item task: "name" or "name|damage"
                    if (!t.type().equals("item")) {
                        return Result.INVALID;
                    }
                    String[] parts = value.strip().split("[|]", 3);
                    net.minecraft.util.ResourceLocation rl = parts[0].contains(":") ? new net.minecraft.util.ResourceLocation(parts[0]) : null;
                    if (rl == null || !net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.containsKey(rl)) {
                        return Result.INVALID;
                    }
                    DataNode.Compound spec = DataNode.Compound.empty().with("id", parts[0]);
                    if (parts.length > 1) {
                        try {
                            int damage = Integer.parseInt(parts[1]);
                            if (damage > 0 && damage < 32767) {
                                spec = spec.with("damage", damage);
                            }
                        } catch (NumberFormatException ex) {
                            return Result.INVALID;
                        }
                    }
                    if (parts.length > 2 && !parts[2].isBlank()) {
                        DataNode.Compound nbt = parseNbt(parts[2]);
                        if (nbt == null) {
                            return Result.INVALID;
                        }
                        if (!nbt.entries().isEmpty()) {
                            spec = spec.with("nbt", nbt);
                        }
                    }
                    p = p.with("item", spec);
                }
                case "fluid_nbt" -> {
                    if (!t.type().equals("fluid")) {
                        return Result.INVALID;
                    }
                    DataNode.Compound f = p.get("fluid") instanceof DataNode.Compound fc ? fc : DataNode.Compound.empty().with("fluid", "minecraft:water").with("amount", 1000);
                    if (value.isBlank()) {
                        f = f.without("nbt");
                    } else {
                        DataNode.Compound nbt = parseNbt(value.strip());
                        if (nbt == null) {
                            return Result.INVALID;
                        }
                        f = nbt.entries().isEmpty() ? f.without("nbt") : f.with("nbt", nbt);
                    }
                    p = p.with("fluid", f);
                }
                case "fluid", "fluid_amount" -> {
                    if (!t.type().equals("fluid")) {
                        return Result.INVALID;
                    }
                    DataNode.Compound f = p.get("fluid") instanceof DataNode.Compound fc ? fc : DataNode.Compound.empty().with("fluid", "minecraft:water").with("amount", 1000);
                    if (key.equals("fluid")) {
                        String name = value.strip();
                        String bare = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
                        if (!net.minecraftforge.fluids.FluidRegistry.isFluidRegistered(bare)) {
                            return Result.INVALID;
                        }
                        f = f.with("fluid", name.contains(":") ? name : "minecraft:" + name);
                    } else {
                        long amount;
                        try {
                            amount = Long.parseLong(value.strip());
                        } catch (NumberFormatException ex) {
                            return Result.INVALID;
                        }
                        if (amount < 1 || amount > Integer.MAX_VALUE) {
                            return Result.INVALID;
                        }
                        f = f.with("amount", (int) amount);
                    }
                    p = p.with("fluid", f);
                }
                case "stat" -> {
                    String path = value.contains(":") ? value.substring(value.indexOf(':') + 1) : value;
                    if (!t.type().equals("stat") || !STATS.contains(path)) {
                        return Result.INVALID;
                    }
                    p = p.with(key, "minecraft:" + path);
                }
                case "stage" -> {
                    String v = value.strip();
                    if (!t.type().equals("gamestage") || v.isEmpty() || v.length() > 100) {
                        return Result.INVALID;
                    }
                    p = p.with(key, v);
                }
                case "team_stage" -> {
                    if (!t.type().equals("gamestage") || !value.equals("true") && !value.equals("false")) {
                        return Result.INVALID;
                    }
                    p = value.equals("true") ? p.with(key, true) : p.without(key);
                }
                case "to_interact" -> {
                    String v = value.strip();
                    if (!t.type().equals("interaction") || v.isEmpty() || v.length() > 200) {
                        return Result.INVALID;
                    }
                    p = p.with(key, v);
                }
                case "interaction_type" -> {
                    if (!t.type().equals("interaction") || !INTERACTION_TYPES.contains(value)) {
                        return Result.INVALID;
                    }
                    p = p.with(key, value);
                }
                case "criterion" -> {
                    if (!t.type().equals("advancement") || value.length() > 200) {
                        return Result.INVALID;
                    }
                    p = value.strip().isEmpty() ? p.without(key) : p.with(key, value.strip());
                }
                case "timer" -> {
                    int ticks;
                    try {
                        ticks = Integer.parseInt(value.strip());
                    } catch (NumberFormatException ex) {
                        return Result.INVALID;
                    }
                    if (!t.type().equals("observation") || ticks < 0 || ticks > 1_000_000) {
                        return Result.INVALID;
                    }
                    p = p.with(key, ticks);
                }
                case "observation_type" -> {
                    if (!t.type().equals("observation") || !List.of("block", "entity").contains(value)) {
                        return Result.INVALID;
                    }
                    p = p.with(key, value);
                }
                case "dimension" -> {
                    if (!t.type().equals("location") || !value.strip().matches("[a-z0-9_.-]+:[a-z0-9_./-]+|-?[0-9]+")) {
                        return Result.INVALID;
                    }
                    p = p.with(key, value.strip());
                }
                case "ignore_dimension" -> {
                    if (!t.type().equals("location") || !value.equals("true") && !value.equals("false")) {
                        return Result.INVALID;
                    }
                    p = value.equals("true") ? p.with(key, true) : p.without(key);
                }
                case "custom_name", "entityTypeTag", "nbt_filter" -> {
                    if (!t.type().equals("kill") || value.length() > 300) {
                        return Result.INVALID;
                    }
                    String v = value.strip();
                    if (key.equals("nbt_filter") && !v.isEmpty()) {
                        try {
                            net.minecraft.nbt.JsonToNBT.getTagFromJson(v);
                        } catch (net.minecraft.nbt.NBTException ex) {
                            return Result.INVALID;
                        }
                    }
                    p = v.isEmpty() ? p.without(key) : p.with(key, v);
                }
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
                    if (!(t.type().equals("item") || t.type().equals("fluid")) || !List.of("default", "fuzzy", "exact").contains(value)) {
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
        // a reward inside a reward table is edited the same way, on a stand-in quest that holds only it
        for (RewardTable tbl : book.tables().values()) {
            for (int ti = 0; ti < tbl.entries.size(); ti++) {
                Definitions.WeightedReward entry = tbl.entries.get(ti);
                if (entry.reward().id().equals(rewardId)) {
                    Quest stand = new Quest("stand-in");
                    stand.rewards.add(entry.reward());
                    Result res = setRewardPropIn(stand, rewardId, key, value);
                    if (res == Result.OK) {
                        tbl.entries.set(ti, new Definitions.WeightedReward(entry.weight(), stand.rewards.get(0)));
                    }
                    return res;
                }
            }
        }
        for (Chapter c : book.chapters()) {
            for (Quest q : c.quests) {
                Result res = setRewardPropIn(q, rewardId, key, value);
                if (res != Result.NOT_FOUND) {
                    return res;
                }
            }
        }
        return Result.NOT_FOUND;
    }

    private Result setRewardPropIn(Quest q, String rewardId, String key, String value) {
        if (key == null || value == null) {
            return Result.INVALID;
        }

        {
            {
                for (int i = 0; i < q.rewards.size(); i++) {
                    Reward r = q.rewards.get(i);
                    if (!r.id().equals(rewardId)) {
                        continue;
                    }
                    DataNode.Compound p = r.params();
                    switch (key) {
                        case "item" -> {
                            // the Item row of an item reward: "name", "name|damage" or "name|damage|nbt"
                            if (!r.type().equals("item")) {
                                return Result.INVALID;
                            }
                            String[] parts = value.strip().split("[|]", 3);
                            net.minecraft.util.ResourceLocation rl = parts[0].contains(":") ? new net.minecraft.util.ResourceLocation(parts[0]) : null;
                            if (rl == null || !net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.containsKey(rl)) {
                                return Result.INVALID;
                            }
                            DataNode.Compound spec = DataNode.Compound.empty().with("id", parts[0]);
                            if (parts.length > 1 && parts[1].matches("[0-9]+") && Integer.parseInt(parts[1]) > 0 && Integer.parseInt(parts[1]) < 32767) {
                                spec = spec.with("damage", Integer.parseInt(parts[1]));
                            }
                            if (parts.length > 2 && !parts[2].isBlank()) {
                                DataNode.Compound nbt = parseNbt(parts[2]);
                                if (nbt == null) {
                                    return Result.INVALID;
                                }
                                if (!nbt.entries().isEmpty()) {
                                    spec = spec.with("nbt", nbt);
                                }
                            }
                            p = p.with("item", spec);
                        }
                        case "title" -> p = withText(p, "title", value, 200);
                        case "tags" -> {
                                            List<DataNode> tagList = new ArrayList<>();
                                            for (String part : value.split(",")) {
                                                String tg = part.strip();
                                                if (!tg.isEmpty()) {
                                                    if (tg.length() > 40) {
                                                        return Result.INVALID;
                                                    }
                                                    tagList.add(new DataNode.Str(tg));
                                                }
                                            }
                                            if (tagList.size() > 20) {
                                                return Result.INVALID;
                                            }
                                            p = tagList.isEmpty() ? p.without("tags") : p.with("tags", new DataNode.Lst(tagList));
                                        }
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
                        case "stage" -> {
                            String v = value.strip();
                            if (!r.type().equals("gamestage") || v.isEmpty() || v.length() > 100) {
                                return Result.INVALID;
                            }
                            p = p.with("stage", v);
                        }
                        case "remove" -> {
                            if (!r.type().equals("gamestage") || !value.equals("true") && !value.equals("false")) {
                                return Result.INVALID;
                            }
                            p = value.equals("true") ? p.with("remove", true) : p.without("remove");
                        }
                        case "team_reward" -> {
                            if (!List.of("default", "true", "false").contains(value)) {
                                return Result.INVALID;
                            }
                            p = value.equals("default") ? p.without(key) : p.with(key, value.equals("true"));
                        }
                        case "feedback_message" -> {
                            if (!r.type().equals("command") || value.length() > 500) {
                                return Result.INVALID;
                            }
                            p = value.strip().isEmpty() ? p.without(key) : p.with(key, value.strip());
                        }
                        case "random_bonus" -> {
                            int bonus;
                            try {
                                bonus = Integer.parseInt(value.strip());
                            } catch (NumberFormatException ex) {
                                return Result.INVALID;
                            }
                            if (!r.type().equals("item") || bonus < 0 || bonus > 100_000) {
                                return Result.INVALID;
                            }
                            p = bonus == 0 ? p.without(key) : p.with(key, bonus);
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
                    if (id.equalsIgnoreCase(com.levviata.reflibquests.io.Ids.tableId(r.params()))) {
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
                case "hide_tooltip", "use_title" -> {
                    // FTB 26.1.2 table switches, kept in the table's own data like the file keeps them
                    if (!value.equals("true") && !value.equals("false")) {
                        return Result.INVALID;
                    }
                    t.raw = t.raw.with(key, value.equals("true"));
                }
                case "loot_size" -> {
                    int v = Integer.parseInt(value);
                    if (v < 0 || v > 100) {
                        return Result.INVALID;
                    }
                    t.lootSize = v;
                }
                case "loot_enabled" -> {
                    if (value.equals("true")) {
                        if (!t.raw.has("loot_crate")) {
                            t.raw = t.raw.with("loot_crate", DataNode.Compound.empty().with("string_id", t.id).with("color", 0xFFFFFF)
                                    .with("drops", DataNode.Compound.empty().with("passive", 0).with("monster", 0).with("boss", 0)));
                        }
                    } else if (value.equals("false")) {
                        t.raw = t.raw.without("loot_crate");
                    } else {
                        return Result.INVALID;
                    }
                }
                case "tags" -> {
                    List<DataNode> tagList = new ArrayList<>();
                    for (String part : value.split(",")) {
                        String tg = part.strip();
                        if (!tg.isEmpty()) {
                            if (tg.length() > 40 || tagList.size() >= 20) {
                                return Result.INVALID;
                            }
                            tagList.add(new DataNode.Str(tg));
                        }
                    }
                    t.raw = tagList.isEmpty() ? t.raw.without("tags") : t.raw.with("tags", new DataNode.Lst(tagList));
                }
                case "icon" -> {
                    // "item:<id>|<damage>", "image:<picture id>" or "clear"
                    if (value.equals("clear")) {
                        t.raw = t.raw.without("icon");
                    } else if (value.startsWith("image:") && value.length() < 206) {
                        t.raw = t.raw.with("icon", DataNode.Compound.empty().with("image", value.substring(6)));
                    } else if (value.startsWith("item:") && value.substring(5).split("[|]", 3)[0].matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                        String[] parts = value.substring(5).split("[|]", 3);
                        DataNode.Compound item = DataNode.Compound.empty().with("id", parts[0]);
                        if (parts.length > 1 && parts[1].matches("[0-9]+") && Integer.parseInt(parts[1]) > 0) {
                            item = item.with("damage", Integer.parseInt(parts[1]));
                        }
                        if (parts.length > 2 && !parts[2].isBlank()) {
                            DataNode.Compound nbt = parseNbt(parts[2]);
                            if (nbt == null) {
                                return Result.INVALID;
                            }
                            if (!nbt.entries().isEmpty()) {
                                item = item.with("nbt", nbt);
                            }
                        }
                        t.raw = t.raw.with("icon", item);
                    } else {
                        return Result.INVALID;
                    }
                }
                case "loot_string_id", "loot_item_name", "loot_color", "loot_glow", "loot_boss", "loot_monster", "loot_passive" -> {
                    DataNode.Compound crate = t.raw.getCompound("loot_crate");
                    DataNode.Compound drops = crate.getCompound("drops");
                    switch (key) {
                        case "loot_string_id" -> {
                            if (value.length() > 60 || !value.matches("[a-z0-9_]*")) {
                                return Result.INVALID;
                            }
                            crate = crate.with("string_id", value);
                        }
                        case "loot_item_name" -> {
                            if (value.length() > 100) {
                                return Result.INVALID;
                            }
                            crate = crate.with("item_name", value);
                        }
                        case "loot_color" -> {
                            String hex = value.startsWith("#") ? value.substring(1) : value;
                            if (!hex.matches("[0-9a-fA-F]{6}")) {
                                return Result.INVALID;
                            }
                            crate = crate.with("color", Integer.parseInt(hex, 16));
                        }
                        case "loot_glow" -> {
                            if (!value.equals("true") && !value.equals("false")) {
                                return Result.INVALID;
                            }
                            crate = crate.with("glow", value.equals("true"));
                        }
                        default -> {
                            int weight = Integer.parseInt(value);
                            if (weight < 0 || weight > 1_000_000) {
                                return Result.INVALID;
                            }
                            drops = drops.with(key.substring(5), weight);
                            crate = crate.with("drops", drops);
                        }
                    }
                    t.raw = t.raw.with("loot_crate", crate);
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
