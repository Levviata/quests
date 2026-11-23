/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.data;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

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
