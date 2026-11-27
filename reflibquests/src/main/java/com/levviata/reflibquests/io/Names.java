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

public final class Names {

    private Names() {}

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

    /**
     * Older versions of this mod stored a modded dimension as {@code modded:name}. That prefix is no longer written, but
     * values that have it are still understood and shown without it.
     */
    public static final String MODDED_PREFIX = "modded:";

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

    /** What a dimension is called on screen: the stored value without the old {@code modded:} prefix. */
    public static String dimensionLabel(String stored) {
        return stored != null && stored.startsWith(MODDED_PREFIX) ? stored.substring(MODDED_PREFIX.length()) : String.valueOf(stored);
    }

    public static java.util.List<String> dimensionNames() {
        java.util.List<String> out = new java.util.ArrayList<>(java.util.List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));
        for (net.minecraft.world.DimensionType type : net.minecraft.world.DimensionType.values()) {
            String name = type.getName();
            if (!name.equals("overworld") && !name.equals("the_nether") && !name.equals("the_end")) {
                out.add(name);
            }
        }
        return out;
    }

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
