/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.BookEditor;
import com.levviata.reflibquests.model.BookEditor.Result;

/**
 * Turns an edit request from a client (an operation name plus arguments) into a call on {@link BookEditor}.
 * Everything the client sent is untrusted: arguments are read defensively and every operation is validated
 * by the editor. Permission is checked by the caller.
 */
final class QuestEdits {

    private QuestEdits() {}

    /**
     * @return null if the edit was applied, otherwise a short reason for the player
     */
    @Nullable
    static String apply(QuestServer qs, EntityPlayerMP player, String op, DataNode.Compound a) {
        BookEditor ed = new BookEditor(qs.book(), new Random());
        switch (op) {
            case "create_chapter": {
                com.levviata.reflibquests.model.Definitions.Chapter made = ed.createChapter(a.getString("title", ""));
                if (made == null) {
                    return "That chapter name is not valid.";
                }
                if (!a.getString("group", "").isEmpty()) {
                    ed.setChapterGroup(made.id, a.getString("group", "")); // made from the + of a group
                }
                return null;
            }
            case "set_group_chapter":
                return fail(ed.setGroupChapter(a.getString("id", ""), a.getString("chapter", "")));
            case "rename_chapter":
                return fail(ed.renameChapter(a.getString("id", ""), a.getString("title", "")));
            case "set_chapter_prop":
                return fail(ed.setChapterProp(a.getString("id", ""), a.getString("key", ""), a.getString("value", "")));
            case "delete_chapter":
                return fail(ed.deleteChapter(a.getString("id", "")));
            case "create_group":
                return ed.createGroup(a.getString("title", ""), a.getString("id", "")) != null ? null : "That group name is not valid.";
            case "rename_group":
                return fail(ed.renameGroup(a.getString("id", ""), a.getString("title", "")));
            case "delete_group":
                return fail(ed.deleteGroup(a.getString("id", "")));
            case "set_chapter_group":
                return fail(ed.setChapterGroup(a.getString("id", ""), a.getString("group", "")));
            case "move_chapter":
                return fail(ed.moveChapter(a.getString("id", ""), a.getInt("by", 0)));
            case "move_group":
                return fail(ed.moveGroup(a.getString("id", ""), a.getInt("by", 0)));
            case "create_quest":
                return ed.createQuest(a.getString("chapter", ""), a.getDouble("x", Double.NaN), a.getDouble("y", Double.NaN)) != null
                        ? null : "Could not create a quest there.";
            case "move_quest":
                return fail(ed.moveQuest(a.getString("id", ""), a.getDouble("x", Double.NaN), a.getDouble("y", Double.NaN)));
            case "delete_quest":
                return fail(ed.deleteQuest(a.getString("id", "")));
            case "set_bezier":
                return fail(ed.setBezier(a.getString("id", ""), a.getCompound("points")));
            case "move_quests":
                return fail(ed.moveQuests(a));
            case "delete_quests":
                return fail(ed.deleteQuests(a));
            case "set_text":
                return fail(ed.setText(a.getString("id", ""), a.getString("title", ""), a.getString("subtitle", ""), lines(a.getList("desc"))));
            case "set_title":
                return fail(ed.setTitleOnly(a.getString("id", ""), a.getString("text", "")));
            case "set_subtitle":
                return fail(ed.setSubtitleOnly(a.getString("id", ""), a.getString("text", "")));
            case "add_lines":
                return fail(ed.addLines(a.getString("id", ""), lines(a.getList("lines"))));
            case "move_image":
                return fail(ed.moveImage(a.getString("id", ""), a.getDouble("x", 0), a.getDouble("y", 0)));
            case "delete_image":
                return fail(ed.deleteImage(a.getString("id", "")));
            case "set_image_props":
                return fail(ed.setImageProps(a.getString("id", ""), a.getCompound("props")));
            case "create_link":
                return ed.createLink(a.getString("chapter", ""), a.getString("quest", ""), a.getDouble("x", Double.NaN), a.getDouble("y", Double.NaN)) != null
                        ? null : "Could not link that quest there.";
            case "delete_link":
                return fail(ed.deleteLink(a.getString("id", "")));
            case "toggle_dep":
                return fail(ed.toggleDependency(a.getString("id", ""), a.getString("dep", "")));
            case "remove_task":
                return fail(ed.removeTask(a.getString("id", "")));
            case "task_value":
                return fail(ed.setTaskValue(a.getString("id", ""), a.getLong("value", 0)));
            case "remove_reward":
                return fail(ed.removeReward(a.getString("id", "")));
            case "set_task_prop":
                return fail(ed.setTaskProp(a.getString("id", ""), a.getString("key", ""), a.getString("value", "")));
            case "set_reward_prop":
                return fail(ed.setRewardProp(a.getString("id", ""), a.getString("key", ""), a.getString("value", "")));
            case "set_flag":
                return fail(ed.setFlag(a.getString("id", ""), a.getString("flag", ""), a.getBool("value", false)));
            case "set_prop":
                return fail(ed.setProp(a.getString("id", ""), a.getString("key", ""), a.getString("value", "")));
            case "set_shape":
                return fail(ed.setShape(a.getString("id", ""), a.getString("shape", "")));
            case "paste_quests": {
                List<String> ids = lines(a.getList("ids"));
                return ed.pasteQuests(a.getString("chapter", ""), ids, a.getDouble("x", Double.NaN), a.getDouble("y", Double.NaN),
                        a.getBool("deps", true)) != null ? null : "Nothing could be pasted.";
            }
            case "move_task":
                return fail(ed.moveTask(a.getString("id", ""), a.getInt("by", 0)));
            case "move_reward":
                return fail(ed.moveReward(a.getString("id", ""), a.getInt("by", 0)));
            case "set_icon": {
                if (a.getString("mode", "").equals("task")) {
                    return fail(ed.setIconFromTask(a.getString("id", ""), a.getString("task", "")));
                }
                DataNode.Compound icon = DataNode.Compound.empty();
                if (a.getString("mode", "").equals("held")) {
                    DataNode.Compound held = heldItem(player);
                    if (held == null) {
                        return "Hold the item in your main hand first.";
                    }
                    icon = held.getCompound("item");
                }
                return fail(ed.setIcon(a.getString("kind", ""), a.getString("id", ""), icon));
            }
            case "set_book_prop":
                return fail(ed.setBookProp(a.getString("key", ""), a.getString("value", "")));
            case "add_task":
                return addTask(ed, player, a);
            case "add_reward":
                return addReward(ed, player, a);
            case "create_table":
                return ed.createTable(a.getString("title", ""), a.getString("id", "")) != null ? null : "That table name is not valid.";
            case "rename_table":
                return fail(ed.renameTable(a.getString("id", ""), a.getString("title", "")));
            case "delete_table":
                return fail(ed.deleteTable(a.getString("id", "")));
            case "set_table_prop":
                return fail(ed.setTableProp(a.getString("id", ""), a.getString("key", ""), a.getString("value", "")));
            case "add_table_entry":
                return addTableEntry(ed, player, a);
            case "set_entry_weight":
                return fail(ed.setEntryWeight(a.getString("id", ""), a.getString("entry", ""), (float) a.getDouble("weight", 0)));
            case "remove_table_entry":
                return fail(ed.removeTableEntry(a.getString("id", ""), a.getString("entry", "")));
            default:
                return "Unknown edit '" + op + "'.";
        }
    }

    private static String addTask(BookEditor ed, EntityPlayerMP player, DataNode.Compound a) {
        String type = a.getString("type", "");
        DataNode.Compound params = a.getCompound("params");
        if (type.equals("item")) {
            DataNode.Compound chosen = pickedItem(a.getCompound("item"), a.getLong("count", 1));
            DataNode.Compound held = chosen != null ? chosen : heldItem(player);
            if (held == null) {
                return "Hold the item in your main hand first.";
            }
            params = held;
        } else if (type.equals("location")) {
            // a one block box where the player stands, in the player's dimension
            params = DataNode.Compound.empty().with("dimension", String.valueOf(player.dimension))
                    .with("position", new DataNode.Lst(java.util.List.of(new DataNode.Int((int) Math.floor(player.posX)),
                            new DataNode.Int((int) Math.floor(player.posY)), new DataNode.Int((int) Math.floor(player.posZ)))))
                    .with("size", new DataNode.Lst(java.util.List.of(new DataNode.Int(1), new DataNode.Int(1), new DataNode.Int(1))));
        } else {
            params = params.without("item"); // only the held-item path may set an item
        }
        return ed.addTask(a.getString("id", ""), type, params) != null ? null : "Could not add that task.";
    }

    private static String addReward(BookEditor ed, EntityPlayerMP player, DataNode.Compound a) {
        String type = a.getString("type", "");
        DataNode.Compound params = a.getCompound("params");
        if (type.equals("item")) {
            DataNode.Compound chosen = pickedItem(a.getCompound("item"));
            DataNode.Compound held = chosen != null ? chosen : heldItem(player);
            if (held == null) {
                return "Hold the item in your main hand first.";
            }
            params = held;
        } else {
            params = params.without("item");
        }
        return ed.addReward(a.getString("id", ""), type, params) != null ? null : "Could not add that reward.";
    }

    private static String addTableEntry(BookEditor ed, EntityPlayerMP player, DataNode.Compound a) {
        String type = a.getString("type", "");
        DataNode.Compound params = a.getCompound("params");
        if (type.equals("item")) {
            // the editor picked the item; check that it exists in this game and keep only its id, damage and a sane count
            DataNode.Compound item = params.getCompound("item");
            String itemId = item.getString("id", "");
            net.minecraft.util.ResourceLocation rl = itemId.contains(":") ? new net.minecraft.util.ResourceLocation(itemId) : null;
            if (rl == null || !net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.containsKey(rl)) {
                return "That item does not exist in this game.";
            }
            DataNode.Compound clean = DataNode.Compound.empty().with("id", itemId);
            if (item.getInt("damage", 0) > 0) {
                clean = clean.with("damage", Math.min(item.getInt("damage", 0), 32767));
            }
            params = DataNode.Compound.empty().with("item", clean).with("count", Math.max(1, Math.min(64, params.getInt("count", 1))));
        } else {
            params = params.without("item");
        }
        return ed.addTableEntry(a.getString("id", ""), type, params, (float) a.getDouble("weight", 1), a.getString("entry", "")) != null
                ? null : "Could not add that entry.";
    }

    /** An item picked in the item picker: only its registry name and damage are kept, and the item has to exist in this game. Null when none was given. */
    @Nullable
    private static DataNode.Compound pickedItem(DataNode.Compound item) {
        String id = item.getString("id", "");
        if (id.isEmpty()) {
            return null;
        }
        net.minecraft.util.ResourceLocation rl = id.contains(":") ? new net.minecraft.util.ResourceLocation(id) : null;
        if (rl == null || !net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.containsKey(rl)) {
            return null;
        }
        DataNode.Compound clean = DataNode.Compound.empty().with("id", id);
        int damage = item.getInt("damage", 0);
        if (damage > 0 && damage < 32767) {
            clean = clean.with("damage", damage);
        }
        return DataNode.Compound.empty().with("item", clean).with("count", 1);
    }

    /** The picked item with the count the picker asked for (1 to a million). */
    @Nullable
    private static DataNode.Compound pickedItem(DataNode.Compound item, long count) {
        DataNode.Compound base = pickedItem(item);
        return base == null ? null : base.with("count", (int) Math.max(1, Math.min(1_000_000, count)));
    }

    /** The stack in the player's main hand as quest-file parameters: an item with its damage value, and a count. */
    @Nullable
    private static DataNode.Compound heldItem(EntityPlayerMP player) {
        ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty() || held.getItem().getRegistryName() == null) {
            return null;
        }
        DataNode.Compound item = DataNode.Compound.empty().with("id", held.getItem().getRegistryName().toString());
        if (held.getHasSubtypes() || held.getMetadata() != 0) {
            item = item.with("damage", held.getMetadata());
        }
        return DataNode.Compound.empty().with("item", item).with("count", held.getCount());
    }

    private static List<String> lines(List<DataNode> list) {
        List<String> out = new ArrayList<>();
        for (DataNode n : list) {
            if (n instanceof DataNode.Str s) {
                out.add(s.value());
            }
        }
        return out;
    }

    @Nullable
    private static String fail(Result r) {
        switch (r) {
            case OK:
                return null;
            case NOT_FOUND:
                return "That object no longer exists.";
            case CYCLE:
                return "That would make quests depend on each other in a loop.";
            default:
                return "That value is not allowed.";
        }
    }
}
