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

    public synchronized List<K> drain(R recipient) {
        LinkedHashSet<K> set = dirty.remove(recipient);
        return set == null ? List.of() : new ArrayList<>(set);
    }

    public synchronized void remove(R recipient) {
        dirty.remove(recipient);
    }
}
