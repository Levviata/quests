/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.RewardTable;

/**
 * A full, independent copy of a chapter or a reward table, for the edit history. It copies field by field, so a field
 * added to the model later is kept without touching this class. Values that cannot change (text, numbers, enums, data
 * trees and records of them) are shared; lists, maps and the editable model classes are copied.
 */
public final class DeepCopy {

    private DeepCopy() {}

    public static Chapter chapter(Chapter c) {
        return (Chapter) copyObject(c);
    }

    public static Definitions.ChapterGroup group(Definitions.ChapterGroup g) {
        return (Definitions.ChapterGroup) copyObject(g);
    }

    public static RewardTable table(RewardTable t) {
        return (RewardTable) copyObject(t);
    }

    @SuppressWarnings("unchecked")
    private static Object copyValue(Object v) {
        if (v == null || v instanceof String || v instanceof Number || v instanceof Boolean || v instanceof Character
                || v instanceof Enum<?> || v instanceof DataNode || v.getClass().isRecord()) {
            return v;
        }
        if (v instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object e : list) {
                out.add(copyValue(e));
            }
            return out;
        }
        if (v instanceof Set<?> set) {
            Set<Object> out = new LinkedHashSet<>();
            for (Object e : set) {
                out.add(copyValue(e));
            }
            return out;
        }
        if (v instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(e.getKey(), copyValue(e.getValue()));
            }
            return out;
        }
        return copyObject(v);
    }

    @SuppressWarnings("unchecked")
    private static Object copyObject(Object src) {
        Class<?> type = src.getClass();
        try {
            Field idField = type.getDeclaredField("id");
            idField.setAccessible(true);
            Constructor<?> ctor = type.getDeclaredConstructor(String.class);
            ctor.setAccessible(true);
            Object copy = ctor.newInstance(idField.get(src));
            for (Field f : type.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getName().equals("id")) {
                    continue;
                }
                f.setAccessible(true);
                Object value = f.get(src);
                if (Modifier.isFinal(f.getModifiers())) {
                    if (value instanceof List<?> from) {
                        List<Object> into = (List<Object>) f.get(copy);
                        into.clear();
                        for (Object e : from) {
                            into.add(copyValue(e));
                        }
                    }
                    continue;
                }
                f.set(copy, copyValue(value));
            }
            return copy;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot copy " + type.getName() + ": " + ex, ex);
        }
    }
}
