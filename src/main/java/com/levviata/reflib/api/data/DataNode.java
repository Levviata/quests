/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small immutable, Minecraft-independent data tree used for authored data files. It is deliberately
 * independent of NBT so the text codec ({@link Snbt}) can be tested without the game; use
 * {@link NbtBridge} to convert to and from NBT.
 */
public sealed interface DataNode {

    record Str(String value) implements DataNode {}

    record Int(int value) implements DataNode {}

    record Lng(long value) implements DataNode {}

    record Dbl(double value) implements DataNode {}

    record Bool(boolean value) implements DataNode {}

    /** Ordered list; elements may have any type in text form (NBT conversion requires one type). */
    record Lst(List<DataNode> items) implements DataNode {
        public Lst {
            items = Collections.unmodifiableList(new ArrayList<>(items));
        }
    }

    /** Insertion-ordered string-keyed map. Immutable: {@code with} returns a copy. */
    record Compound(Map<String, DataNode> entries) implements DataNode {
        public Compound {
            entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }

        public static Compound empty() {
            return new Compound(Map.of());
        }

        public Compound with(String key, DataNode value) {
            Map<String, DataNode> copy = new LinkedHashMap<>(entries);
            copy.put(key, value);
            return new Compound(copy);
        }

        public Compound with(String key, String value) {
            return with(key, new Str(value));
        }

        public Compound with(String key, int value) {
            return with(key, new Int(value));
        }

        public Compound with(String key, long value) {
            return with(key, new Lng(value));
        }

        public Compound with(String key, double value) {
            return with(key, new Dbl(value));
        }

        public Compound with(String key, boolean value) {
            return with(key, new Bool(value));
        }

        public Compound without(String key) {
            Map<String, DataNode> copy = new LinkedHashMap<>(entries);
            copy.remove(key);
            return new Compound(copy);
        }

        public boolean has(String key) {
            return entries.containsKey(key);
        }

        public DataNode get(String key) {
            return entries.get(key);
        }

        public String getString(String key, String fallback) {
            return get(key) instanceof Str s ? s.value() : fallback;
        }

        /** Accepts {@code Int}; also {@code Lng} if it fits. */
        public int getInt(String key, int fallback) {
            DataNode n = get(key);
            if (n instanceof Int i) {
                return i.value();
            }
            if (n instanceof Lng l && l.value() >= Integer.MIN_VALUE && l.value() <= Integer.MAX_VALUE) {
                return (int) l.value();
            }
            return fallback;
        }

        public long getLong(String key, long fallback) {
            DataNode n = get(key);
            if (n instanceof Lng l) {
                return l.value();
            }
            return n instanceof Int i ? i.value() : fallback;
        }

        /** Accepts {@code Dbl}, and integer values widened to double. */
        public double getDouble(String key, double fallback) {
            DataNode n = get(key);
            if (n instanceof Dbl d) {
                return d.value();
            }
            if (n instanceof Int i) {
                return i.value();
            }
            return n instanceof Lng l ? l.value() : fallback;
        }

        /** Accepts {@code Bool}, and the integers 0 and 1 (older files write booleans as bytes). */
        public boolean getBool(String key, boolean fallback) {
            DataNode n = get(key);
            if (n instanceof Bool b) {
                return b.value();
            }
            return n instanceof Int i && (i.value() == 0 || i.value() == 1) ? i.value() == 1 : fallback;
        }

        public List<DataNode> getList(String key) {
            return get(key) instanceof Lst l ? l.items() : List.of();
        }

        public Compound getCompound(String key) {
            return get(key) instanceof Compound c ? c : empty();
        }
    }
}
