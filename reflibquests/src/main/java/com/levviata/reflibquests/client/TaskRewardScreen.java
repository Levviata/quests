/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.Task;

/** "Edit Task" and "Edit Reward": the settings of one task or reward. Accept sends only what was changed. */
@SideOnly(Side.CLIENT)
final class TaskRewardScreen extends SettingsScreen {

    private static final List<String> CONSUME_LABELS = List.of("Default", "True", "False");
    private static final List<String> AUTO_LABELS = List.of("Default", "Disabled", "Enabled", "Enabled, No Toast", "Enabled, Invisible");
    private static final List<String> AUTO_VALUES = List.of("default", "disabled", "enabled", "no_toast", "invisible");

    private final Task task;
    private final Reward reward;
    private String title;
    private String amount = "";
    private String optional = "False";
    private String consume = "Default";
    private String auto = "Default";
    /** More options, by the key the editor knows them by: the value shown, and the value when the window opened. */
    private final java.util.Map<String, String> flags = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, String> initialFlags = new java.util.HashMap<>();
    private String team = "Default";
    private String exclude = "False";
    private String ignoreBlocking = "False";
    private String initialTeam = "Default";
    private String initialExclude = "False";
    private String initialIgnore = "False";
    private final String initialTitle;
    private final String initialAmount;
    private final String initialOptional;
    private final String initialConsume;
    private final String initialAuto;
    private final java.util.Map<String, String> extra = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, String> initialExtra = new java.util.HashMap<>();
    private String box = "";
    private String initialBox = "";
    private String target = "";
    private String initialTarget = "";

    TaskRewardScreen(GuiScreen parent, @Nullable Task task, @Nullable Reward reward) {
        super(parent, task != null ? "Edit Task" : "Edit Reward");
        this.task = task;
        this.reward = reward;
        DataNode.Compound p = task != null ? task.params() : reward.params();
        title = p.getString("title", "");
        if (task != null) {
            if (task.type().equals("item") || List.of("xp", "kill", "stat", "energy").contains(task.type())) {
                amount = String.valueOf(task.maxProgress());
            }
            optional = task.optionalTask() ? "True" : "False";
            consume = !p.has("consume_items") ? "Default" : p.getBool("consume_items", false) ? "True" : "False";
        } else {
            String key = reward.type().equals("item") ? "count" : reward.type();
            if (reward.type().equals("item") || reward.type().equals("xp") || reward.type().equals("xp_levels")) {
                amount = String.valueOf(Math.max(1, p.getInt(key, 1)));
            }
            int at = AUTO_VALUES.indexOf(p.getString("auto", "default"));
            auto = AUTO_LABELS.get(Math.max(0, at));
            if (reward != null) {
                team = !p.has("team_reward") ? "Default" : p.getBool("team_reward", false) ? "True" : "False";
                exclude = p.getBool("exclude_from_claim_all", false) ? "True" : "False";
                ignoreBlocking = p.getBool("ignore_reward_blocking", false) ? "True" : "False";
                initialTeam = team;
                initialExclude = exclude;
                initialIgnore = ignoreBlocking;
            }
        }
        initialTitle = title;
        initialAmount = amount;
        initialOptional = optional;
        initialConsume = consume;
        initialAuto = auto;

        section(task != null ? "Task" : "Reward");
        text("Title", () -> title, v -> title = v).tip("A name shown instead of the one made from what it is. Empty keeps the automatic name.");
        if (!amount.isEmpty()) {
            number(task != null && task.type().equals("item") ? "Count" : "Amount", true, () -> amount,
                    v -> amount = String.valueOf(Math.max(1L, Long.parseLong(v)))).tip("How many are needed, or how many are given.");
        }
        String tk = task == null ? null : com.levviata.reflibquests.model.BookEditor.targetKey(task.type());
        if (tk != null) {
            target = p.getString(tk, "");
            initialTarget = target;
            text(task.type().equals("kill") ? "Entity" : task.type().equals("dimension") ? "Dimension" : task.type().equals("biome") ? "Biome"
                    : task.type().equals("structure") ? "Structure" : "Advancement", () -> target, v -> target = v)
                    .tip("A registry name such as minecraft:plains.");
        }
        if (task != null && task.type().equals("location")) {
            List<com.levviata.reflib.api.data.DataNode> pos = p.getList("position");
            List<com.levviata.reflib.api.data.DataNode> size = p.getList("size");
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                com.levviata.reflib.api.data.DataNode n = (i < 3 ? pos : size).size() == 3 ? (i < 3 ? pos : size).get(i % 3) : null;
                b.append(i > 0 ? ", " : "").append(n instanceof DataNode.Int v ? v.value() : 0);
            }
            box = b.toString();
            initialBox = box;
            text("Position and Size", () -> box, v -> box = v).tip("x, y, z, then width, height, depth in blocks.");
        }
        if (task != null) {
            choice("Optional Task", List.of("False", "True"), () -> optional, v -> optional = v)
                    .tip("The quest can be completed without this task.");
            flag("Disable Completion Toast", "disable_toast", List.of("False", "True"), yesNo(p, "disable_toast"),
                    "No pop-up when this task is completed.");
            flag("Only From Task Screen", "task_screen_only", List.of("False", "True"), yesNo(p, "task_screen_only"),
                    "Progress only counts when done through the task screen.");
            if (task.type().equals("item")) {
                String crafting = !p.has("only_from_crafting") ? "Default" : p.getBool("only_from_crafting", false) ? "True" : "False";
                flag("Only Detect From Crafting", "only_from_crafting", CONSUME_LABELS, crafting, "Only items made by crafting count.");
                String match = p.getString("match_components", "default");
                flag("Match Data Components", "match_components", List.of("Default", "Fuzzy", "Exact"),
                        Character.toUpperCase(match.charAt(0)) + match.substring(1), "Default and Fuzzy ignore the item's data; Exact needs it to match.");
                choice("Consume Items", CONSUME_LABELS, () -> consume, v -> consume = v)
                        .tip("Default follows the book's setting. True takes the items when they are handed in.");
            }
        } else {
            String rt = reward.type();
            if (rt.equals("command")) {
                extra.put("command", p.getString("command", ""));
                extra.put("permission_level", String.valueOf(Math.max(0, Math.min(4, p.getInt("permission_level", 0)))));
                extra.put("silent", p.getBool("silent", false) ? "True" : "False");
                text("Command", () -> extra.get("command"), v -> extra.put("command", v)).tip("The command run for the player. @p is the player.");
                choice("Run with Permission Level", List.of("0", "1", "2", "3", "4"), () -> extra.get("permission_level"), v -> extra.put("permission_level", v))
                        .tip("0 default, 1, 2 Game Master, 3 Server Admin, 4 Server Owner.");
                choice("Silent", List.of("False", "True"), () -> extra.get("silent"), v -> extra.put("silent", v));
            } else if (rt.equals("toast")) {
                extra.put("description", p.getString("description", ""));
                text("Description", () -> extra.get("description"), v -> extra.put("description", v));
            } else if (rt.equals("advancement")) {
                extra.put("advancement", p.getString("advancement", ""));
                extra.put("criterion", p.getString("criterion", ""));
                text("Advancement", () -> extra.get("advancement"), v -> extra.put("advancement", v)).tip("A registry name such as minecraft:story/mine_stone.");
                text("Criterion", () -> extra.get("criterion"), v -> extra.put("criterion", v));
            }
            initialExtra.putAll(extra);
            choice("Auto-claim", AUTO_LABELS, () -> auto, v -> auto = v)
                    .tip("Whether the reward is given by itself once the quest is complete.");
            choice("Per-Team Reward", CONSUME_LABELS, () -> team, v -> team = v)
                    .tip("Given once to the whole team instead of to every member. Default follows the book's setting.");
            choice("Exclude from Claim All", List.of("False", "True"), () -> exclude, v -> exclude = v);
            choice("Ignore reward blocking", List.of("False", "True"), () -> ignoreBlocking, v -> ignoreBlocking = v);
            flag("Disable Reward Screen Blur", "disable_reward_screen_blur", List.of("False", "True"), yesNo(p, "disable_reward_screen_blur"),
                    "The reward screen does not blur what is behind it.");
            flag("Only One", "only_one", List.of("False", "True"), yesNo(p, "only_one"), "Only one reward of its quest can be claimed.");
        }
    }

    private void flag(String label, String key, List<String> options, String current, String tip) {
        flags.put(key, current);
        initialFlags.put(key, current);
        choice(label, options, () -> flags.get(key), v -> flags.put(key, v)).tip(tip);
    }

    private static String yesNo(com.levviata.reflib.api.data.DataNode.Compound p, String key) {
        return p.getBool(key, false) ? "True" : "False";
    }

    private void change(String op, String id, String key, String value) {
        TableEdits.send(op, DataNode.Compound.empty().with("id", id).with("key", key).with("value", value));
    }

    @Override
    protected void accepted() {
        sendFlags();
        if (task != null) {
            if (!title.equals(initialTitle)) {
                change("set_task_prop", task.id(), "title", title.strip());
            }
            if (!amount.isEmpty() && !amount.equals(initialAmount)) {
                TableEdits.send("task_value", DataNode.Compound.empty().with("id", task.id()).with("value", Long.parseLong(amount)));
            }
            if (!box.equals(initialBox)) {
                change("set_task_prop", task.id(), "box", box.strip());
            }
            if (!target.equals(initialTarget)) {
                change("set_task_prop", task.id(), "target", target.strip());
            }
            if (!optional.equals(initialOptional)) {
                change("set_task_prop", task.id(), "optional_task", optional.equals("True") ? "true" : "false");
            }
            if (!consume.equals(initialConsume)) {
                change("set_task_prop", task.id(), "consume_items", consume.toLowerCase(java.util.Locale.ROOT));
            }
        } else {
            if (!title.equals(initialTitle)) {
                change("set_reward_prop", reward.id(), "title", title.strip());
            }
            if (!amount.isEmpty() && !amount.equals(initialAmount)) {
                String key = reward.type().equals("item") ? "count" : reward.type();
                change("set_reward_prop", reward.id(), key, amount);
            }
            for (java.util.Map.Entry<String, String> e : extra.entrySet()) {
                if (!e.getValue().equals(initialExtra.get(e.getKey()))) {
                    String v = e.getKey().equals("silent") ? e.getValue().toLowerCase(java.util.Locale.ROOT) : e.getValue().strip();
                    change("set_reward_prop", reward.id(), e.getKey(), v);
                }
            }
            if (!team.equals(initialTeam)) {
                change("set_reward_prop", reward.id(), "team_reward", team.toLowerCase(java.util.Locale.ROOT));
            }
            if (!exclude.equals(initialExclude)) {
                change("set_reward_prop", reward.id(), "exclude_from_claim_all", exclude.toLowerCase(java.util.Locale.ROOT));
            }
            if (!ignoreBlocking.equals(initialIgnore)) {
                change("set_reward_prop", reward.id(), "ignore_reward_blocking", ignoreBlocking.toLowerCase(java.util.Locale.ROOT));
            }
            if (!auto.equals(initialAuto)) {
                change("set_reward_prop", reward.id(), "auto", AUTO_VALUES.get(AUTO_LABELS.indexOf(auto)));
            }
        }
    }

    private void sendFlags() {
        for (java.util.Map.Entry<String, String> e : flags.entrySet()) {
            if (!e.getValue().equals(initialFlags.get(e.getKey()))) {
                change(task != null ? "set_task_prop" : "set_reward_prop", task != null ? task.id() : reward.id(), e.getKey(),
                        e.getValue().toLowerCase(java.util.Locale.ROOT));
            }
        }
    }

    @Override
    protected void cancelled() {
        // nothing was changed
    }
}
