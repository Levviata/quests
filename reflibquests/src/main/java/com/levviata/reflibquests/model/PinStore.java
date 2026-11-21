/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.levviata.reflib.api.data.DataNode;

/**
 * Which quests each player has pinned for tracking. Per player (not per team), in the order pinned. Pure data
 * and rules; the server saves it and sends each player their own list.
 */
public final class PinStore {

    public static final int MAX_PER_PLAYER = 10;

    public enum Result { PINNED, UNPINNED, FULL }

    private final Map<UUID, LinkedHashSet<String>> pins = new LinkedHashMap<>();
    private boolean dirty;

    public List<String> pinsOf(UUID player) {
        LinkedHashSet<String> set = pins.get(player);
        return set == null ? List.of() : new ArrayList<>(set);
    }

    /** Pin the quest, or unpin it if it already is. A full list refuses a new pin. */
    public Result toggle(UUID player, String questId) {
        LinkedHashSet<String> set = pins.computeIfAbsent(player, p -> new LinkedHashSet<>());
        if (set.remove(questId)) {
            if (set.isEmpty()) {
                pins.remove(player);
            }
            dirty = true;
            return Result.UNPINNED;
        }
        if (set.size() >= MAX_PER_PLAYER) {
            if (set.isEmpty()) {
                pins.remove(player);
            }
            return Result.FULL;
        }
        set.add(questId);
        dirty = true;
        return Result.PINNED;
    }

    /** Drop pins of quests that no longer exist. @return true if anything was removed */
    public boolean prune(QuestBook book) {
        boolean changed = false;
        for (var it = pins.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            changed |= e.getValue().removeIf(id -> book.quest(id) == null);
            if (e.getValue().isEmpty()) {
                it.remove();
            }
        }
        dirty |= changed;
        return changed;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public DataNode.Compound toData() {
        DataNode.Compound byPlayer = DataNode.Compound.empty();
        for (Map.Entry<UUID, LinkedHashSet<String>> e : pins.entrySet()) {
            List<DataNode> list = new ArrayList<>();
            for (String id : e.getValue()) {
                list.add(new DataNode.Str(id));
            }
            byPlayer = byPlayer.with(e.getKey().toString(), new DataNode.Lst(list));
        }
        return DataNode.Compound.empty().with("dataVersion", 1).with("pins", byPlayer);
    }

    /**
     * @throws IllegalArgumentException if the data is from a newer version
     */
    public static PinStore fromData(DataNode.Compound c) {
        if (c.getInt("dataVersion", 1) > 1) {
            throw new IllegalArgumentException("pin data version " + c.getInt("dataVersion", 1) + " is newer than supported 1");
        }
        PinStore s = new PinStore();
        for (Map.Entry<String, DataNode> e : c.getCompound("pins").entries().entrySet()) {
            try {
                UUID player = UUID.fromString(e.getKey());
                if (e.getValue() instanceof DataNode.Lst l) {
                    LinkedHashSet<String> set = new LinkedHashSet<>();
                    for (DataNode n : l.items()) {
                        if (n instanceof DataNode.Str str && set.size() < MAX_PER_PLAYER) {
                            set.add(str.value());
                        }
                    }
                    if (!set.isEmpty()) {
                        s.pins.put(player, set);
                    }
                }
            } catch (IllegalArgumentException ex) {
                // skip a damaged entry
            }
        }
        return s;
    }
}
