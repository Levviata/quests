/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.data;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Step-wise upgrader for versioned data files. The version lives in the compound under
 * {@link #VERSION_KEY}; a file without it counts as version 0. Each step upgrades from version
 * {@code n} to {@code n + 1}.
 */
public final class DataUpgrader {

    public static final String VERSION_KEY = "dataVersion";

    private final int currentVersion;
    private final Map<Integer, UnaryOperator<DataNode.Compound>> steps = new HashMap<>();

    public DataUpgrader(int currentVersion) {
        if (currentVersion < 0) {
            throw new IllegalArgumentException("currentVersion must be >= 0");
        }
        this.currentVersion = currentVersion;
    }

    /** Register the step that turns data of {@code fromVersion} into {@code fromVersion + 1}. */
    public DataUpgrader step(int fromVersion, UnaryOperator<DataNode.Compound> upgrade) {
        if (fromVersion < 0 || fromVersion >= currentVersion) {
            throw new IllegalArgumentException("step must start in [0, " + (currentVersion - 1) + "], got " + fromVersion);
        }
        if (steps.putIfAbsent(fromVersion, upgrade) != null) {
            throw new IllegalStateException("Step from version " + fromVersion + " already registered");
        }
        return this;
    }

    public int currentVersion() {
        return currentVersion;
    }

    /**
     * @return the data upgraded to the current version, with {@link #VERSION_KEY} set
     * @throws IllegalStateException if the data is newer than this build, or a step is missing
     */
    public DataNode.Compound upgrade(DataNode.Compound data) {
        int version = data.getInt(VERSION_KEY, 0);
        if (version > currentVersion) {
            throw new IllegalStateException("Data version " + version + " is newer than the supported version "
                    + currentVersion + "; refusing to load (would lose data)");
        }
        DataNode.Compound result = data;
        for (int v = version; v < currentVersion; v++) {
            UnaryOperator<DataNode.Compound> step = steps.get(v);
            if (step == null) {
                throw new IllegalStateException("No upgrade step registered from data version " + v);
            }
            result = step.apply(result);
        }
        return result.with(VERSION_KEY, currentVersion);
    }
}
