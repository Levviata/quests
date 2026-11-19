/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.team;

/**
 * Holder of the active {@link TeamProvider}. Defaults to {@link SoloTeamProvider}. At most one provider may
 * replace the default; a second replacement is refused so two mods cannot silently fight over teams.
 */
public final class Teams {

    private static TeamProvider provider = new SoloTeamProvider();
    private static boolean replaced;

    private Teams() {}

    public static synchronized TeamProvider get() {
        return provider;
    }

    /** @throws IllegalStateException if a provider was already installed */
    public static synchronized void install(TeamProvider replacement) {
        if (replaced) {
            throw new IllegalStateException("A team provider is already installed: " + provider.getClass().getName());
        }
        provider = java.util.Objects.requireNonNull(replacement);
        replaced = true;
    }

    /** Test hook. */
    static synchronized void resetForTests() {
        provider = new SoloTeamProvider();
        replaced = false;
    }
}
