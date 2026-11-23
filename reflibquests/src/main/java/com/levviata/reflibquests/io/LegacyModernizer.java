/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflib.api.data.Snbt;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.ChapterGroup;
import com.levviata.reflibquests.model.Definitions.ChapterImage;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.RewardTable;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.model.Definitions.WeightedReward;
import com.levviata.reflibquests.model.QuestBook;

/**
 * Rewrites a book that {@link LegacyImporter} read from a 1.12.2 pack into the form FTB Quests 26.1.2 saves: item ids
 * and metadata become 26.1.2 ids (with data components where the metadata used to pick a fluid), ore dictionary filters
 * become tags, dimensions, entities and images get their modern names, and settings that the old pack kept in its own
 * dialect are dropped. The id table is data ({@code /ntm/legacy_1_12_2_to_26_1_2.json}, written by
 * {@code tools/ntm/build_map.py}); every substitution and every entry without an equivalent is recorded in
 * {@link #notes()} so the result can be reviewed.
 */
public final class LegacyModernizer {

    /** One decision worth reviewing. {@code kind} is the table status ("substitute", "inferred", "unresolved"...). */
    public record Note(String kind, String from, String to, String note, String where) {}

    private static final String TABLE_RESOURCE = "/ntm/legacy_1_12_2_to_26_1_2.json";
    private static final Pattern DIGITS = Pattern.compile("-?[0-9]+");
    private static final Pattern PROGRESS = Pattern.compile("^/rquests\\s+(complete|reset)\\s+(\\S+)\\s+(\\S+)\\s*$");

    private final DataNode.Compound items;
    private final DataNode.Compound vanilla;
    private final DataNode.Compound fluids;
    private final DataNode.Compound entities;
    private final DataNode.Compound oredict;
    private final DataNode.Compound images;
    private final List<String> keepMods = new ArrayList<>();

    private final List<Note> notes = new ArrayList<>();
    private final Map<String, Integer> counts = new TreeMap<>();
    private String where = "";

    public LegacyModernizer(DataNode.Compound table) {
        items = table.getCompound("items");
        vanilla = table.getCompound("vanilla");
        fluids = table.getCompound("fluids");
        entities = table.getCompound("entities");
        oredict = table.getCompound("oredict");
        images = table.getCompound("images");
        for (DataNode n : table.getList("keep_mods")) {
            if (n instanceof DataNode.Str s) {
                keepMods.add(s.value());
            }
        }
    }

    /** The table shipped with the mod. */
    public static LegacyModernizer builtin() throws IOException {
        try (InputStream in = LegacyModernizer.class.getResourceAsStream(TABLE_RESOURCE)) {
            if (in == null) {
                throw new IOException("missing resource " + TABLE_RESOURCE);
            }
            return new LegacyModernizer(Snbt.parseLenient(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RuntimeException ex) {
            throw new IOException("cannot read " + TABLE_RESOURCE + ": " + ex.getMessage(), ex);
        }
    }

    public List<Note> notes() {
        return notes;
    }

    /** How many times each kind of decision was taken ("same", "mapped", "substitute"...). */
    public Map<String, Integer> counts() {
        return counts;
    }

    private void count(String key) {
        counts.merge(key, 1, Integer::sum);
    }

    private void note(String kind, String from, String to, String text) {
        notes.add(new Note(kind, from, to, text, where));
        count(kind);
    }

    // ------------------------------------------------------------------ whole book

    public void modernize(QuestBook book) {
        book.settings = settings(book.settings);
        // groups: the old parent chapter becomes a plain group that also holds the parent chapter itself
        for (ChapterGroup g : book.groups()) {
            String parent = g.raw.getString("chapter", "");
            g.raw = DataNode.Compound.empty();
            if (!parent.isEmpty()) {
                Chapter p = book.chapter(parent);
                if (p != null) {
                    p.groupId = g.id;
                }
            }
        }
        java.util.Set<String> usedNames = new java.util.HashSet<>();
        for (Chapter c : book.chapters()) {
            where = "chapter " + c.title;
            c.filename = uniqueName(slug(c.title.isEmpty() ? c.id : c.title), usedNames);
            c.raw = DataNode.Compound.empty();
            c.icon = icon(c.icon);
            for (int i = 0; i < c.images.size(); i++) {
                c.images.set(i, image(c.images.get(i)));
            }
            for (Quest q : c.quests) {
                quest(c, q);
            }
        }
        for (RewardTable t : book.tables().values()) {
            where = "reward table " + (t.title.isEmpty() ? t.id : t.title);
            t.raw = DataNode.Compound.empty();
            for (int i = 0; i < t.entries.size(); i++) {
                WeightedReward w = t.entries.get(i);
                Reward r = reward(w.reward(), book);
                t.entries.set(i, new WeightedReward(w.weight(), r));
            }
        }
        book.reindex();
    }

    private DataNode.Compound settings(DataNode.Compound old) {
        DataNode.Compound s = DataNode.Compound.empty();
        for (String key : List.of("default_reward_team", "default_consume_items", "default_autoclaim_rewards", "default_quest_shape",
                "default_quest_disable_jei", "drop_loot_crates", "loot_crate_no_drop", "disable_gui", "grid_scale")) {
            if (old.has(key)) {
                s = s.with(key, old.get(key));
            }
        }
        DataNode cooldown = old.get("emergency_items_cooldown");
        if (cooldown instanceof DataNode.Str t) { // "5m" in the old file, seconds in the new one
            Matcher m = Pattern.compile("(\\d+)([smh]?)").matcher(t.value());
            if (m.matches()) {
                int n = Integer.parseInt(m.group(1));
                s = s.with("emergency_items_cooldown", n * switch (m.group(2)) { case "m" -> 60; case "h" -> 3600; default -> 1; });
            }
        }
        return s;
    }

    // ------------------------------------------------------------------ quests, tasks, rewards

    private void quest(Chapter chapter, Quest q) {
        where = "quest " + chapter.title + " / " + (q.title.isEmpty() ? q.id : q.title);
        q.raw = DataNode.Compound.empty();
        q.icon = icon(q.icon);
        List<Task> tasks = new ArrayList<>(q.tasks);
        q.tasks.clear();
        for (Task t : tasks) {
            q.tasks.add(task(t));
        }
        List<Reward> rewards = new ArrayList<>(q.rewards);
        q.rewards.clear();
        for (Reward r : rewards) {
            q.rewards.add(reward(r, null));
        }
    }

    private Task task(Task t) {
        DataNode.Compound p = t.params();
        switch (t.type()) {
            case "item" -> {
                DataNode.Compound out = DataNode.Compound.empty();
                long count = p.has("count") ? p.getLong("count", 1) : 1;
                DataNode.Compound item = p.getCompound("item");
                DataNode.Compound spec = item.getString("id", "").equals("ftbfiltersystem:smart_filter") ? filterItem(item) : item(item);
                if (spec == null) {
                    // nothing in 26.1.2 to ask for: a manual check-off keeps the quest usable, and the title says what was meant
                    String was = item.getString("id", "?").equals("ftbfiltersystem:smart_filter")
                            ? item.getCompound("components").getString("ftbfiltersystem:filter", "?")
                            : item.getString("id", "?") + (item.has("damage") ? "@" + item.getInt("damage", 0) : "");
                    String title = p.getString("title", "");
                    if (title.isEmpty()) {
                        title = "Unresolved item: " + was;
                    }
                    note("unresolved", was, "checkmark task", "no 26.1.2 equivalent; the task became a check-off titled \"" + title + "\"");
                    return new Task(t.id(), "checkmark", DataNode.Compound.empty().with("title", title));
                }
                out = out.with("item", spec.with("count", 1));
                if (count > 1) {
                    out = out.with("count", count);
                }
                if (p.has("consume_items")) {
                    out = out.with("consume_items", p.getBool("consume_items", false));
                }
                out = carry(out, p);
                return new Task(t.id(), t.type(), out);
            }
            case "kill" -> {
                String entity = entity(p.getString("entity", ""));
                return new Task(t.id(), t.type(), carry(DataNode.Compound.empty().with("entity", entity).with("value", p.getLong("value", 1)), p));
            }
            case "observation" -> {
                String what = p.getString("to_observe", "");
                String type = p.getString("observation_type", "block");
                if (type.equals("block")) {
                    DataNode.Compound spec = item(DataNode.Compound.empty().with("id", what));
                    what = spec == null ? what : spec.getString("id", what);
                }
                DataNode.Compound out = DataNode.Compound.empty().with("timer", p.getLong("timer", 0)).with("observation_type", type).with("to_observe", what);
                return new Task(t.id(), t.type(), carry(out, p));
            }
            case "dimension" -> {
                int dim = Integer.parseInt(p.getString("dimension", "0"));
                String name = switch (dim) {
                    case -1 -> "minecraft:the_nether";
                    case 1 -> "minecraft:the_end";
                    default -> "minecraft:overworld";
                };
                return new Task(t.id(), t.type(), carry(DataNode.Compound.empty().with("dimension", name), p));
            }
            case "advancement" -> {
                DataNode.Compound out = DataNode.Compound.empty().with("advancement", p.getString("advancement", "")).with("criterion", p.getString("criterion", ""));
                return new Task(t.id(), t.type(), carry(out, p));
            }
            case "fluid" -> {
                String f = fluid(p.getString("fluid", ""));
                DataNode.Compound stack = DataNode.Compound.empty().with("fluid", f).with("amount", (int) p.getLong("value", 1000));
                return new Task(t.id(), t.type(), carry(DataNode.Compound.empty().with("fluid", stack), p));
            }
            default -> {
                return new Task(t.id(), t.type(), carry(DataNode.Compound.empty(), p));
            }
        }
    }

    /** Carries over the settings that mean the same in both dialects (title, optional_task, icon). */
    private DataNode.Compound carry(DataNode.Compound out, DataNode.Compound p) {
        if (p.has("title")) {
            out = out.with("title", p.getString("title", ""));
        }
        if (p.has("optional_task")) {
            out = out.with("optional_task", p.getBool("optional_task", false));
        }
        if (p.get("icon") instanceof DataNode.Compound ic && !ic.entries().isEmpty()) {
            out = out.with("icon", icon(ic));
        }
        return out;
    }

    private Reward reward(Reward r, QuestBook book) {
        DataNode.Compound p = r.params();
        DataNode.Compound out = DataNode.Compound.empty();
        switch (r.type()) {
            case "item" -> {
                DataNode.Compound spec = item(p.getCompound("item"));
                if (spec == null) {
                    spec = DataNode.Compound.empty().with("id", "minecraft:barrier");
                    note("unresolved", p.getCompound("item").getString("id", "?"), "minecraft:barrier", "reward item has no 26.1.2 equivalent; a barrier stands in");
                }
                int count = p.getInt("count", 1);
                out = out.with("item", spec.with("count", Math.max(1, count)));
            }
            case "xp" -> out = out.with("xp", p.getInt("xp", 1));
            case "xp_levels" -> out = out.with("xp_levels", p.getInt("xp_levels", 1));
            case "command" -> {
                String cmd = p.getString("command", "");
                Matcher m = PROGRESS.matcher(cmd);
                if (m.matches()) {
                    cmd = "/ftbquests change_progress " + m.group(3) + " " + m.group(1) + " " + m.group(2);
                }
                out = out.with("command", cmd);
            }
            case "random", "loot", "choice", "all_table" -> {
                String id = p.getString("table_id", "");
                if (DIGITS.matcher(id).matches()) {
                    out = out.with("table_id", Long.parseLong(id));
                } else if (!id.isEmpty()) {
                    out = out.with("table_id", Long.parseUnsignedLong(id, 16)); // FTB Quests 26.1.2 writes the table id in decimal
                }
            }
            default -> out = p;
        }
        if (p.has("title")) {
            out = out.with("title", p.getString("title", ""));
        }
        if (p.has("auto")) {
            out = out.with("auto", p.getString("auto", "default"));
        }
        return new Reward(r.id(), r.type(), out);
    }

    // ------------------------------------------------------------------ chapter images and icons

    private ChapterImage image(ChapterImage img) {
        DataNode.Compound raw = img.raw();
        DataNode.Compound out = DataNode.Compound.empty().with("x", raw.getDouble("x", 0)).with("y", raw.getDouble("y", 0))
                .with("width", raw.getDouble("width", 1)).with("height", raw.getDouble("height", 1))
                .with("rotation", raw.getDouble("rotation", 0));
        String path = raw.getString("image", "");
        if (images.has(path)) {
            String to = images.getString(path, path);
            note("renamed", path, to, "texture folder renamed in 1.13");
            path = to;
        } else if (path.startsWith("lvimages:")) {
            note("kept", path, path, "texture lives in the user's lvimage mod; ship it as a resource pack");
        }
        out = out.with("image", path);
        out = out.with("order", raw.getInt("order", 0));
        List<String> hover = new ArrayList<>();
        for (DataNode h : raw.getList("hover")) {
            if (h instanceof DataNode.Str s) {
                hover.add(s.value());
            }
        }
        if (!hover.isEmpty()) {
            out = out.with("title", String.join(" - ", hover));
        }
        String click = raw.getString("click", "");
        if (!click.isEmpty()) {
            out = out.with("click_action", DataNode.Compound.empty().with("type", "open_uri").with("data", click));
            note("mapped", "image click " + click, "click_action open_uri", "keys read from a file FTB Quests 26.1.2 saved");
        }
        String hexId = img.id().matches("[0-9A-F]{16}") ? img.id() : String.format("%08X%08X", img.id().hashCode(), 0x1A6E);
        return new ChapterImage(hexId, out);
    }

    private DataNode.Compound icon(DataNode.Compound old) {
        if (old.entries().isEmpty()) {
            return old;
        }
        DataNode.Compound spec = item(old);
        if (spec == null) {
            return DataNode.Compound.empty();
        }
        return spec.with("count", 1);
    }

    // ------------------------------------------------------------------ item translation

    /** One item stack of the old pack ({id, damage?, nbt?}) as {id, components?}; null when it cannot be given a place. */
    private DataNode.Compound item(DataNode.Compound old) {
        String id = old.getString("id", "");
        if (id.isEmpty()) {
            return null;
        }
        // a plain texture path used as a picture
        if (id.contains("textures/")) {
            return null;
        }
        int meta = Math.max(0, old.getInt("damage", 0));
        DataNode.Compound nbt = old.getCompound("nbt");
        if (id.equals("minecraft:spawn_egg") || id.endsWith(":spawn_egg")) {
            String entity = nbt.getCompound("EntityTag").getString("id", "");
            if (!entity.isEmpty()) {
                String mapped = entity(entity.contains(":") ? entity : "minecraft:" + entity);
                int colon = mapped.indexOf(':');
                String egg = mapped.substring(0, colon + 1) + mapped.substring(colon + 1) + "_spawn_egg";
                note("mapped", id + " " + entity, egg, "spawn egg of that entity");
                return DataNode.Compound.empty().with("id", egg);
            }
        }
        String key = id + "@" + meta;
        DataNode hit = items.get(key);
        if (hit == null) {
            hit = vanilla.get(key);
        }
        if (hit instanceof DataNode.Compound e) {
            DataNode.Compound to = e.get("to") instanceof DataNode.Str ts ? DataNode.Compound.empty().with("id", ts.value()) : e.getCompound("to");
            String status = e.getString("status", "");
            if (to.has("id")) {
                if (!status.equals("renamed") || !id.startsWith("minecraft:")) {
                    note(status, key, to.getString("id", ""), e.getString("note", ""));
                } else {
                    count("mapped");
                }
                DataNode.Compound out = DataNode.Compound.empty().with("id", to.getString("id", ""));
                if (to.has("components")) {
                    out = out.with("components", to.getCompound("components"));
                }
                return out;
            }
            note("unresolved", key, "", e.getString("note", ""));
            return null;
        }
        int colon = id.indexOf(':');
        String ns = colon > 0 ? id.substring(0, colon) : "minecraft";
        if (keepMods.contains(ns)) {
            note("kept", key, id, "mod not installed in the 26.1.2 instance; id kept as written");
            return DataNode.Compound.empty().with("id", id);
        }
        count(meta > 0 ? "not-in-table-meta" : "same");
        if (meta > 0 && !ns.equals("minecraft")) {
            note("unresolved", key, id, "metadata " + meta + " has no entry in the table; the id is kept without it");
        }
        return DataNode.Compound.empty().with("id", id);
    }

    /** An FTB Filter System smart filter written by LegacyImporter: its ore() and item() pieces are translated. */
    private DataNode.Compound filterItem(DataNode.Compound spec) {
        String f = spec.getCompound("components").getString("ftbfiltersystem:filter", "");
        List<String> pieces = new ArrayList<>();
        for (String[] part : parts(f)) {
            String arg = part[1];
            if (part[0].equals("item")) {
                DataNode.Compound i = item(DataNode.Compound.empty().with("id", arg));
                if (i != null) {
                    pieces.add("item(" + i.getString("id", arg) + ")");
                }
            } else {
                DataNode e = oredict.get(arg);
                String tag = e instanceof DataNode.Compound c ? c.getString("tag", "") : "";
                if (e instanceof DataNode.Compound ec && !ec.getList("items").isEmpty()) {
                    StringBuilder listed = new StringBuilder();
                    for (DataNode in : ec.getList("items")) {
                        if (in instanceof DataNode.Str s) {
                            pieces.add("item(" + s.value() + ")");
                            listed.append(listed.length() == 0 ? "" : ", ").append(s.value());
                        }
                    }
                    note(ec.getString("status", "inferred"), "ore(" + arg + ")", "items " + listed, ec.getString("note", ""));
                } else if (!tag.isEmpty()) {
                    note(((DataNode.Compound) e).getString("status", "derived"), "ore(" + arg + ")", "tag(" + tag + ")", ((DataNode.Compound) e).getString("note", ""));
                    pieces.add("tag(" + tag + ")");
                } else {
                    note("unresolved", "ore(" + arg + ")", "", e instanceof DataNode.Compound c2 ? c2.getString("note", "no entry") : "no entry in the table");
                }
            }
        }
        if (pieces.isEmpty()) {
            return null;
        }
        String filter = pieces.size() == 1 ? pieces.get(0) : "or(" + String.join("", pieces) + ")";
        return DataNode.Compound.empty().with("id", "ftbfiltersystem:smart_filter")
                .with("components", DataNode.Compound.empty().with("ftbfiltersystem:filter", filter));
    }

    /** The {@code ore(...)} and {@code item(...)} pieces of a filter string, with nested brackets in names kept whole. */
    private static List<String[]> parts(String f) {
        List<String[]> out = new ArrayList<>();
        int i = 0;
        while (i < f.length()) {
            int open = f.indexOf('(', i);
            if (open < 0) {
                break;
            }
            int start = f.lastIndexOf(')', open) + 1;
            int from = Math.max(i, start);
            String kind = f.substring(from, open).trim();
            int depth = 1;
            int j = open + 1;
            while (j < f.length() && depth > 0) {
                char c = f.charAt(j++);
                depth += c == '(' ? 1 : c == ')' ? -1 : 0;
            }
            String body = f.substring(open + 1, j - 1);
            if (kind.equals("ore") || kind.equals("item")) {
                out.add(new String[] {kind, body});
            } else if (kind.equals("or")) {
                out.addAll(parts(body));
            }
            i = j;
        }
        return out;
    }

    private String entity(String old) {
        if (entities.has(old)) {
            note("mapped", old, entities.getString(old, old), "entity renamed");
            return entities.getString(old, old);
        }
        return BuiltinIdMap.entities().containsKey(old) ? old : old;
    }

    private String fluid(String old) {
        if (fluids.has(old)) {
            note("mapped", "fluid " + old, fluids.getString(old, old), "fluid id");
            return fluids.getString(old, old);
        }
        note("unresolved", "fluid " + old, old, "fluid has no 26.1.2 equivalent in the table");
        return old;
    }

    // ------------------------------------------------------------------ names

    static String slug(String s) {
        String t = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return t.isEmpty() ? "chapter" : t;
    }

    private static String uniqueName(String wanted, java.util.Set<String> used) {
        String n = wanted;
        int i = 2;
        while (!used.add(n)) {
            n = wanted + "_" + i++;
        }
        return n;
    }

    /** All notes as a map from kind to how many, for the summary. */
    public Map<String, Integer> noteKinds() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Note n : notes) {
            m.merge(n.kind(), 1, Integer::sum);
        }
        return m;
    }
}
