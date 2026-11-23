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

final class QuestText {

    private QuestText() {}

    static String rawTitle(Quest q) {
        if (!q.title.isEmpty()) {
            return q.title;
        }
        String auto = autoTitle(q);
        if (auto.isEmpty() && q.tasks.isEmpty()) {
            return "Unnamed";
        }
        return auto.isEmpty() ? q.id : auto;
    }

    static String title(Quest q) {
        return com.levviata.reflibquests.text.RichText.toRainbowAware(rawTitle(q));
    }

    static String titlePlain(Quest q) {
        return com.levviata.reflibquests.text.RichText.toSectionCodes(rawTitle(q));
    }

    static String taskTitle(Task t) {
        Quest holder = new Quest("task-title");
        holder.tasks.add(t);
        return autoTitle(holder);
    }

    private static String autoTitle(Quest q) {
        return autoTitle(q, true);
    }

    private static String autoTitle(Quest q, boolean ownTitle) {
        if (q.tasks.isEmpty()) {
            return "";
        }
        Task t = q.tasks.get(0);
        String custom = t.params().getString("title", "");
        if (ownTitle && !custom.isEmpty()) {
            return custom;
        }
        switch (t.type()) {
            case "item": {
                DataNode spec = t.params().get("item");
                java.util.List<ItemResolver.Resolved> set = setOf(spec);

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
                return (points ? "XP: " : "XP Levels: ") + "\u00a7c" + t.maxProgress();
            }
            case "location":
                return "Location";
            case "fluid": {

                long mb = t.maxProgress();
                String fl = t.params().get("fluid") instanceof DataNode.Compound fc ? fc.getString("fluid", "") : "";
                String name = titleCase(fl.isEmpty() ? "water" : fl);
                return (mb % 1000 == 0 ? mb / 1000 + " B" : mb + " mB") + " of " + name + " Bucket";
            }
            case "stat":
                return titleCase(t.params().getString("stat", ""));
            case "dimension":
                return "Visit Dimension: \u00a72" + t.params().getString("dimension", "");
            case "biome":
                return "Visit Biome: \u00a72" + t.params().getString("biome", "");
            case "observation":
                return "Observation: \u00a72" + t.params().getString("to_observe", "");
            case "gamestage":
                return "Stage: " + t.params().getString("stage", "");
            case "interaction":
                return "Interaction: " + t.params().getString("to_interact", "minecraft:air");
            case "forge_energy":
                return "Forge Energy: \u00a72" + t.maxProgress() + " FE";
            case "advancement": {
                String adv = t.params().getString("advancement", "");
                String last = adv.contains("/") ? adv.substring(adv.lastIndexOf('/') + 1) : adv.contains(":") ? adv.substring(adv.indexOf(':') + 1) : adv;
                return "Advancement: \u00a7e" + (last.equals("root") ? titleCase(adv.contains(":") ? adv.substring(0, adv.indexOf(':')) : adv) : titleCase(last));
            }
            default:
                return pretty(t.type());
        }
    }

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

    static String itemName(@Nullable DataNode spec) {
        ItemStack s = stackOf(spec);
        if (s != null) {
            return s.getDisplayName();
        }
        String id = spec instanceof DataNode.Str str ? str.value() : spec instanceof DataNode.Compound c ? c.getString("id", "") : "";
        return id.equals(com.levviata.reflibquests.game.ItemSet.SMART_FILTER) ? "Any of these items" : id.isEmpty() ? "an unknown item" : pretty(id);
    }

    static String titleRainbow(Quest q) {
        return com.levviata.reflibquests.text.RichText.toRainbowAware(rawTitle(q));
    }

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
        if (spec instanceof DataNode.Compound c && c.get("image") instanceof DataNode.Str img && !img.value().isEmpty()) {
            return imageMarker(img.value());
        }
        java.util.List<ItemResolver.Resolved> set = setOf(spec);
        if (set.isEmpty()) {
            return null;
        }
        if (com.levviata.reflibquests.game.ItemSet.isFilter(spec)) {
            return set.get((int) ((System.currentTimeMillis() / 2000) % set.size())).toStack(1);
        }
        return set.get(0).toStack(Math.max(1, set.get(0).count()));
    }

    static ItemStack imageMarker(String id) {
        ItemStack marker = new ItemStack(net.minecraft.init.Items.PAINTING);
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        tag.setString(IMAGE_TAG, id);
        marker.setTagCompound(tag);
        return marker;
    }

    static final String IMAGE_TAG = "reflib_image";

    @Nullable
    static String imageOf(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(IMAGE_TAG) ? stack.getTagCompound().getString(IMAGE_TAG) : null;
    }

    @Nullable
    static ItemStack stackOfOld(@Nullable DataNode spec) {
        ItemResolver.Resolved r = ItemResolver.resolve(spec, ClientQuests.idMap());
        return r == null ? null : r.toStack(Math.max(1, r.count()));
    }

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

    @Nullable
    static net.minecraftforge.fluids.Fluid fluidOf(Task t) {
        DataNode own = t.params().get("icon");
        if (own instanceof DataNode.Compound c && !c.entries().isEmpty()) {
            return null;
        }
        DataNode f = t.params().get("fluid");
        String name = f instanceof DataNode.Compound fc ? fc.getString("fluid", "") : f instanceof DataNode.Str fs ? fs.value() : "";
        int colon = name.indexOf(':');
        try {
            return net.minecraftforge.fluids.FluidRegistry.getFluid(colon < 0 ? name : name.substring(colon + 1));
        } catch (RuntimeException ex) {
            return null;
        }
    }

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
                return null;
            case "biome":
                return new ItemStack(net.minecraft.init.Blocks.SAPLING);
            case "structure":
                return new ItemStack(net.minecraft.init.Items.PAPER);
            case "gamestage":
                return null;
            case "interaction":
                return new ItemStack(net.minecraft.init.Items.DIAMOND_PICKAXE);
            case "forge_energy":
                return new ItemStack(net.minecraft.init.Blocks.REDSTONE_BLOCK);
            default:
                return null;
        }
    }

    static String autoMode(com.levviata.reflibquests.model.QuestBook book, Reward r) {
        String mode = r.autoClaim();
        return mode.equals("default") ? book.settings.getString("default_autoclaim_rewards", "disabled") : mode;
    }

    static boolean hiddenReward(com.levviata.reflibquests.model.QuestBook book, Reward r) {
        return autoMode(book, r).equals("invisible");
    }

    static boolean silentReward(com.levviata.reflibquests.model.QuestBook book, Reward r) {
        String mode = autoMode(book, r);
        return mode.equals("no_toast") || mode.equals("invisible");
    }

    @Nullable
    static net.minecraftforge.fluids.Fluid iconFluid(Quest q) {
        if (!q.icon.entries().isEmpty() && stackOf(q.icon) != null) {
            return null;
        }
        for (Task t : q.tasks) {
            if (taskIcon(t) != null) {
                return t.type().equals("fluid") ? fluidOf(t) : null;
            }
        }
        return null;
    }

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
        Quest holder = new Quest("task-title");
        holder.tasks.add(t);
        String title = autoTitle(holder, true);
        return title.isEmpty() ? taskSentence(t) : title;
    }

    private static String taskSentence(Task t) {
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
            return p.getString("title", "");
        }
        switch (r.type()) {
            case "item": {
                ItemStack s = stackOf(p.get("item"));
                int count = (int) Math.min(Integer.MAX_VALUE, Task.itemCount(p));
                return (count > 1 ? count + "x " : "") + (s == null ? itemName(p.get("item")) : s.getDisplayName());
            }
            case "xp":
                return "XP: §a+" + Math.max(1, p.getInt("xp", 1));
            case "xp_levels":
                return "XP Levels: §a+" + Math.max(1, p.getInt("xp_levels", 1));
            case "command":
                return "Command: §c" + p.getString("command", "");
            case "toast":
                return "Toast";
            case "custom":
                return "Custom";
            case "gamestage":
                return "Stage: §e" + p.getString("stage", "");
            case "advancement": {
                String adv = p.getString("advancement", "");
                String last = adv.contains("/") ? adv.substring(adv.lastIndexOf('/') + 1) : adv.contains(":") ? adv.substring(adv.indexOf(':') + 1) : adv;
                return "Advancement: §e" + (last.equals("root") ? titleCase(adv.contains(":") ? adv.substring(0, adv.indexOf(':')) : adv) : titleCase(last));
            }
            case "choice":
            case "random":
            case "loot":
            case "all_table": {

                com.levviata.reflibquests.model.QuestBook qb = ClientQuests.book();
                com.levviata.reflibquests.model.Definitions.RewardTable table = qb == null ? null : qb.tables().get(tableId(r));
                if (table != null && !table.title.isEmpty()) {
                    return table.title;
                }
                return r.type().equals("choice") ? "Choose one reward" : r.type().equals("random") ? "Random pick" : r.type().equals("loot") ? "Loot" : "Several rewards";
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
