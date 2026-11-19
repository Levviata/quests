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

    static String title(Quest q) {
        return q.title.isEmpty() ? q.id : q.title;
    }

    @Nullable
    static ItemStack stackOf(@Nullable DataNode spec) {
        ItemResolver.Resolved r = ItemResolver.resolve(spec, ClientQuests.idMap());
        return r == null ? null : r.toStack(Math.max(1, r.count()));
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
            if (t.type().equals("item")) {
                ItemStack s = stackOf(t.params().get("item"));
                if (s != null) {
                    return s;
                }
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
                String name = s == null ? "an unknown item" : s.getDisplayName();
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
            default:
                return t.type() + " (not supported yet)";
        }
    }

    static String reward(Reward r) {
        DataNode.Compound p = r.params();
        switch (r.type()) {
            case "item": {
                ItemStack s = stackOf(p.get("item"));
                int count = Math.max(1, p.getInt("count", 1));
                return (count > 1 ? count + "x " : "") + (s == null ? "an unknown item" : s.getDisplayName());
            }
            case "xp":
                return p.getInt("xp", 1) + " XP";
            case "xp_levels":
                return p.getInt("xp_levels", 1) + " XP level(s)";
            case "command":
                return "A reward command";
            case "toast":
                return "A message";
            case "advancement":
                return "An advancement";
            case "choice":
                return "Choose one reward";
            case "random":
                return "A random reward";
            case "all_table":
                return "Several rewards";
            default:
                return r.type() + " (not supported yet)";
        }
    }

    static String tableId(Reward r) {
        return Ids.normalize(r.params().getString("table_id", ""));
    }

    private static String shortName(String id) {
        int c = id.indexOf(':');
        String s = c >= 0 ? id.substring(c + 1) : id;
        return s.replace('_', ' ');
    }
}
