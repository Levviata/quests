/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.assets;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.levviata.reflib.impl.assets.ZipAssetIndex;

public final class SuppliedAssets {

    public static final String FOLDER = "reflib-supplied";

    private static final Logger LOGGER = LogManager.getLogger("reflib/assets");

    public record Requirement(String projectId, String displayName, String whereToGet, List<String> domains,
                              @Nullable File jar, boolean optional) {
        public boolean isPresent() {
            return jar != null;
        }
    }

    private static final List<ZipAssetIndex> SOURCES = new ArrayList<>();
    private static final Map<String, Requirement> REQUIREMENTS = new ConcurrentHashMap<>();
    private static final Map<String, String> ALIASES = new ConcurrentHashMap<>();
    private static File folder = new File(FOLDER);

    private SuppliedAssets() {}

    public static synchronized void init(File gameDir) {
        closeAll();
        folder = new File(gameDir, FOLDER);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            LOGGER.warn("Could not create {}", folder);
        }
        File[] files = folder.listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (!f.isFile() || !(n.endsWith(".jar") || n.endsWith(".zip"))) {
                    continue;
                }
                try {
                    ZipAssetIndex idx = new ZipAssetIndex(f, null);
                    if (idx.domains().isEmpty()) {
                        idx.close();
                        continue;
                    }
                    SOURCES.add(idx);
                    LOGGER.info("Supplied assets: {} provides {}", f.getName(), idx.domains());
                } catch (IOException ex) {
                    LOGGER.warn("Cannot read supplied archive {}: {}", f, ex.toString());
                }
            }
        }

        for (Requirement r : new ArrayList<>(REQUIREMENTS.values())) {
            REQUIREMENTS.put(r.projectId(), resolve(r.projectId(), r.displayName(), r.whereToGet(), r.domains(), r.optional()));
        }
    }

    private static void closeAll() {
        for (ZipAssetIndex idx : SOURCES) {
            try {
                idx.close();
            } catch (IOException ignored) {

            }
        }
        SOURCES.clear();
    }

    public static synchronized File folder() {
        return folder;
    }

    public static synchronized Requirement declare(String projectId, String displayName, String whereToGet,
                                                   String... domains) {
        if (domains.length == 0) {
            throw new IllegalArgumentException("declare at least one asset domain");
        }
        return declare(projectId, displayName, whereToGet, false, domains);
    }

    public static synchronized Requirement declareOptional(String projectId, String displayName, String whereToGet,
                                                           String... domains) {
        if (domains.length == 0) {
            throw new IllegalArgumentException("declare at least one asset domain");
        }
        return declare(projectId, displayName, whereToGet, true, domains);
    }

    private static Requirement declare(String projectId, String displayName, String whereToGet, boolean optional,
                                       String[] domains) {
        Requirement r = resolve(projectId, displayName, whereToGet, Arrays.asList(domains), optional);
        REQUIREMENTS.put(projectId, r);
        if (r.isPresent()) {
            LOGGER.info("[{}] using supplied assets from {}", projectId, r.jar().getName());
        } else {
            LOGGER.warn("[{}] supplied assets for {} not found in {}; using fallback assets. Get it: {}",
                    projectId, displayName, folder.getAbsolutePath(), whereToGet);
        }
        return r;
    }

    private static Requirement resolve(String projectId, String displayName, String whereToGet, List<String> domains,
                                       boolean optional) {
        Set<String> want = new HashSet<>();
        for (String d : domains) {
            want.add(d.toLowerCase(Locale.ROOT));
        }
        File found = null;
        for (ZipAssetIndex idx : SOURCES) {
            if (idx.domains().containsAll(want)) {
                found = idx.file();
            }
        }
        return new Requirement(projectId, displayName, whereToGet, List.copyOf(domains), found, optional);
    }

    public static boolean isPresent(String projectId) {
        Requirement r = REQUIREMENTS.get(projectId);
        return r != null && r.isPresent();
    }

    public static List<Requirement> missing() {
        return missing(false);
    }

    public static List<Requirement> missingOptional() {
        return missing(true);
    }

    private static List<Requirement> missing(boolean optional) {
        List<Requirement> out = new ArrayList<>();
        for (Requirement r : REQUIREMENTS.values()) {
            if (!r.isPresent() && r.optional() == optional) {
                out.add(r);
            }
        }
        out.sort(java.util.Comparator.comparing(Requirement::projectId));
        return out;
    }

    public static void alias(String domain, String path, String originalPath) {
        ALIASES.put(domain.toLowerCase(Locale.ROOT) + ":" + path.toLowerCase(Locale.ROOT), originalPath);
    }

    public static String resolveAlias(String domain, String path) {
        String a = ALIASES.get(domain.toLowerCase(Locale.ROOT) + ":" + path.toLowerCase(Locale.ROOT));
        return a == null ? path : a;
    }

    @Nullable
    public static synchronized byte[] read(String domain, String path) {
        String real = resolveAlias(domain, path);
        for (ZipAssetIndex idx : SOURCES) {
            try {
                byte[] data = idx.read(domain, real);
                if (data != null) {
                    return data;
                }
            } catch (IOException ex) {
                LOGGER.warn("Cannot read {}:{} from {}: {}", domain, real, idx.file().getName(), ex.toString());
            }
        }
        return null;
    }

    public static synchronized List<ZipAssetIndex> sources() {
        return Collections.unmodifiableList(new ArrayList<>(SOURCES));
    }

    static synchronized void resetForTests() {
        closeAll();
        REQUIREMENTS.clear();
        ALIASES.clear();
    }
}
