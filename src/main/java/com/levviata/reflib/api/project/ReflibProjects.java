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

public final class ReflibProjects {

    public record Project(String modId, String version, int requiredApiLevel) {}

    private static final Map<String, Project> PROJECTS = new LinkedHashMap<>();

    private ReflibProjects() {}

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

    public static synchronized List<Project> all() {
        return new ArrayList<>(PROJECTS.values());
    }

    static synchronized void clearForTests() {
        PROJECTS.clear();
    }
}
