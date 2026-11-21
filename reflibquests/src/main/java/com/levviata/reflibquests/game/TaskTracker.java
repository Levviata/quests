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

/**
 * Detects task progress. Polled (once per detection delay) for state-based tasks: item, xp, location,
 * dimension. Event-driven for kill and advancement. Consuming item tasks only progress through
 * {@link #submit}. Checkmarks only through {@link #check}.
 */
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

    /** Whether the task may currently make progress for this team. */
    private boolean open(TeamProgress team, Entry e) {
        return !team.isCompleted(e.quest().id)
                && !QuestRules.taskDone(team, e.task())
                && QuestRules.canProgress(quests.book(), team, e.quest());
    }

    // ------------------------------------------------------------------ polling

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
                case "xp":
                    quests.setTaskProgress(player, t, t.params().getBool("points", false) ? player.experienceTotal : player.experienceLevel);
                    break;
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
                    // looking at the block (the timer of the original is not used: seeing it once counts)
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

    /**
     * The name 1.12.2 uses for a structure, for the vanilla ones; empty when the structure does not exist in this game
     * (structures that came with later versions or other mods).
     */
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
        return n != null ? t.params().getBool("consume_items", false)
                : quests.book().settings.getBool("default_consume_items", false);
    }

    private void pollItem(EntityPlayerMP player, Task t, Entry e) {
        if (consumes(t)) {
            return; // consumed only through submit
        }
        List<ItemResolver.Resolved> set = ItemSet.resolveAll(t.params().get("item"), quests.idMap());
        if (set.isEmpty()) {
            return;
        }
        quests.setTaskProgress(player, t, Math.min(t.maxProgress(), countItems(player, set)));
    }

    private static long countItems(EntityPlayerMP player, List<ItemResolver.Resolved> set) {
        long n = 0;
        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            ItemStack s = player.inventory.mainInventory.get(i);
            if (ItemSet.matches(set, s)) {
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

    // ------------------------------------------------------------------ events

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
            String wanted = quests.idMap().entity(e.task().params().getString("entity", ""));
            if (wanted.equals(key.toString())) {
                quests.addTaskProgress(killer, e.task(), 1);
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

    // ------------------------------------------------------------------ player actions

    /** The player confirms a checkmark task. @return a message for the player */
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

    /** Hand in items for a consuming item task. @return a message for the player */
    public String submit(EntityPlayerMP player, String taskId) {
        Task t = quests.book().task(taskId);
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
            if (ItemSet.matches(set, s)) {
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
