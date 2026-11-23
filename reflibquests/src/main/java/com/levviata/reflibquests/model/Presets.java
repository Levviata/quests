/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Quest;

public final class Presets {

    private Presets() {}

    private static DataNode.Compound of(QuestBook book, Quest q) {
        if (book == null) {
            return DataNode.Compound.empty();
        }
        String name = q.preset == null ? "" : q.preset;
        if (name.isEmpty()) {

            Definitions.Chapter c = book.chapterOf(q.id);
            name = c == null || c.preset == null ? "" : c.preset;
        }
        return name.isEmpty() ? DataNode.Compound.empty() : book.settings.getCompound("presets").getCompound(name);
    }

    public static double size(QuestBook book, Quest q) {
        DataNode.Compound p = of(book, q);
        double s = p.getDouble("size", 0);
        return s > 0 ? s : q.size;
    }

    public static String shape(QuestBook book, Quest q) {
        return of(book, q).getString("shape", "");
    }
}
