/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import java.util.Map;
import java.util.UUID;

import com.levviata.reflib.api.data.DataNode;

public final class ProgressCodec {

    public static final int VERSION = 1;

    private ProgressCodec() {}

    public static DataNode.Compound write(TeamProgress p) {
        return DataNode.Compound.empty()
                .with("dataVersion", VERSION)
                .with("team", p.teamId().toString())
                .with("locked", p.isLocked())
                .with("rewardsBlocked", p.areRewardsBlocked())
                .with("tasks", longs(p.taskProgress))
                .with("started", longs(p.started))
                .with("completed", longs(p.completed))
                .with("repeatReadyAt", longs(p.repeatReadyAt))
                .with("claimed", longs(p.claimed))
                .with("count", ints(p.completionCount));
    }

    public static TeamProgress read(UUID teamId, DataNode.Compound c) {
        int version = c.getInt("dataVersion", 1);
        if (version > VERSION) {
            throw new IllegalArgumentException("progress data version " + version + " is newer than supported " + VERSION);
        }
        TeamProgress p = new TeamProgress(teamId);
        p.setLocked(c.getBool("locked", false));
        p.setRewardsBlocked(c.getBool("rewardsBlocked", false));
        readLongs(c.getCompound("tasks"), p.taskProgress);
        readLongs(c.getCompound("started"), p.started);
        readLongs(c.getCompound("completed"), p.completed);
        readLongs(c.getCompound("repeatReadyAt"), p.repeatReadyAt);
        readLongs(c.getCompound("claimed"), p.claimed);
        for (Map.Entry<String, DataNode> e : c.getCompound("count").entries().entrySet()) {
            if (e.getValue() instanceof DataNode.Int i) {
                p.completionCount.put(e.getKey(), i.value());
            }
        }
        return p;
    }

    private static DataNode.Compound longs(Map<String, Long> map) {
        DataNode.Compound c = DataNode.Compound.empty();
        for (Map.Entry<String, Long> e : map.entrySet()) {
            c = c.with(e.getKey(), e.getValue());
        }
        return c;
    }

    private static DataNode.Compound ints(Map<String, Integer> map) {
        DataNode.Compound c = DataNode.Compound.empty();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            c = c.with(e.getKey(), e.getValue());
        }
        return c;
    }

    private static void readLongs(DataNode.Compound c, Map<String, Long> into) {
        for (String key : c.entries().keySet()) {
            into.put(key, c.getLong(key, 0));
        }
    }
}
