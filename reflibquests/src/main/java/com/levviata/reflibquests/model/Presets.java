/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Quest;

/**
 * Visual presets of the book ({@code presets} in data.json5: name -> {shape, size}). Measured in FTB Quests 26.1.2: a
 * quest that names a preset is drawn with the preset's shape and size, whatever shape and size the quest itself has
 * (a quest with shape Circle and size 3 and preset goal was drawn as the preset's hexagon at size 2).
 */
public final class Presets {

    private Presets() {}

    private static DataNode.Compound of(QuestBook book, Quest q) {
        if (book == null) {
            return DataNode.Compound.empty();
        }
        String name = q.preset == null ? "" : q.preset;
        if (name.isEmpty()) {
            // a quest without a preset takes its chapter's ("Default Visual Preset")
            Definitions.Chapter c = book.chapterOf(q.id);
            name = c == null || c.preset == null ? "" : c.preset;
        }
        return name.isEmpty() ? DataNode.Compound.empty() : book.settings.getCompound("presets").getCompound(name);
    }

    /** The size the quest is drawn with. */
    public static double size(QuestBook book, Quest q) {
        DataNode.Compound p = of(book, q);
        double s = p.getDouble("size", 0);
        return s > 0 ? s : q.size;
    }

    /** The shape name from the quest's preset, or empty if it has none. */
    public static String shape(QuestBook book, Quest q) {
        return of(book, q).getString("shape", "");
    }
}
