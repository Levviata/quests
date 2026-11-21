/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.data;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataFilesTest {

    @TempDir
    Path dir;

    @Test
    void writeThenReadRoundTripAndCreatesParents() throws IOException {
        Path file = dir.resolve("a/b/chapter.snbt");
        DataNode.Compound data = DataNode.Compound.empty().with("title", "x").with("n", 3);
        DataFiles.write(file, data);
        assertEquals(data, DataFiles.read(file));
        assertFalse(Files.exists(dir.resolve("a/b/chapter.snbt.tmp")));
    }

    @Test
    void overwriteKeepsPreviousAsBackup() throws IOException {
        Path file = dir.resolve("f.snbt");
        DataNode.Compound v1 = DataNode.Compound.empty().with("v", 1);
        DataNode.Compound v2 = DataNode.Compound.empty().with("v", 2);
        DataFiles.write(file, v1);
        DataFiles.write(file, v2);
        assertEquals(v2, DataFiles.read(file));
        assertEquals(v1, DataFiles.read(dir.resolve("f.snbt.bak")));
    }

    @Test
    void parseErrorNamesTheFile() throws IOException {
        Path file = dir.resolve("bad.snbt");
        Files.writeString(file, "{ a: nope }");
        DataFiles.DataFileException ex = assertThrows(DataFiles.DataFileException.class, () -> DataFiles.read(file));
        assertTrue(ex.getMessage().contains("bad.snbt"));
        assertTrue(ex.getMessage().contains("line 1"));
    }

    @Test
    void missingFileIsAnIoError() {
        assertThrows(IOException.class, () -> DataFiles.read(dir.resolve("none.snbt")));
    }
}
