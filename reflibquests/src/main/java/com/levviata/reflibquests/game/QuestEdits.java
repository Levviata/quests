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

final class QuestEdits {

    private QuestEdits() {}

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
                    ed.setChapterGroup(made.id, a.getString("group", ""));
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
            case "create_quest": {
                com.levviata.reflibquests.model.Definitions.Quest made = ed.createQuest(a.getString("chapter", ""), a.getDouble("x", Double.NaN), a.getDouble("y", Double.NaN));
                if (made == null) {
                    return "Could not create a quest there.";
                }
                String newTitle = a.getString("title", "").strip();
                if (!newTitle.isEmpty() && newTitle.length() <= 200) {
                    made.title = newTitle;
                }
                DataNode.Compound first = a.getCompound("task");
                if (!first.entries().isEmpty()) {

                    String placeholder = made.tasks.get(0).id();
                    String error = addTask(ed, player, first.with("id", made.id));
                    if (error != null) {
                        ed.deleteQuest(made.id);
                        return error;
                    }
                    ed.removeTask(placeholder);

                    String realTask = made.tasks.isEmpty() ? "" : made.tasks.get(0).id();
                    int applied = 0;
                    for (DataNode e : a.getList("edits")) {
                        if (!(e instanceof DataNode.Compound ec) || applied++ >= 40) {
                            continue;
                        }
                        String editOp = ec.getString("op", "");
                        if (!(editOp.equals("set_task_prop") || editOp.equals("task_value") || editOp.equals("set_icon"))) {
                            continue;
                        }
                        DataNode.Compound editArgs = ec.getCompound("args");
                        if (editArgs.getString("id", "").equals("@task")) {
                            editArgs = editArgs.with("id", realTask);
                        }
                        apply(qs, player, editOp, editArgs);
                    }
                }
                return null;
            }
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
            case "create_image":
                return ed.createImage(a.getString("chapter", ""), a.getString("image", ""), a.getDouble("x", Double.NaN),
                        a.getDouble("y", Double.NaN), a.getDouble("width", 1), a.getDouble("height", 1)) != null
                        ? null : "Could not put that picture there.";
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
                if (a.getString("mode", "").equals("item")) {
                    DataNode.Compound chosen = pickedItem(a.getCompound("item"));
                    if (chosen == null) {
                        return "That item does not exist in this game.";
                    }
                    icon = chosen.getCompound("item");
                } else if (a.getString("mode", "").equals("image")) {
                    String image = a.getString("image", "");
                    try {
                        new net.minecraft.util.ResourceLocation(image);
                    } catch (RuntimeException ex) {
                        return "That is not a valid picture name.";
                    }
                    if (image.isEmpty() || image.length() > 200) {
                        return "That is not a valid picture name.";
                    }
                    icon = DataNode.Compound.empty().with("image", image);
                } else if (a.getString("mode", "").equals("held")) {
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

            params = DataNode.Compound.empty().with("dimension", String.valueOf(player.dimension))
                    .with("position", new DataNode.Lst(java.util.List.of(new DataNode.Int((int) Math.floor(player.posX)),
                            new DataNode.Int((int) Math.floor(player.posY)), new DataNode.Int((int) Math.floor(player.posZ)))))
                    .with("size", new DataNode.Lst(java.util.List.of(new DataNode.Int(1), new DataNode.Int(1), new DataNode.Int(1))));
        } else {
            params = params.without("item");
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
            clean = withNbt(clean, item);
            params = DataNode.Compound.empty().with("item", clean).with("count", Math.max(1, params.getInt("count", 1)));
        } else {
            params = params.without("item");
        }
        return ed.addTableEntry(a.getString("id", ""), type, params, (float) a.getDouble("weight", 1), a.getString("entry", "")) != null
                ? null : "Could not add that entry.";
    }

    private static DataNode.Compound withNbt(DataNode.Compound clean, DataNode.Compound item) {
        DataNode.Compound nbt = item.getCompound("nbt");
        if (nbt.entries().isEmpty()) {
            return clean;
        }
        DataNode.Compound checked = BookEditor.parseNbt(com.levviata.reflib.api.data.Snbt.printCompact(nbt));
        return checked == null ? clean : clean.with("nbt", checked);
    }

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
        clean = withNbt(clean, item);
        return DataNode.Compound.empty().with("item", clean).with("count", 1);
    }

    @Nullable
    private static DataNode.Compound pickedItem(DataNode.Compound item, long count) {
        DataNode.Compound base = pickedItem(item);
        return base == null ? null : base.with("count", (int) Math.max(1, Math.min(Integer.MAX_VALUE, count)));
    }

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
        if (held.hasTagCompound() && !held.getTagCompound().isEmpty()) {
            DataNode.Compound nbt = BookEditor.parseNbt(held.getTagCompound().toString());
            if (nbt != null && !nbt.entries().isEmpty()) {
                item = item.with("nbt", nbt);
            }
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
