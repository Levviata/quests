/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.levviata.reflib.api.data.DataFiles;
import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.ChapterImage;
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
 * Reads the older quest folder layout (a {@code file.snbt}, {@code chapters/<id>/chapter.snbt} with one file per quest,
 * and {@code reward_tables/<id>.snbt}) into the same {@link QuestBook} the newer layout produces. Quest ids are the file
 * names, reward tables are referred to by their position in {@code reward_tables/index.snbt}, items are written as
 * {@code "mod:item [count] [damage]"} and the quest text is split into a short {@code description} (the subtitle) and
 * the long {@code text}. Nothing is thrown for bad data; problems are collected and the rest of the book still loads.
 */
public final class LegacyImporter {

    private static final Pattern PROGRESS_COMMAND = Pattern.compile("^/?ftbquests\\s+change_progress\\s+(complete|reset)\\s+(\\S+)\\s+(\\S+)\\s*$");

    private final List<String> problems = new ArrayList<>();
    private int quests;
    private int tasks;
    private int rewards;
    private final List<String> tableIds = new ArrayList<>();
    private final Map<Chapter, Long> chapterGroupNumbers = new LinkedHashMap<>();

    /** When true ids are written the way FTB Quests 26.1.2 keeps them: 16 upper-case hex digits (its language keys need that form). */
    private boolean wide;

    private LegacyImporter() {}

    private String nid(String raw) {
        String s = Ids.normalize(raw);
        if (wide && s.matches("[0-9A-F]{1,16}")) {
            return "0".repeat(16 - s.length()) + s;
        }
        return s;
    }

    private String generated(String base, String tag, int index) {
        return wide ? String.format("%08X%08X", base.hashCode(), tag.hashCode() * 31 + index) : base + "_" + tag + index;
    }

    /** True for a folder in the older layout that has not been converted yet. */
    public static boolean applies(Path folder) {
        return Files.exists(folder.resolve("file.snbt"))
                && !Files.exists(folder.resolve("data.json5")) && !Files.exists(folder.resolve("data.snbt"))
                && Files.isDirectory(folder.resolve("chapters"));
    }

    public static UpstreamImporter.Result load(Path folder) {
        return new LegacyImporter().run(folder);
    }

    /** Like {@link #load} but with ids in the 16-digit form FTB Quests 26.1.2 uses (needed to write a book for it). */
    public static UpstreamImporter.Result loadWide(Path folder) {
        LegacyImporter i = new LegacyImporter();
        i.wide = true;
        return i.run(folder);
    }

    private UpstreamImporter.Result run(Path folder) {
        QuestBook book = new QuestBook();
        DataNode.Compound settings = read(folder.resolve("file.snbt"));
        if (settings != null) {
            book.settings = settings;
        }
        book.progressionMode = ProgressionMode.LINEAR;
        book.fallbackLocale = "en_us";
        readTables(folder.resolve("reward_tables"), book);
        readChapters(folder.resolve("chapters"), book);
        book.reindex(); // chapters are found by id from here on
        makeGroups(book);
        book.reindex();
        return new UpstreamImporter.Result(book, problems, quests, tasks, rewards);
    }

    private DataNode.Compound read(Path file) {
        try {
            return DataFiles.readLenient(file);
        } catch (IOException | RuntimeException ex) {
            problems.add(file + ": " + ex.getMessage());
            return null;
        }
    }

    /**
     * Names from an {@code index.snbt}, then any others (sorted) that it does not list. Chapter folders are the exception:
     * FTB Quests only loads the chapters its index lists, so an unlisted chapter folder is left out and reported.
     */
    private List<String> ordered(Path dir, boolean directories) {
        List<String> names = new ArrayList<>();
        DataNode.Compound index = Files.exists(dir.resolve("index.snbt")) ? read(dir.resolve("index.snbt")) : null;
        if (index != null) {
            for (DataNode n : index.getList("index")) {
                if (n instanceof DataNode.Str s) {
                    names.add(s.value());
                }
            }
        }
        List<String> found = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.forEach(p -> {
                String n = p.getFileName().toString();
                if (directories && Files.isDirectory(p)) {
                    found.add(n);
                } else if (!directories && n.endsWith(".snbt") && !n.equals("index.snbt")) {
                    found.add(n.substring(0, n.length() - 5));
                }
            });
        } catch (IOException ex) {
            problems.add("cannot list " + dir + ": " + ex.getMessage());
        }
        found.sort(String::compareTo);
        names.removeIf(n -> !found.contains(n));
        boolean listedOnly = directories && index != null;
        for (String n : found) {
            if (!names.contains(n)) {
                if (listedOnly) {
                    problems.add("chapter folder " + n + " is not listed in " + dir.resolve("index.snbt") + "; skipped, as FTB Quests does");
                } else {
                    names.add(n);
                }
            }
        }
        return names;
    }

    // ------------------------------------------------------------------ items

    /** {@code "mod:item"}, {@code "mod:item 1 6"} (count, damage) or an item compound, as an item spec; null if it is not an item. */
    private static DataNode.Compound itemSpec(DataNode n, boolean ignoreDamage, boolean ignoreNbt) {
        String id;
        int damage = -1;
        if (n instanceof DataNode.Str s) {
            String[] parts = s.value().trim().split("\\s+");
            id = parts[0];
            if (parts.length >= 3) {
                try {
                    damage = Integer.parseInt(parts[2]);
                } catch (NumberFormatException ex) {
                    damage = -1;
                }
            }
        } else if (n instanceof DataNode.Compound c) {
            if (!c.has("id") && c.get("item") instanceof DataNode.Str inner) {
                return itemSpec(inner, ignoreDamage, ignoreNbt); // {item: "mod:item 1 6"}: the text form inside a compound
            }
            id = c.getString("id", "");
            damage = c.has("Damage") ? c.getInt("Damage", -1) : c.getInt("damage", -1);
        } else {
            return null;
        }
        if (id.isEmpty() || id.equals("air") || id.equals("minecraft:air")) {
            return null;
        }
        DataNode.Compound out = DataNode.Compound.empty().with("id", id);
        if (damage >= 0 && !ignoreDamage) {
            out = out.with("damage", damage);
        }
        if (n instanceof DataNode.Compound c && !ignoreNbt && !c.getCompound("tag").entries().isEmpty()) {
            out = out.with("nbt", c.getCompound("tag")); // e.g. which creature a spawn egg holds
        }
        return out;
    }

    /** For an ItemFilters filter item that matches an ore dictionary name, that name; otherwise null. */
    private static String oreOf(DataNode n) {
        if (!(n instanceof DataNode.Compound c) || !c.getString("id", "").equals("itemfilters:filter")) {
            return null;
        }
        DataNode.Compound parent = c.getCompound("ForgeCaps").getCompound("Parent");
        String data = parent.getString("data", "");
        return parent.getString("id", "").equals("ore") && !data.isEmpty() ? data : null;
    }

    private static String displayNameOf(DataNode n) {
        return n instanceof DataNode.Compound c ? c.getCompound("tag").getCompound("display").getString("Name", "") : "";
    }

    private static DataNode.Compound iconOf(DataNode n) {
        DataNode.Compound c = itemSpec(n, false, false);
        return c == null ? DataNode.Compound.empty() : c;
    }

    private static String shortId(String id) {
        return id;
    }

    // ------------------------------------------------------------------ reward tables

    private void readTables(Path dir, QuestBook book) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        int order = 0;
        for (String name : ordered(dir, false)) {
            DataNode.Compound c = read(dir.resolve(name + ".snbt"));
            if (c == null) {
                continue;
            }
            String id = nid(name);
            RewardTable t = new RewardTable(id);
            t.filename = name;
            t.title = c.getString("title", "");
            t.order = order++;
            t.lootSize = c.getInt("loot_size", 0);
            t.emptyWeight = (float) c.getDouble("empty_weight", 0);
            int n = 0;
            for (DataNode rn : c.getList("rewards")) {
                if (!(rn instanceof DataNode.Compound rc)) {
                    continue;
                }
                DataNode.Compound spec = itemSpec(rc.get("item"), false, false);
                if (spec == null) {
                    problems.add("reward table " + name + " has an entry that is not an item");
                    continue;
                }
                DataNode.Compound params = DataNode.Compound.empty().with("item", spec).with("count", Math.max(1, rc.getInt("count", 1)));
                t.entries.add(new WeightedReward((float) rc.getDouble("weight", 1.0), new Reward(generated(id, "r", n++), "item", params)));
                rewards++;
            }
            book.tables().put(id, t);
            tableIds.add(id);
        }
    }

    // ------------------------------------------------------------------ chapters and quests

    private void readChapters(Path dir, QuestBook book) {
        int order = 0;
        for (String name : ordered(dir, true)) {
            Path chapterDir = dir.resolve(name);
            DataNode.Compound c = Files.exists(chapterDir.resolve("chapter.snbt")) ? read(chapterDir.resolve("chapter.snbt")) : DataNode.Compound.empty();
            if (c == null) {
                continue;
            }
            Chapter chapter = new Chapter(nid(name));
            chapter.filename = name;
            chapter.order = order++;
            chapter.title = c.getString("title", name);
            chapter.icon = iconOf(c.get("icon"));
            chapter.alwaysInvisible = c.getBool("always_invisible", false);
            long groupNumber = c.get("group") instanceof DataNode.Lng l ? l.value() : c.get("group") instanceof DataNode.Int gi ? gi.value() : 0;
            if (groupNumber != 0) {
                chapterGroupNumbers.put(chapter, groupNumber);
            }
            chapter.defaultQuestShape = c.getString("default_quest_shape", "");
            chapter.raw = c.without("images");
            int img = 0;
            for (DataNode in : c.getList("images")) {
                if (in instanceof DataNode.Compound ic) {
                    chapter.images.add(image(chapter.id, img++, ic));
                }
            }
            for (String file : ordered(chapterDir, false)) {
                if (file.equals("chapter")) {
                    continue;
                }
                DataNode.Compound qc = read(chapterDir.resolve(file + ".snbt"));
                if (qc != null) {
                    chapter.quests.add(readQuest(file, qc));
                }
            }
            book.chapters().add(chapter);
        }
    }

    /**
     * In the older files a chapter's group is the id of another chapter (read as a signed number): the chapter is listed
     * under that one. Each such parent becomes a group that is shown as the parent chapter's own row, with the others nested
     * below it. A number that names no chapter becomes a plain group called "Group 1", "Group 2" and so on.
     */
    private void makeGroups(QuestBook book) {
        Map<Long, String> idOfNumber = new LinkedHashMap<>();
        int plain = 0;
        for (Map.Entry<Chapter, Long> e : chapterGroupNumbers.entrySet()) {
            long number = e.getValue();
            String groupId = idOfNumber.get(number);
            if (groupId == null) {
                String hex = nid(Integer.toHexString((int) number));
                Chapter parent = book.chapter(hex);
                com.levviata.reflibquests.model.Definitions.ChapterGroup g;
                if (parent != null) {
                    g = new com.levviata.reflibquests.model.Definitions.ChapterGroup(wide ? String.format("%08X%08X", hex.hashCode(), 0x67) : hex); // a group must not share its id with the chapter
                    g.title = parent.title;
                    g.raw = DataNode.Compound.empty().with("chapter", parent.id);
                } else {
                    g = new com.levviata.reflibquests.model.Definitions.ChapterGroup(String.valueOf(number));
                    g.title = "Group " + (++plain);
                    problems.add("chapter group " + number + " does not name a chapter; it is called " + g.title);
                }
                book.groups().add(g);
                groupId = g.id;
                idOfNumber.put(number, groupId);
            }
            e.getKey().groupId = groupId;
        }
    }

    private static ChapterImage image(String chapterId, int index, DataNode.Compound ic) {
        List<DataNode> hover = new ArrayList<>();
        for (DataNode h : ic.getList("hover")) {
            if (h instanceof DataNode.Str) {
                hover.add(h); // every line of the hover text is kept
            }
        }
        DataNode.Compound raw = DataNode.Compound.empty().with("image", ic.getString("image", ""))
                .with("x", ic.getDouble("x", 0)).with("y", ic.getDouble("y", 0))
                .with("width", ic.getDouble("width", 1)).with("height", ic.getDouble("height", 1))
                .with("rotation", ic.getDouble("rotation", 0)).with("order", index);
        if (!hover.isEmpty()) {
            raw = raw.with("hover", new DataNode.Lst(hover));
        }
        if (!ic.getString("click", "").isEmpty()) {
            raw = raw.with("click", ic.getString("click", ""));
        }
        if (ic.getBool("dev", false)) {
            raw = raw.with("dev", true);
        }
        return new ChapterImage(chapterId + "_image" + index, raw);
    }

    private Quest readQuest(String file, DataNode.Compound c) {
        String id = nid(file);
        Quest q = new Quest(id);
        q.title = c.getString("title", "");
        q.subtitle = c.getString("description", "");
        for (DataNode line : c.getList("text")) {
            if (line instanceof DataNode.Str s) {
                q.description.add(s.value());
            }
        }
        // a description that is a single empty line is only a placeholder
        if (q.description.size() == 1 && q.description.get(0).isEmpty()) {
            q.description.clear();
        }
        q.x = c.getDouble("x", 0);
        q.y = c.getDouble("y", 0);
        q.shape = c.getString("shape", "");
        q.size = c.getDouble("size", 1.0);
        q.icon = iconOf(c.get("icon"));
        for (DataNode d : c.getList("dependencies")) {
            if (d instanceof DataNode.Str s && !nid(s.value()).isEmpty()) {
                q.dependencies.add(nid(s.value()));
            }
        }
        q.dependencyMode = DependencyMode.parse(c.getString("dependency_requirement", "all_completed"));
        q.minRequiredDependencies = c.getInt("min_required_dependencies", 0);
        q.optional = c.getBool("optional", false);
        q.orTasks = c.getBool("or_tasks", false);
        q.disableToast = c.getBool("disable_toast", false);
        if (c.getBool("hide", false)) {
            q.hideUntilDepsComplete = Tristate.TRUE; // hidden until the quests it needs are completed
        }
        if (c.has("hide_dependency_lines")) {
            q.hideDependencyLines = c.getBool("hide_dependency_lines", false) ? Tristate.TRUE : Tristate.FALSE;
        }
        if (c.has("can_repeat")) {
            q.canRepeat = c.getBool("can_repeat", false) ? Tristate.TRUE : Tristate.FALSE;
        }
        q.raw = c.without("tasks").without("rewards").without("text").without("dependencies");
        int t = 0;
        for (DataNode tn : c.getList("tasks")) {
            if (tn instanceof DataNode.Compound tc) {
                q.tasks.add(task(id, t++, tc));
                tasks++;
            }
        }
        int r = 0;
        for (DataNode rn : c.getList("rewards")) {
            if (rn instanceof DataNode.Compound rc) {
                Reward reward = reward(id, r++, rc);
                if (reward != null) {
                    q.rewards.add(reward);
                    rewards++;
                }
            }
        }
        quests++;
        return q;
    }

    private Task task(String questId, int index, DataNode.Compound tc) {
        String id = tc.has("uid") ? nid(tc.getString("uid", "")) : "";
        if (id.isEmpty()) {
            id = generated(questId, "t", index);
        }
        String type = Ids.stripNamespace(tc.getString("type", "checkmark"));
        DataNode.Compound p = DataNode.Compound.empty();
        String filterName = "";
        switch (type) {
            case "item" -> {
                boolean ignoreDamage = tc.getBool("ignore_damage", false);
                List<String> pieces = new ArrayList<>();
                DataNode.Compound plain = null;
                for (DataNode e : tc.getList("items")) {
                    String ore = oreOf(e);
                    if (ore != null) {
                        // an "any item with this ore dictionary name" filter
                        pieces.add("ore(" + ore + ")");
                        if (filterName.isEmpty()) {
                            filterName = displayNameOf(e);
                        }
                        continue;
                    }
                    DataNode.Compound spec = itemSpec(e, ignoreDamage, tc.getBool("ignore_nbt", false));
                    if (spec != null) {
                        pieces.add("item(" + spec.getString("id", "") + ")");
                        if (plain == null) {
                            plain = spec;
                        }
                    }
                }
                if (pieces.isEmpty()) {
                    problems.add("task " + id + " of quest " + questId + " has no item");
                } else if (pieces.size() == 1 && plain != null) {
                    p = p.with("item", plain);
                } else {
                    p = p.with("item", DataNode.Compound.empty().with("id", "ftbfiltersystem:smart_filter")
                            .with("components", DataNode.Compound.empty().with("ftbfiltersystem:filter",
                                    pieces.size() == 1 ? pieces.get(0) : "or(" + String.join("", pieces) + ")")));
                }
                long count = Math.max(1, tc.getLong("count", 1));
                p = p.with("count", count > Integer.MAX_VALUE ? (DataNode) new DataNode.Lng(count) : new DataNode.Int((int) count));
                if (tc.has("consume_items")) {
                    p = p.with("consume_items", tc.getBool("consume_items", false));
                }
            }
            case "kill" -> p = p.with("entity", tc.getString("entity", "")).with("value", Math.max(1, tc.getLong("value", 1)));
            case "observation" -> {
                String match = tc.getString("match_type", "block_id");
                p = p.with("observation_type", match.equals("block_id") ? "block" : match)
                        .with("to_observe", tc.getString("match", "")).with("timer", tc.getLong("timer", 0));
            }
            case "dimension" -> p = p.with("dimension", String.valueOf(tc.getInt("dim", 0)));
            case "advancement" -> p = p.with("advancement", tc.getString("advancement", "")).with("criterion", tc.getString("criterion", ""));
            case "fluid" -> p = p.with("fluid", tc.getString("fluid", "")).with("value", Math.max(1, tc.getLong("value", 1000)));
            case "checkmark" -> { }
            default -> p = tc.without("uid").without("type");
        }
        if (!tc.getString("title", "").isEmpty()) {
            p = p.with("title", tc.getString("title", ""));
        } else if (!filterName.isEmpty()) {
            p = p.with("title", filterName); // the name the filter item was given ("Any stoneResource")
        }
        if (tc.has("icon")) {
            DataNode.Compound icon = iconOf(tc.get("icon"));
            if (!icon.entries().isEmpty()) {
                p = p.with("icon", icon);
            }
        }
        return new Task(id, type, p);
    }

    private Reward reward(String questId, int index, DataNode.Compound rc) {
        String id = rc.has("uid") ? nid(rc.getString("uid", "")) : "";
        if (id.isEmpty()) {
            id = generated(questId, "q", index);
        }
        String type = rc.has("type") ? Ids.stripNamespace(rc.getString("type", "")) : "item";
        DataNode.Compound p = DataNode.Compound.empty();
        switch (type) {
            case "item" -> {
                DataNode.Compound spec = itemSpec(rc.get("item"), false, false);
                if (spec == null) {
                    problems.add("reward " + id + " of quest " + questId + " has no item");
                    return null;
                }
                p = p.with("item", spec).with("count", Math.max(1, rc.getInt("count", 1)));
            }
            case "xp" -> p = p.with("xp", Math.max(1, rc.getInt("xp", 1)));
            case "xp_levels" -> p = p.with("xp_levels", Math.max(1, rc.getInt("xp_levels", 1)));
            case "command" -> {
                String command = rc.getString("command", "");
                p = p.with("command", command);
                Matcher m = PROGRESS_COMMAND.matcher(command);
                if (m.matches()) {
                    // the original mod's command for unlocking a quest, in this mod's form (and with the rights it needs)
                    p = p.with("command", "/rquests " + m.group(1) + " " + nid(m.group(3)) + " " + m.group(2))
                            .with("permission_level", 2);
                }
            }
            case "random", "loot", "choice", "all_table" -> {
                int at = rc.getInt("table", -1);
                if (at < 0 || at >= tableIds.size()) {
                    problems.add("reward " + id + " of quest " + questId + " names reward table " + at + ", which does not exist");
                } else {
                    p = p.with("table_id", tableIds.get(at));
                }
            }
            default -> p = rc.without("uid").without("type");
        }
        for (String key : List.of("title", "auto")) {
            if (!rc.getString(key, "").isEmpty()) {
                p = p.with(key, rc.getString(key, ""));
            }
        }
        if (rc.has("icon")) {
            DataNode.Compound icon = iconOf(rc.get("icon"));
            if (!icon.entries().isEmpty()) {
                p = p.with("icon", icon);
            }
        }
        return new Reward(id, type, p);
    }
}
