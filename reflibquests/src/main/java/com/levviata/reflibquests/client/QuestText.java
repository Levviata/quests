/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.game.ItemResolver;
import com.levviata.reflibquests.io.Ids;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.Task;

/** Short English descriptions of tasks and rewards, and icon lookup, for the quest screen. */
final class QuestText {

    private QuestText() {}

    /** The quest's title as written (with {@code &} codes); quests without one are named after their first task. */
    static String rawTitle(Quest q) {
        if (!q.title.isEmpty()) {
            return q.title;
        }
        String auto = autoTitle(q);
        return auto.isEmpty() ? q.id : auto;
    }

    /** The title for drawing: formatting codes as section signs, {@code &z} kept as a rainbow marker (draw it with {@code Ui.drawText}). */
    static String title(Quest q) {
        return com.levviata.reflibquests.text.RichText.toRainbowAware(rawTitle(q));
    }

    /** The title for places that cannot show a rainbow (chat). */
    static String titlePlain(Quest q) {
        return com.levviata.reflibquests.text.RichText.toSectionCodes(rawTitle(q));
    }

    /** The name of one task as FTB Quests 26.1.2 shows it in menus and window titles ("Kill: 100x Zombie"), colour codes included. */
    static String taskTitle(Task t) {
        Quest holder = new Quest("task-title");
        holder.tasks.add(t);
        return autoTitle(holder);
    }

    private static String autoTitle(Quest q) {
        if (q.tasks.isEmpty()) {
            return "";
        }
        Task t = q.tasks.get(0);
        String custom = t.params().getString("title", "");
        if (!custom.isEmpty()) {
            return custom;
        }
        switch (t.type()) {
            case "item": {
                DataNode spec = t.params().get("item");
                java.util.List<ItemResolver.Resolved> set = setOf(spec);
                // a quest without a title of its own is named after what it asks for, with the amount: "80x Iron Ingot"
                String amount = t.maxProgress() > 1 ? t.maxProgress() + "x " : "";
                if (!set.isEmpty()) {
                    return amount + set.get(0).toStack(1).getDisplayName();
                }
                String id = spec instanceof DataNode.Str s ? s.value() : spec instanceof DataNode.Compound c ? c.getString("id", "") : "";
                return amount + (id.equals(com.levviata.reflibquests.game.ItemSet.SMART_FILTER) ? "Any of these items" : pretty(id));
            }
            case "checkmark":
                return "Checkmark";
            case "kill":
                return "Kill: " + t.maxProgress() + "x " + entityName(t.params().getString("entity", ""));
            case "xp": {
                boolean points = t.params().getBool("points", false);
                net.minecraft.client.entity.EntityPlayerSP me = net.minecraft.client.Minecraft.getMinecraft().player;
                long have = me == null ? Long.MAX_VALUE : points ? me.experienceTotal : me.experienceLevel;
                return (points ? "XP: " : "XP Levels: ") + (have < t.maxProgress() ? "\u00a7c" : "") + t.maxProgress(); // red while the player is short of it
            }
            case "location":
                return "Location";
            case "fluid": {
                // "1 B of Water Bucket", as FTB Quests names it
                long mb = t.maxProgress();
                String fl = t.params().get("fluid") instanceof DataNode.Compound fc ? fc.getString("fluid", "") : "";
                String name = titleCase(fl.isEmpty() ? "water" : fl);
                return (mb % 1000 == 0 ? mb / 1000 + " B" : mb + " mB") + " of " + name + " Bucket";
            }
            case "stat":
                return titleCase(t.params().getString("stat", ""));
            case "dimension":
                return "Visit Dimension: \u00a7a" + t.params().getString("dimension", "");
            case "biome":
                return "Visit Biome: \u00a7a" + t.params().getString("biome", "");
            case "observation":
                return "Observation: \u00a7a" + t.params().getString("to_observe", "");
            case "gamestage":
                return "Stage: " + t.params().getString("stage", "");
            case "interaction":
                return "Interaction: " + t.params().getString("to_interact", "minecraft:air"); // FTB: "Interaction: minecraft:air"
            case "forge_energy":
                return "Forge Energy: \u00a7a" + t.maxProgress() + " FE";
            case "advancement": {
                String adv = t.params().getString("advancement", "");
                String last = adv.contains("/") ? adv.substring(adv.lastIndexOf('/') + 1) : adv.contains(":") ? adv.substring(adv.indexOf(':') + 1) : adv;
                return "Advancement: \u00a7e" + (last.equals("root") ? titleCase(adv.contains(":") ? adv.substring(0, adv.indexOf(':')) : adv) : titleCase(last));
            }
            default:
                return pretty(t.type());
        }
    }

    /** "minecraft:mob_kills" becomes "Mob Kills". */
    private static String titleCase(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        StringBuilder out = new StringBuilder();
        for (String word : path.split("[_ /]+")) {
            if (!word.isEmpty()) {
                out.append(out.length() > 0 ? " " : "").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    /**
     * Whether claiming the reward would actually give something in this game: items must exist, tables must exist and
     * contain at least one reward that can be given. Rewards that cannot are not offered as claimable.
     */
    static boolean grantable(com.levviata.reflibquests.model.QuestBook book, com.levviata.reflibquests.model.Definitions.Reward r, int depth) {
        switch (r.type()) {
            case "item":
                return !setOf(r.params().get("item")).isEmpty();
            case "random":
            case "loot":
            case "all_table":
            case "choice": {
                if (depth > 3) {
                    return false;
                }
                com.levviata.reflibquests.model.Definitions.RewardTable table = book.tables().get(tableId(r));
                if (table == null) {
                    return false;
                }
                for (com.levviata.reflibquests.model.Definitions.WeightedReward w : table.entries) {
                    if (grantable(book, w.reward(), depth + 1)) {
                        return true;
                    }
                }
                return table.entries.isEmpty();
            }
            default:
                return true;
        }
    }

    /** A readable name for an item spec: the real name when the item exists, else its id made readable. */
    static String itemName(@Nullable DataNode spec) {
        ItemStack s = stackOf(spec);
        if (s != null) {
            return s.getDisplayName();
        }
        String id = spec instanceof DataNode.Str str ? str.value() : spec instanceof DataNode.Compound c ? c.getString("id", "") : "";
        return id.equals(com.levviata.reflibquests.game.ItemSet.SMART_FILTER) ? "Any of these items" : id.isEmpty() ? "an unknown item" : pretty(id);
    }

    /** Title with a rainbow marker kept for {@code &z} (for the few places that can draw rainbow text). */
    static String titleRainbow(Quest q) {
        return com.levviata.reflibquests.text.RichText.toRainbowAware(rawTitle(q));
    }

    /** {@code minecraft:stripped_oak_log} becomes "Stripped Oak Log". */
    private static String pretty(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        StringBuilder sb = new StringBuilder();
        for (String word : path.replace('/', ' ').replace('_', ' ').split(" +")) {
            if (!word.isEmpty()) {
                sb.append(sb.length() > 0 ? " " : "").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return sb.toString();
    }

    private static final java.util.IdentityHashMap<DataNode, java.util.List<ItemResolver.Resolved>> SETS = new java.util.IdentityHashMap<>();
    private static int setsVersion = -1;

    /** The items a task or reward spec accepts, remembered until the book changes. */
    static java.util.List<ItemResolver.Resolved> setOf(@Nullable DataNode spec) {
        if (spec == null) {
            return java.util.List.of();
        }
        int version = ClientQuests.bookVersion();
        if (version != setsVersion) {
            SETS.clear();
            setsVersion = version;
        }
        return SETS.computeIfAbsent(spec, s -> com.levviata.reflibquests.game.ItemSet.resolveAll(s, ClientQuests.idMap()));
    }

    @Nullable
    static ItemStack stackOf(@Nullable DataNode spec) {
        java.util.List<ItemResolver.Resolved> set = setOf(spec);
        if (set.isEmpty()) {
            return null;
        }
        if (com.levviata.reflibquests.game.ItemSet.isFilter(spec)) {
            return set.get((int) ((System.currentTimeMillis() / 1200) % set.size())).toStack(1); // a collection cycles its items
        }
        return set.get(0).toStack(Math.max(1, set.get(0).count()));
    }

    @Nullable
    static ItemStack stackOfOld(@Nullable DataNode spec) {
        ItemResolver.Resolved r = ItemResolver.resolve(spec, ClientQuests.idMap());
        return r == null ? null : r.toStack(Math.max(1, r.count()));
    }

    /** The name the game shows for an entity ("Balls-O-Tron Prime"); the readable form of its id when the game has no name for it. */
    static String entityName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String mapped = ClientQuests.idMap().entity(raw);
        String id = mapped == null || mapped.isEmpty() ? raw : mapped;
        net.minecraft.util.ResourceLocation key = new net.minecraft.util.ResourceLocation(id.contains(":") ? id : "minecraft:" + id);
        String translation = net.minecraft.entity.EntityList.getTranslationName(key);
        if (translation != null) {
            String lang = "entity." + translation + ".name";
            if (net.minecraft.client.resources.I18n.hasKey(lang)) {
                return net.minecraft.client.resources.I18n.format(lang);
            }
        }
        return titleCase(raw);
    }

    /** The spawn egg of an entity, as the icon of a kill task. */
    static ItemStack spawnEgg(String entityName) {
        String id = entityName == null ? "" : ClientQuests.idMap().entity(entityName);
        if (id.isEmpty()) {
            return null;
        }
        net.minecraft.nbt.NBTTagCompound entity = new net.minecraft.nbt.NBTTagCompound();
        entity.setString("id", id.contains(":") ? id : "minecraft:" + id);
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        tag.setTag("EntityTag", entity);
        ItemStack egg = new ItemStack(net.minecraft.init.Items.SPAWN_EGG);
        egg.setTagCompound(tag);
        return egg;
    }

    /** The icon a task shows: the one it names itself, the spawn egg of a kill task, or the item of an item task; else null. */
    @Nullable
    static ItemStack taskIcon(Task t) {
        DataNode own = t.params().get("icon");
        if (own instanceof DataNode.Compound c && !c.entries().isEmpty()) {
            ItemStack s = stackOf(c);
            if (s != null) {
                return s;
            }
        }
        if (t.type().equals("kill")) {
            return spawnEgg(t.params().getString("entity", ""));
        }
        if (t.type().equals("item")) {
            return stackOf(t.params().get("item"));
        }
        // the picture of each task type, as in FTB Quests' own list of them
        switch (t.type()) {
            case "xp":
                return new ItemStack(net.minecraft.init.Items.EXPERIENCE_BOTTLE);
            case "fluid": {
                DataNode f = t.params().get("fluid");
                String name = f instanceof DataNode.Compound fc ? fc.getString("fluid", "") : f instanceof DataNode.Str fs ? fs.value() : "";
                int colon = name.indexOf(':');
                String bare = colon < 0 ? name : name.substring(colon + 1);
                try {
                    net.minecraftforge.fluids.Fluid fl = net.minecraftforge.fluids.FluidRegistry.getFluid(bare);
                    ItemStack bucket = fl == null ? ItemStack.EMPTY : net.minecraftforge.fluids.FluidUtil.getFilledBucket(new net.minecraftforge.fluids.FluidStack(fl, 1000));
                    return bucket.isEmpty() ? null : bucket;
                } catch (RuntimeException ex) {
                    return null;
                }
            }
            case "dimension":
                return new ItemStack(net.minecraft.init.Items.ENDER_EYE);
            case "stat":
                return new ItemStack(net.minecraft.init.Items.IRON_SWORD);
            case "location":
                return new ItemStack(net.minecraft.init.Items.COMPASS);
            case "advancement":
                return t.params().getString("advancement", "").endsWith("/root")
                        ? new ItemStack(net.minecraft.init.Blocks.GRASS) : new ItemStack(net.minecraft.init.Items.WHEAT);
            case "observation":
                return null; // drawn as the library's landscape picture (see the widget)
            case "biome":
                return new ItemStack(net.minecraft.init.Blocks.SAPLING);
            case "structure":
                return new ItemStack(net.minecraft.init.Items.PAPER);
            case "gamestage":
                return null; // drawn as the library's controller picture (see the widget)
            case "interaction":
                return new ItemStack(net.minecraft.init.Items.DIAMOND_PICKAXE);
            case "forge_energy":
                return new ItemStack(net.minecraft.init.Blocks.REDSTONE_BLOCK);
            default:
                return null;
        }
    }

    /** The quest's icon: its own, else the first item task's item, else the first item reward's item. */
    @Nullable
    static ItemStack iconOf(Quest q) {
        if (!q.icon.entries().isEmpty()) {
            ItemStack s = stackOf(q.icon);
            if (s != null) {
                return s;
            }
        }
        for (Task t : q.tasks) {
            ItemStack s = taskIcon(t);
            if (s != null) {
                return s;
            }
        }
        for (Reward r : q.rewards) {
            if (r.type().equals("item")) {
                ItemStack s = stackOf(r.params().get("item"));
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    static String task(Task t) {
        DataNode.Compound p = t.params();
        switch (t.type()) {
            case "item": {
                ItemStack s = stackOf(p.get("item"));
                String name = s == null ? itemName(p.get("item")) : s.getDisplayName();
                return (t.maxProgress() > 1 ? "Collect " + t.maxProgress() + "x " : "Collect ") + name;
            }
            case "checkmark":
                return "Confirm";
            case "xp":
                return p.getBool("points", false) ? "Gain " + t.maxProgress() + " XP points" : "Reach XP level " + t.maxProgress();
            case "kill":
                return "Kill " + t.maxProgress() + "x " + shortName(p.getString("entity", "?"));
            case "advancement":
                return "Earn advancement " + p.getString("advancement", "?");
            case "dimension":
                return "Visit " + shortName(p.getString("dimension", "?"));
            case "location":
                return "Reach a location";
            case "biome":
                return "Visit the " + shortName(p.getString("biome", "?")) + " biome";
            case "structure":
                return "Find " + shortName(p.getString("structure", "?"));
            case "observation":
                return "Look at " + shortName(p.getString("to_observe", "?"));
            case "gamestage":
                return "Stage: " + p.getString("stage", "?");
            case "interaction":
                return "Interact with " + shortName(p.getString("to_interact", "minecraft:air"));
            case "forge_energy":
                return "Forge Energy: " + t.maxProgress() + " FE";
            default:
                return t.type() + " (not supported yet)";
        }
    }

    static String reward(Reward r) {
        DataNode.Compound p = r.params();
        if (!r.type().equals("item") && !p.getString("title", "").isEmpty()) {
            return p.getString("title", ""); // the name the author gave it ("100% Complete Chapter")
        }
        switch (r.type()) {
            case "item": {
                ItemStack s = stackOf(p.get("item"));
                int count = (int) Math.min(Integer.MAX_VALUE, Task.itemCount(p));
                return (count > 1 ? count + "x " : "") + (s == null ? itemName(p.get("item")) : s.getDisplayName());
            }
            case "xp":
                return "XP: +" + Math.max(1, p.getInt("xp", 1)); // as FTB Quests names it
            case "xp_levels":
                return "XP Levels: +" + Math.max(1, p.getInt("xp_levels", 1));
            case "command":
                return "Command: §c" + p.getString("command", ""); // the command in red, as FTB Quests shows it
            case "toast":
                return "Toast";
            case "advancement": {
                String adv = p.getString("advancement", "");
                String last = adv.contains("/") ? adv.substring(adv.lastIndexOf('/') + 1) : adv.contains(":") ? adv.substring(adv.indexOf(':') + 1) : adv;
                return "Advancement: §e" + (last.equals("root") ? titleCase(adv.contains(":") ? adv.substring(0, adv.indexOf(':')) : adv) : titleCase(last));
            }
            case "choice":
            case "random":
            case "loot":
            case "all_table": {
                // FTB Quests names a table reward after its table
                com.levviata.reflibquests.model.QuestBook qb = ClientQuests.book();
                com.levviata.reflibquests.model.Definitions.RewardTable table = qb == null ? null : qb.tables().get(tableId(r));
                if (table != null && !table.title.isEmpty()) {
                    return table.title;
                }
                return r.type().equals("choice") ? "Choose one reward" : r.type().equals("random") ? "A random reward" : r.type().equals("loot") ? "Loot" : "Several rewards";
            }
            default:
                return r.type() + " (not supported yet)";
        }
    }

    static String tableId(Reward r) {
        return Ids.tableId(r.params());
    }

    private static String shortName(String id) {
        int c = id.indexOf(':');
        String s = c >= 0 ? id.substring(c + 1) : id;
        return s.replace('_', ' ');
    }
}
