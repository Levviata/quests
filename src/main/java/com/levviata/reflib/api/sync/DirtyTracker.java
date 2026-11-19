/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.sync;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Per-recipient set of changed keys, the building block of "snapshot on join, then deltas". A project marks
 * keys dirty when data changes, and once per tick (or when it chooses) drains each recipient's set and sends
 * only those objects. A newly joined recipient gets a full snapshot instead, sent by the project.
 *
 * <p>Thread-safe. Keys keep insertion order and are de-duplicated.
 *
 * @param <R> recipient type (for example a player id)
 * @param <K> key of a changed object (for example a quest id)
 */
public final class DirtyTracker<R, K> {

    private final Map<R, LinkedHashSet<K>> dirty = new HashMap<>();

    public synchronized void mark(R recipient, K key) {
        dirty.computeIfAbsent(recipient, r -> new LinkedHashSet<>()).add(key);
    }

    public synchronized void markForAll(Collection<? extends R> recipients, K key) {
        for (R r : recipients) {
            mark(r, key);
        }
    }

    public synchronized boolean hasDirty(R recipient) {
        LinkedHashSet<K> set = dirty.get(recipient);
        return set != null && !set.isEmpty();
    }

    /** @return the recipient's pending keys in the order they were first marked, and clears them */
    public synchronized List<K> drain(R recipient) {
        LinkedHashSet<K> set = dirty.remove(recipient);
        return set == null ? List.of() : new ArrayList<>(set);
    }

    /** Forget a recipient (on logout); pending deltas are dropped because the next join sends a snapshot. */
    public synchronized void remove(R recipient) {
        dirty.remove(recipient);
    }
}
