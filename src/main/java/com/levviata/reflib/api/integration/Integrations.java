/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.DefaultArtifactVersion;
import net.minecraftforge.fml.common.versioning.InvalidVersionSpecificationException;
import net.minecraftforge.fml.common.versioning.VersionRange;

public final class Integrations {

    private static final Logger LOGGER = LogManager.getLogger("reflib/integration");

    private record Entry(String modId, String versionRange, Supplier<Runnable> factory) {}

    private static final List<Entry> PENDING = new ArrayList<>();

    private Integrations() {}

    public static synchronized void register(String modId, String versionRange, Supplier<Runnable> factory) {
        PENDING.add(new Entry(modId, versionRange, factory));
    }

    public static int activateAll() {
        return activateAll(Integrations::loadedVersion);
    }

    public static synchronized int activateAll(Function<String, String> versionLookup) {
        int activated = 0;
        for (Entry e : PENDING) {
            String version = versionLookup.apply(e.modId);
            if (version == null) {
                continue;
            }
            if (!versionMatches(e.versionRange, version)) {
                LOGGER.info("Skipping integration with {}: version {} does not match {}", e.modId, version, e.versionRange);
                continue;
            }
            try {
                e.factory.get().run();
                activated++;
                LOGGER.info("Activated integration with {} {}", e.modId, version);
            } catch (Throwable t) {
                LOGGER.error("Integration with {} failed and was disabled", e.modId, t);
            }
        }
        PENDING.clear();
        return activated;
    }

    public static boolean versionMatches(String range, String version) {
        if (range == null || range.equals("*") || range.isEmpty()) {
            return true;
        }
        try {
            return VersionRange.createFromVersionSpec(range).containsVersion(new DefaultArtifactVersion(version));
        } catch (InvalidVersionSpecificationException ex) {
            throw new IllegalArgumentException("Invalid version range '" + range + "'", ex);
        }
    }

    @Nullable
    private static String loadedVersion(String modId) {
        ModContainer container = Loader.instance().getIndexedModList().get(modId);
        return container == null ? null : container.getVersion();
    }
}
