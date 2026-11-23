/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.data;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonParser;

class Json5Test {

    private static DataNode.Compound sample() {
        return DataNode.Compound.empty()
                .with("title", "A \"quoted\" line\nwith \\ and\ttab\u0001")
                .with("n", 5).with("big", 3_000_000_000L).with("d", 0.25).with("t", true)
                .with("list", new DataNode.Lst(List.of(new DataNode.Int(1), new DataNode.Int(2))))
                .with("objs", new DataNode.Lst(List.of(DataNode.Compound.empty().with("a", 1), DataNode.Compound.empty())))
                .with("empty", DataNode.Compound.empty())
                .with("emptyList", new DataNode.Lst(List.of()))
                .with("weird key!", "v");
    }

    @Test
    void outputIsStrictJsonWithCommas() {

        String text = Json5.print(sample());
        assertDoesNotThrow(() -> JsonParser.parseString(text));
        assertTrue(text.contains("\"n\": 5"));
        assertFalse(text.contains("3000000000L"), "no type suffixes");
    }

    @Test
    void ourOwnParserReadsItBack() {
        DataNode.Compound c = Snbt.parse(Json5.print(sample()));
        assertEquals(sample().getString("title", null), c.getString("title", ""));
        assertEquals(5, c.getInt("n", 0));
        assertEquals(3_000_000_000L, c.getLong("big", 0));
        assertEquals(0.25, c.getDouble("d", 0));
        assertEquals(2, c.getList("objs").size());
        assertEquals("v", c.getString("weird key!", null));
    }

    @Test
    void refusesNanAndInfinity() {
        assertThrows(IllegalArgumentException.class, () -> Json5.print(DataNode.Compound.empty().with("x", Double.POSITIVE_INFINITY)));
    }

    @Test
    void writeJson5FileIsAtomicAndReadable(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("sub/q.json5");
        DataFiles.writeJson5(f, sample());
        assertDoesNotThrow(() -> JsonParser.parseString(Files.readString(f)));
        assertEquals(5, DataFiles.read(f).getInt("n", 0));
        assertFalse(Files.exists(dir.resolve("sub/q.json5.tmp")));
    }
}
