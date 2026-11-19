/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api;

/**
 * Constants describing the public API. Everything under {@code com.levviata.reflib.api} is covered by
 * semantic versioning; {@code com.levviata.reflib.impl} is not and must not be imported by other mods.
 */
public final class ReflibApi {

    public static final String MOD_ID = "reflib";

    /**
     * Integer API level. Increases whenever the public API gains features; a project declares the minimum
     * level it needs through {@link com.levviata.reflib.api.project.ReflibProjects#register}.
     */
    public static final int API_LEVEL = 1;

    private ReflibApi() {}
}
