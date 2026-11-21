/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.team;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holder of the active {@link TeamProvider}. Defaults to {@link SoloTeamProvider}. At most one provider may
 * replace the default; a second replacement is refused so two mods cannot silently fight over teams.
 *
 * <p>Mods that depend on team membership (for example to show a team's progress) register a
 * {@link ChangeListener}; a provider calls {@link #notifyChanged} when a player's team changes.
 */
public final class Teams {

    /** Told when a player moved to another team (joined, left, was removed, or their party was disbanded). */
    @FunctionalInterface
    public interface ChangeListener {
        void teamChanged(UUID player);
    }

    private static TeamProvider provider = new SoloTeamProvider();
    private static boolean replaced;
    private static final List<ChangeListener> LISTENERS = new CopyOnWriteArrayList<>();

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

    public static void addListener(ChangeListener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(ChangeListener listener) {
        LISTENERS.remove(listener);
    }

    /** Called by a team provider after a membership change. Listener failures are not allowed to stop the others. */
    public static void notifyChanged(UUID player) {
        for (ChangeListener l : LISTENERS) {
            try {
                l.teamChanged(player);
            } catch (RuntimeException ex) {
                org.apache.logging.log4j.LogManager.getLogger("reflib/teams").error("Team change listener failed", ex);
            }
        }
    }

    /** Test hook. */
    static synchronized void resetForTests() {
        provider = new SoloTeamProvider();
        replaced = false;
        LISTENERS.clear();
    }
}
