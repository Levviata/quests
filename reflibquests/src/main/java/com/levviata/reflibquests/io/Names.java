/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.Random;
import java.util.List;

import com.levviata.reflibquests.model.Definitions.WeightedReward;

/** Small pure helpers for names and weighted choices. */
public final class Names {

    private Names() {}

    /** Dimension names of newer versions to 1.12.2 numeric ids. Unknown names return empty. */
    public static OptionalInt dimensionId(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "minecraft:overworld":
            case "overworld":
                return OptionalInt.of(0);
            case "minecraft:the_nether":
            case "the_nether":
            case "nether":
                return OptionalInt.of(-1);
            case "minecraft:the_end":
            case "the_end":
            case "end":
                return OptionalInt.of(1);
            default:
                try {
                    return OptionalInt.of(Integer.parseInt(name));
                } catch (NumberFormatException ex) {
                    return registeredDimension(name);
                }
        }
    }

    /** The prefix of a dimension that is not one of the three of the base game. */
    public static final String MODDED_PREFIX = "modded:";

    /** A dimension type of this game by its name ({@code modded:name}, or the bare name). */
    private static OptionalInt registeredDimension(String name) {
        String bare = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        for (net.minecraft.world.DimensionType type : net.minecraft.world.DimensionType.values()) {
            if (type.getName().equalsIgnoreCase(bare)) {
                it.unimi.dsi.fastutil.ints.IntSortedSet ids = net.minecraftforge.common.DimensionManager.getRegisteredDimensions().get(type);
                return ids == null || ids.isEmpty() ? OptionalInt.of(type.getId()) : OptionalInt.of(ids.firstInt());
            }
        }
        return OptionalInt.empty();
    }

    /** Every dimension this game has registered, as the ids a Dimension task stores. */
    public static java.util.List<String> dimensionNames() {
        java.util.List<String> out = new java.util.ArrayList<>(java.util.List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));
        for (net.minecraft.world.DimensionType type : net.minecraft.world.DimensionType.values()) {
            String name = type.getName();
            if (!name.equals("overworld") && !name.equals("the_nether") && !name.equals("the_end")) {
                out.add(MODDED_PREFIX + name);
            }
        }
        return out;
    }

    /**
     * Pick one entry by weight; {@code emptyWeight} is the weight of "nothing". Entries with non-positive
     * weight are never picked.
     *
     * @return the chosen entry, or null for "nothing" (also when there are no entries)
     */
    public static WeightedReward pick(List<WeightedReward> entries, float emptyWeight, Random random) {
        double total = Math.max(0, emptyWeight);
        for (WeightedReward w : entries) {
            total += Math.max(0, w.weight());
        }
        if (total <= 0) {
            return null;
        }
        double roll = random.nextDouble() * total;
        for (WeightedReward w : entries) {
            double weight = Math.max(0, w.weight());
            if (roll < weight) {
                return w;
            }
            roll -= weight;
        }
        return null;
    }
}
