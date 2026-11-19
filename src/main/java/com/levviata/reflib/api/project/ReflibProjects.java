/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.levviata.reflib.api.ReflibApi;

/**
 * Registry of the referencing projects (mods built on reflib) that are loaded. Registration makes
 * namespace collisions and API-level mismatches fail at startup with a readable message instead of
 * a {@link NoSuchMethodError} later.
 */
public final class ReflibProjects {

    /** A registered project. */
    public record Project(String modId, String version, int requiredApiLevel) {}

    private static final Map<String, Project> PROJECTS = new LinkedHashMap<>();

    private ReflibProjects() {}

    /**
     * Register a project. Call once, early (mod construction or pre-init).
     *
     * @param modId            the project's own mod id; also used as its namespace for channels and data
     * @param version          the project's version, for diagnostics
     * @param requiredApiLevel the minimum {@link ReflibApi#API_LEVEL} the project needs
     * @throws IllegalStateException if the id is taken or the installed library is too old
     */
    public static synchronized Project register(String modId, String version, int requiredApiLevel) {
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("modId must not be blank");
        }
        if (requiredApiLevel < 1) {
            throw new IllegalArgumentException("requiredApiLevel must be >= 1, got " + requiredApiLevel);
        }
        if (requiredApiLevel > ReflibApi.API_LEVEL) {
            throw new IllegalStateException("Project '" + modId + "' needs reflib API level " + requiredApiLevel
                    + " but the installed reflib provides level " + ReflibApi.API_LEVEL + ". Update reflib.");
        }
        if (PROJECTS.containsKey(modId)) {
            throw new IllegalStateException("Project '" + modId + "' is already registered with reflib");
        }
        Project project = new Project(modId, version, requiredApiLevel);
        PROJECTS.put(modId, project);
        return project;
    }

    /** @return all registered projects in registration order */
    public static synchronized List<Project> all() {
        return new ArrayList<>(PROJECTS.values());
    }

    /** Test hook. */
    static synchronized void clearForTests() {
        PROJECTS.clear();
    }
}
