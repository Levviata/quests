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
                    return OptionalInt.empty();
                }
        }
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
