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

/**
 * Lets a project use assets (textures, language files, data) that live in jars the <em>player</em> supplies,
 * without shipping or copying them.
 *
 * <p>The player puts the jars into {@code <game folder>/reflib-supplied/}. Reflib scans that folder once at
 * startup. On the client every exposed asset domain of every jar is served as a resource pack, so ordinary
 * {@code ResourceLocation} lookups just work; language files written as JSON (newer versions) are converted to
 * the {@code .lang} format on the fly. The {@code minecraft} domain is never exposed, nothing is extracted or
 * copied, and nothing in the jars is ever loaded as code.
 *
 * <p>A project declares what it expects so that players are told when a jar is missing:
 *
 * <pre>{@code
 * SuppliedAssets.declare("myproject", "Original Mod 1.2.3", "https://example.org/get-it", "originalmod");
 * if (!SuppliedAssets.isPresent("myproject")) { // fall back to own placeholder art }
 * }</pre>
 *
 * <p>This is a distribution mechanism, not a licence: whether using a given mod's assets this way is allowed
 * is decided by that mod's licence, and is the responsibility of the project that declares the requirement.
 */
public final class SuppliedAssets {

    public static final String FOLDER = "reflib-supplied";

    private static final Logger LOGGER = LogManager.getLogger("reflib/assets");

    /** What a project expects from the player. {@code jar} is null when nothing matching was found. */
    public record Requirement(String projectId, String displayName, String whereToGet, List<String> domains,
                              @Nullable File jar) {
        public boolean isPresent() {
            return jar != null;
        }
    }

    private static final List<ZipAssetIndex> SOURCES = new ArrayList<>();
    private static final Map<String, Requirement> REQUIREMENTS = new ConcurrentHashMap<>();
    private static final Map<String, String> ALIASES = new ConcurrentHashMap<>();
    private static File folder = new File(FOLDER);

    private SuppliedAssets() {}

    /**
     * Scan the supplied-assets folder under {@code gameDir}. Called by reflib itself at startup; calling it again
     * re-scans. Unreadable archives are logged and skipped.
     */
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
        // re-resolve requirements declared before a re-scan
        for (Requirement r : new ArrayList<>(REQUIREMENTS.values())) {
            REQUIREMENTS.put(r.projectId(), resolve(r.projectId(), r.displayName(), r.whereToGet(), r.domains()));
        }
    }

    private static void closeAll() {
        for (ZipAssetIndex idx : SOURCES) {
            try {
                idx.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
        SOURCES.clear();
    }

    /** @return the folder players should put jars into */
    public static synchronized File folder() {
        return folder;
    }

    /**
     * Declare that a project needs assets from the given domains.
     *
     * @param displayName  shown to the player when the jar is missing, e.g. "Some Mod 1.2.3"
     * @param whereToGet   text or address telling the player where to obtain it
     * @param domains      asset domains the project reads; a jar must provide all of them
     */
    public static synchronized Requirement declare(String projectId, String displayName, String whereToGet,
                                                   String... domains) {
        if (domains.length == 0) {
            throw new IllegalArgumentException("declare at least one asset domain");
        }
        Requirement r = resolve(projectId, displayName, whereToGet, Arrays.asList(domains));
        REQUIREMENTS.put(projectId, r);
        if (r.isPresent()) {
            LOGGER.info("[{}] using supplied assets from {}", projectId, r.jar().getName());
        } else {
            LOGGER.warn("[{}] supplied assets for {} not found in {}; using fallback assets. Get it: {}",
                    projectId, displayName, folder.getAbsolutePath(), whereToGet);
        }
        return r;
    }

    private static Requirement resolve(String projectId, String displayName, String whereToGet, List<String> domains) {
        Set<String> want = new HashSet<>();
        for (String d : domains) {
            want.add(d.toLowerCase(Locale.ROOT));
        }
        File found = null;
        for (ZipAssetIndex idx : SOURCES) {
            if (idx.domains().containsAll(want)) {
                found = idx.file(); // later files (by name) win, so a newer version name overrides an older one
            }
        }
        return new Requirement(projectId, displayName, whereToGet, List.copyOf(domains), found);
    }

    public static boolean isPresent(String projectId) {
        Requirement r = REQUIREMENTS.get(projectId);
        return r != null && r.isPresent();
    }

    /** @return the requirements whose jar was not found */
    public static List<Requirement> missing() {
        List<Requirement> out = new ArrayList<>();
        for (Requirement r : REQUIREMENTS.values()) {
            if (!r.isPresent()) {
                out.add(r);
            }
        }
        out.sort(java.util.Comparator.comparing(Requirement::projectId));
        return out;
    }

    /**
     * Serve {@code domain:path} from another file of the supplied jars, for example when the new name differs
     * from the old one. May be called at any time before the resources load.
     */
    public static void alias(String domain, String path, String originalPath) {
        ALIASES.put(domain.toLowerCase(Locale.ROOT) + ":" + path.toLowerCase(Locale.ROOT), originalPath);
    }

    /** @return the aliased path for a request, or the path itself */
    public static String resolveAlias(String domain, String path) {
        String a = ALIASES.get(domain.toLowerCase(Locale.ROOT) + ":" + path.toLowerCase(Locale.ROOT));
        return a == null ? path : a;
    }

    /**
     * Read a supplied file (any side, any time after startup).
     *
     * @return the bytes, or null if no supplied jar has it
     */
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

    /** @return the open archives; used by the client resource pack */
    public static synchronized List<ZipAssetIndex> sources() {
        return Collections.unmodifiableList(new ArrayList<>(SOURCES));
    }

    /** Test hook. */
    static synchronized void resetForTests() {
        closeAll();
        REQUIREMENTS.clear();
        ALIASES.clear();
    }
}
