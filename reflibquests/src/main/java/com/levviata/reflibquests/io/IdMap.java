/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import java.util.HashMap;
import java.util.Map;

import com.levviata.reflib.api.data.DataNode;

/**
 * Maps names written by newer game versions to 1.12.2 names. File form ({@code id_map.snbt}):
 *
 * <pre>{@code
 * {
 *   items: { "minecraft:oak_log": "minecraft:log@0", "minecraft:red_wool": "minecraft:wool@14" }
 *   entities: { "minecraft:zombified_piglin": "minecraft:zombie_pigman" }
 * }
 * }</pre>
 *
 * A target may carry {@code @meta} (item damage). Names without an entry are returned unchanged, so ids that
 * did not change between versions need no entry.
 */
public final class IdMap {

    /** A mapped item name and its metadata value (0 if none given). */
    public record ItemTarget(String name, int meta, boolean hasMeta) {}

    private final Map<String, String> items = new HashMap<>();
    private final Map<String, String> entities = new HashMap<>();

    /** Only the entries in {@code data}. */
    public static IdMap from(DataNode.Compound data) {
        IdMap m = new IdMap();
        fill(m.items, data.getCompound("items"));
        fill(m.entities, data.getCompound("entities"));
        return m;
    }

    /** The built-in table ({@link BuiltinIdMap}) with the entries of {@code overrides} on top; overrides win. */
    public static IdMap withBuiltin(DataNode.Compound overrides) {
        IdMap m = new IdMap();
        m.items.putAll(BuiltinIdMap.items());
        m.entities.putAll(BuiltinIdMap.entities());
        fill(m.items, overrides.getCompound("items"));
        fill(m.entities, overrides.getCompound("entities"));
        return m;
    }

    private static void fill(Map<String, String> into, DataNode.Compound c) {
        for (var e : c.entries().entrySet()) {
            if (e.getValue() instanceof DataNode.Str s) {
                into.put(e.getKey(), s.value());
            }
        }
    }

    public int size() {
        return items.size() + entities.size();
    }

    public ItemTarget item(String name) {
        String target = items.getOrDefault(name, name);
        int at = target.lastIndexOf('@');
        if (at > 0) {
            try {
                return new ItemTarget(target.substring(0, at), Integer.parseInt(target.substring(at + 1)), true);
            } catch (NumberFormatException ex) {
                return new ItemTarget(target.substring(0, at), 0, false);
            }
        }
        return new ItemTarget(target, 0, false);
    }

    public String entity(String name) {
        return entities.getOrDefault(name, name);
    }
}
