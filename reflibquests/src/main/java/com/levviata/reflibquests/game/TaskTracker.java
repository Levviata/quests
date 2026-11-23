/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.game;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.io.Names;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.model.QuestRules;
import com.levviata.reflibquests.model.TeamProgress;

public final class TaskTracker {

    private record Entry(Quest quest, Task task) {}

    private final QuestServer quests;
    private final List<Entry> entries = new ArrayList<>();

    TaskTracker(QuestServer quests) {
        this.quests = quests;
    }

    void rebuild() {
        entries.clear();
        for (Quest q : quests.book().allQuests()) {
            for (Task t : q.tasks) {
                entries.add(new Entry(q, t));
            }
        }
    }

    private int detectionDelay() {
        return Math.max(1, quests.book().settings.getInt("detection_delay", 20));
    }

    private boolean open(TeamProgress team, Entry e) {
        return !team.isCompleted(e.quest().id)
                && !QuestRules.taskDone(team, e.task())
                && QuestRules.canProgress(quests.book(), team, e.quest());
    }

    public void tick(EntityPlayerMP player) {
        if (player.ticksExisted % detectionDelay() != 0) {
            return;
        }
        TeamProgress team = quests.progressOf(player);
        for (Entry e : new ArrayList<>(entries)) {
            if (!open(team, e)) {
                continue;
            }
            Task t = e.task();
            switch (t.type()) {
                case "item":
                    pollItem(player, t, e);
                    break;
                case "stat": {
                    net.minecraft.stats.StatBase stat = statOf(t.params().getString("stat", ""));
                    if (stat != null) {
                        quests.setTaskProgress(player, t, Math.min(t.maxProgress(), player.getStatFile().readStat(stat)));
                    }
                    break;
                }
                case "advancement": {

                    net.minecraft.server.MinecraftServer server = player.getServer();
                    String id = t.params().getString("advancement", "");
                    net.minecraft.advancements.Advancement adv = server == null || id.isEmpty() ? null
                            : server.getAdvancementManager().getAdvancement(new ResourceLocation(id));
                    if (adv != null) {
                        net.minecraft.advancements.AdvancementProgress progress = player.getAdvancements().getProgress(adv);
                        String criterion = t.params().getString("criterion", "");
                        net.minecraft.advancements.CriterionProgress cp = criterion.isEmpty() ? null : progress.getCriterionProgress(criterion);
                        if (criterion.isEmpty() ? progress.isDone() : cp != null && cp.isObtained()) {
                            quests.setTaskProgress(player, t, 1);
                        }
                    }
                    break;
                }
                case "gamestage": {

                    String stage = t.params().getString("stage", "");
                    if (!stage.isEmpty() && player.getTags().contains(stage)) {
                        quests.setTaskProgress(player, t, 1);
                    }
                    break;
                }
                case "dimension": {
                    var id = Names.dimensionId(t.params().getString("dimension", ""));
                    if (id.isPresent() && player.dimension == id.getAsInt()) {
                        quests.setTaskProgress(player, t, 1);
                    }
                    break;
                }
                case "location":
                    if (inLocation(player, t.params())) {
                        quests.setTaskProgress(player, t, 1);
                    }
                    break;
                case "biome": {
                    net.minecraft.world.biome.Biome wanted = net.minecraftforge.fml.common.registry.ForgeRegistries.BIOMES.getValue(
                            new net.minecraft.util.ResourceLocation(t.params().getString("biome", "minecraft:none")));
                    if (wanted != null && player.world.getBiome(player.getPosition()) == wanted) {
                        quests.setTaskProgress(player, t, 1);
                    }
                    break;
                }
                case "structure": {
                    String name = structureName(t.params().getString("structure", ""));
                    if (!name.isEmpty() && player.world instanceof net.minecraft.world.WorldServer ws
                            && ws.getChunkProvider().isInsideStructure(ws, name, player.getPosition())) {
                        quests.setTaskProgress(player, t, 1);
                    }
                    break;
                }
                case "observation": {

                    net.minecraft.util.math.RayTraceResult hit = player.rayTrace(6.0, 1.0f);
                    if (hit != null && hit.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
                        net.minecraft.util.ResourceLocation seen = player.world.getBlockState(hit.getBlockPos()).getBlock().getRegistryName();
                        if (seen != null && seen.toString().equals(t.params().getString("to_observe", ""))) {
                            quests.setTaskProgress(player, t, 1);
                        }
                    }
                    break;
                }
                default:
                    break;
            }
        }
    }

    static net.minecraft.stats.StatBase statOf(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String old;
        switch (path) {
            case "mob_kills": old = "stat.mobKills"; break;
            case "player_kills": old = "stat.playerKills"; break;
            case "deaths": old = "stat.deaths"; break;
            case "jump": old = "stat.jump"; break;
            case "damage_dealt": old = "stat.damageDealt"; break;
            case "damage_taken": old = "stat.damageTaken"; break;
            case "play_one_minute": case "play_time": old = "stat.playOneMinute"; break;
            case "time_since_death": old = "stat.timeSinceDeath"; break;
            case "walk_one_cm": old = "stat.walkOneCm"; break;
            case "sprint_one_cm": old = "stat.sprintOneCm"; break;
            case "crouch_one_cm": old = "stat.crouchOneCm"; break;
            case "swim_one_cm": old = "stat.swimOneCm"; break;
            case "fall_one_cm": old = "stat.fallOneCm"; break;
            case "climb_one_cm": old = "stat.climbOneCm"; break;
            case "fly_one_cm": old = "stat.flyOneCm"; break;
            case "boat_one_cm": old = "stat.boatOneCm"; break;
            case "horse_one_cm": old = "stat.horseOneCm"; break;
            case "minecart_one_cm": old = "stat.minecartOneCm"; break;
            case "pig_one_cm": old = "stat.pigOneCm"; break;
            case "animals_bred": old = "stat.animalsBred"; break;
            case "fish_caught": old = "stat.fishCaught"; break;
            case "talked_to_villager": old = "stat.talkedToVillager"; break;
            case "traded_with_villager": old = "stat.tradedWithVillager"; break;
            case "leave_game": old = "stat.leaveGame"; break;
            case "drop": old = "stat.drop"; break;
            default: return null;
        }
        return net.minecraft.stats.StatList.getOneShotStat(old);
    }

    private static net.minecraft.nbt.NBTTagCompound fluidNbt(DataNode.Compound p) {
        if (!(p.get("fluid") instanceof DataNode.Compound f) || f.getCompound("nbt").entries().isEmpty()) {
            return null;
        }
        try {
            return net.minecraft.nbt.JsonToNBT.getTagFromJson(com.levviata.reflib.api.data.Snbt.printCompact(f.getCompound("nbt")));
        } catch (net.minecraft.nbt.NBTException | RuntimeException ex) {
            return null;
        }
    }

    static String fluidNameOf(DataNode.Compound p) {
        DataNode f = p.get("fluid");
        String name = f instanceof DataNode.Compound c ? c.getString("fluid", "") : f instanceof DataNode.Str s ? s.value() : "";
        return name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
    }

    private String submitFluid(EntityPlayerMP player, Task t) {
        TeamProgress team = quests.progressOf(player);
        Quest q = quests.book().questOfTask(t.id());
        if (team.isCompleted(q.id) || QuestRules.taskDone(team, t)) {
            return "Already done.";
        }
        if (!QuestRules.canProgress(quests.book(), team, q) || !QuestRules.dependenciesMet(quests.book(), team, q)) {
            return "That quest is not available yet.";
        }
        String fluid = fluidNameOf(t.params());
        net.minecraft.nbt.NBTTagCompound wantedNbt = fluidNbt(t.params());
        boolean exact = exactNbt(t);
        long need = t.maxProgress() - team.taskProgress(t.id());
        long taken = 0;
        for (int i = 0; i < player.inventory.mainInventory.size() && taken < need; i++) {
            while (taken < need) {
                ItemStack stack = player.inventory.mainInventory.get(i);
                if (stack.isEmpty()) {
                    break;
                }
                ItemStack one = stack.copy();
                one.setCount(1);
                net.minecraftforge.fluids.capability.IFluidHandlerItem handler = net.minecraftforge.fluids.FluidUtil.getFluidHandler(one);
                net.minecraftforge.fluids.FluidStack contained = handler == null ? null : net.minecraftforge.fluids.FluidUtil.getFluidContained(one);
                if (contained == null || contained.getFluid() == null || !contained.getFluid().getName().equals(fluid)) {
                    break;
                }
                if (wantedNbt != null && !net.minecraft.nbt.NBTUtil.areNBTEquals(wantedNbt, contained.tag, !exact)) {
                    break;
                }
                net.minecraftforge.fluids.FluidStack drained = handler.drain((int) Math.min(contained.amount, need - taken), true);
                if (drained == null || drained.amount <= 0) {
                    break;
                }
                taken += drained.amount;
                ItemStack result = handler.getContainer();
                if (stack.getCount() == 1) {
                    player.inventory.mainInventory.set(i, result);
                } else {
                    stack.shrink(1);
                    if (!player.inventory.addItemStackToInventory(result)) {
                        player.dropItem(result, false);
                    }
                    break;
                }
            }
        }
        if (taken == 0) {
            return "You do not carry a container with that fluid.";
        }
        quests.addTaskProgress(player, t, taken);
        player.inventoryContainer.detectAndSendChanges();
        return "Handed in " + taken + " mB.";
    }

    static String structureName(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (id.contains(":") && !id.startsWith("minecraft:")) {
            return "";
        }
        switch (path) {
            case "village":
                return "Village";
            case "mineshaft":
                return "Mineshaft";
            case "stronghold":
                return "Stronghold";
            case "fortress":
            case "nether_fortress":
                return "Fortress";
            case "mansion":
            case "woodland_mansion":
                return "Mansion";
            case "monument":
            case "ocean_monument":
                return "Monument";
            case "desert_pyramid":
            case "jungle_pyramid":
            case "jungle_temple":
            case "desert_temple":
            case "igloo":
            case "swamp_hut":
                return "Temple";
            case "end_city":
                return "EndCity";
            default:
                return "";
        }
    }

    private boolean consumes(Task t) {
        DataNode n = t.params().get("consume_items");
        if (n != null) {
            return t.params().getBool("consume_items", false);
        }
        boolean fallback = quests.book().settings.getBool("default_consume_items", false);
        com.levviata.reflibquests.model.Definitions.Quest owner = quests.book().questOfTask(t.id());
        com.levviata.reflibquests.model.Definitions.Chapter in = owner == null ? null : quests.book().chapterOf(owner.id);
        return in == null ? fallback : in.defaultConsumeItems.resolve(fallback);
    }

    private void pollItem(EntityPlayerMP player, Task t, Entry e) {
        if (consumes(t) || t.params().getBool("only_from_crafting", false)) {
            return;
        }
        List<ItemResolver.Resolved> set = ItemSet.resolveAll(t.params().get("item"), quests.idMap());
        if (set.isEmpty()) {
            return;
        }
        quests.setTaskProgress(player, t, Math.min(t.maxProgress(), countItems(player, set, exactNbt(t))));
    }

    private static boolean exactNbt(Task t) {
        return t.params().getString("match_components", "default").equals("exact");
    }

    private static long countItems(EntityPlayerMP player, List<ItemResolver.Resolved> set, boolean exact) {
        long n = 0;
        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            ItemStack s = player.inventory.mainInventory.get(i);
            if (ItemSet.matches(set, s, exact)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static boolean inLocation(EntityPlayerMP player, DataNode.Compound p) {
        var dim = Names.dimensionId(p.getString("dimension", "minecraft:overworld"));
        if (!p.getBool("ignore_dimension", false) && (dim.isEmpty() || dim.getAsInt() != player.dimension)) {
            return false;
        }
        var pos = p.getList("position");
        var size = p.getList("size");
        if (pos.size() != 3 || size.size() != 3) {
            return false;
        }
        double[] min = new double[3];
        double[] max = new double[3];
        for (int i = 0; i < 3; i++) {
            if (!(pos.get(i) instanceof DataNode.Int a) || !(size.get(i) instanceof DataNode.Int s)) {
                return false;
            }
            min[i] = a.value();
            max[i] = a.value() + Math.max(1, s.value());
        }
        return player.posX >= min[0] && player.posX < max[0] && player.posY >= min[1] && player.posY < max[1]
                && player.posZ >= min[2] && player.posZ < max[2];
    }

    public void onInteract(EntityPlayerMP player, boolean entity, String name) {
        TeamProgress team = quests.progressOf(player);
        for (Entry e : new ArrayList<>(entries)) {
            if (!e.task().type().equals("interaction") || !open(team, e)) {
                continue;
            }
            String type = e.task().params().getString("interaction_type", "block");
            String match = e.task().params().getString("to_interact", "");
            boolean wantsEntity = type.equals("entity_type");
            if (wantsEntity != entity || !(wantsEntity || type.equals("block") || type.equals("block_state"))) {
                continue;
            }
            int bracket = match.indexOf('[');
            String wanted = bracket >= 0 ? match.substring(0, bracket) : match;
            if (wanted.equals(name) || !entity && wanted.equals("minecraft:air")) {
                quests.setTaskProgress(player, e.task(), 1);
            }
        }
    }

    private String submitEnergy(EntityPlayerMP player, Task t) {
        TeamProgress team = quests.progressOf(player);
        Quest q = quests.book().questOfTask(t.id());
        if (team.isCompleted(q.id) || QuestRules.taskDone(team, t)) {
            return "Already done.";
        }
        if (!QuestRules.canProgress(quests.book(), team, q) || !QuestRules.dependenciesMet(quests.book(), team, q)) {
            return "That quest is not available yet.";
        }
        long need = t.maxProgress() - team.taskProgress(t.id());
        long taken = 0;
        for (int i = 0; i < player.inventory.mainInventory.size() && taken < need; i++) {
            ItemStack stack = player.inventory.mainInventory.get(i);
            if (stack.getCount() != 1) {
                continue;
            }
            net.minecraftforge.energy.IEnergyStorage storage = stack.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY, null);
            if (storage != null && storage.canExtract()) {
                taken += storage.extractEnergy((int) Math.min(Integer.MAX_VALUE, need - taken), false);
            }
        }
        if (taken == 0) {
            return "You do not carry an item with stored energy.";
        }
        quests.addTaskProgress(player, t, taken);
        player.inventoryContainer.detectAndSendChanges();
        return "Handed in " + taken + " FE.";
    }

    public void onKill(EntityPlayerMP killer, EntityLivingBase victim) {
        ResourceLocation key = EntityList.getKey(victim);
        if (key == null) {
            return;
        }
        TeamProgress team = quests.progressOf(killer);
        for (Entry e : new ArrayList<>(entries)) {
            if (!e.task().type().equals("kill") || !open(team, e)) {
                continue;
            }
            DataNode.Compound tp = e.task().params();
            String entityId = tp.getString("entity", "");

            if (entityId.isEmpty() ? true : !quests.idMap().entity(entityId).equals(key.toString())) {
                continue;
            }
            String name = tp.getString("custom_name", "");
            if (!name.isEmpty() && !name.equals(victim.getCustomNameTag())) {
                continue;
            }
            String filter = tp.getString("nbt_filter", "");
            if (!filter.isEmpty()) {
                try {
                    net.minecraft.nbt.NBTTagCompound wantedNbt = net.minecraft.nbt.JsonToNBT.getTagFromJson(filter);
                    if (!net.minecraft.nbt.NBTUtil.areNBTEquals(wantedNbt, victim.writeToNBT(new net.minecraft.nbt.NBTTagCompound()), true)) {
                        continue;
                    }
                } catch (net.minecraft.nbt.NBTException ex) {
                    continue;
                }
            }
            quests.addTaskProgress(killer, e.task(), 1);
        }
    }

    public void onCraft(EntityPlayerMP player, ItemStack crafted) {
        TeamProgress team = quests.progressOf(player);
        for (Entry e : new ArrayList<>(entries)) {
            Task t = e.task();
            if (!t.type().equals("item") || !t.params().getBool("only_from_crafting", false) || consumes(t) || !open(team, e)) {
                continue;
            }
            List<ItemResolver.Resolved> set = ItemSet.resolveAll(t.params().get("item"), quests.idMap());
            if (!set.isEmpty() && ItemSet.matches(set, crafted, exactNbt(t))) {
                quests.addTaskProgress(player, t, crafted.getCount());
            }
        }
    }

    public void onAdvancement(EntityPlayerMP player, ResourceLocation advancement) {
        TeamProgress team = quests.progressOf(player);
        for (Entry e : new ArrayList<>(entries)) {
            if (e.task().type().equals("advancement") && open(team, e)
                    && advancement.toString().equals(e.task().params().getString("advancement", ""))) {
                quests.setTaskProgress(player, e.task(), 1);
            }
        }
    }

    public String check(EntityPlayerMP player, String taskId) {
        Task t = quests.book().task(taskId);
        if (t == null || !t.type().equals("checkmark")) {
            return "No checkmark task '" + taskId + "'.";
        }
        TeamProgress team = quests.progressOf(player);
        Quest q = quests.book().questOfTask(taskId);
        if (team.isCompleted(q.id) || QuestRules.taskDone(team, t)) {
            return "Already done.";
        }
        if (!QuestRules.canProgress(quests.book(), team, q) || !QuestRules.dependenciesMet(quests.book(), team, q)) {
            return "That quest is not available yet.";
        }
        quests.setTaskProgress(player, t, 1);
        return QuestRules.taskDone(quests.progressOf(player), t) ? "Checked." : "You cannot do that task yet.";
    }

    private String submitExperience(EntityPlayerMP player, Task t) {
        TeamProgress team = quests.progressOf(player);
        Quest q = quests.book().questOfTask(t.id());
        if (team.isCompleted(q.id) || QuestRules.taskDone(team, t)) {
            return "Already done.";
        }
        if (!QuestRules.canProgress(quests.book(), team, q) || !QuestRules.dependenciesMet(quests.book(), team, q)) {
            return "That quest is not available yet.";
        }
        boolean points = t.params().getBool("points", false);
        long need = t.maxProgress() - team.taskProgress(t.id());
        long have = points ? player.experienceTotal : player.experienceLevel;
        long take = Math.min(need, have);
        if (take <= 0) {
            return "You do not have enough experience.";
        }
        if (points) {
            player.addExperience((int) -take);
        } else {
            player.addExperienceLevel((int) -take);
        }
        quests.addTaskProgress(player, t, take);
        return "Handed in " + take + (points ? " XP point(s)." : " XP level(s).");
    }

    public String submit(EntityPlayerMP player, String taskId) {
        Task t = quests.book().task(taskId);
        if (t != null && t.type().equals("xp")) {
            return submitExperience(player, t);
        }
        if (t != null && t.type().equals("fluid")) {
            return submitFluid(player, t);
        }
        if (t != null && t.type().equals("forge_energy")) {
            return submitEnergy(player, t);
        }
        if (t == null || !t.type().equals("item")) {
            return "No item task '" + taskId + "'.";
        }
        List<ItemResolver.Resolved> set = ItemSet.resolveAll(t.params().get("item"), quests.idMap());
        if (set.isEmpty()) {
            return "This task's item is not known in 1.12.2.";
        }
        TeamProgress team = quests.progressOf(player);
        Quest q = quests.book().questOfTask(taskId);
        if (team.isCompleted(q.id) || QuestRules.taskDone(team, t)) {
            return "Already done.";
        }
        if (!QuestRules.canProgress(quests.book(), team, q) || !QuestRules.dependenciesMet(quests.book(), team, q)) {
            return "That quest is not available yet.";
        }
        long need = t.maxProgress() - team.taskProgress(taskId);
        long taken = 0;
        for (int i = 0; i < player.inventory.mainInventory.size() && taken < need; i++) {
            ItemStack s = player.inventory.mainInventory.get(i);
            if (ItemSet.matches(set, s, exactNbt(t))) {
                int take = (int) Math.min(s.getCount(), need - taken);
                s.shrink(take);
                taken += take;
            }
        }
        if (taken == 0) {
            return "You do not have the required items.";
        }
        quests.addTaskProgress(player, t, taken);
        return "Handed in " + taken + " item(s).";
    }
}
