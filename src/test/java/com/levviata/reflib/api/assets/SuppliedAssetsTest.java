/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.assets;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SuppliedAssetsTest {

    @TempDir
    Path game;

    @BeforeEach
    void reset() {
        SuppliedAssets.resetForTests();
    }

    @AfterEach
    void cleanup() {
        SuppliedAssets.resetForTests();
    }

    private void jar(String name, String... namesAndContents) throws IOException {
        Path folder = game.resolve(SuppliedAssets.FOLDER);
        Files.createDirectories(folder);
        try (OutputStream out = Files.newOutputStream(folder.resolve(name)); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                zip.putNextEntry(new ZipEntry(namesAndContents[i]));
                zip.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    @Test
    void createsFolderWhenMissingAndReportsRequirementMissing() {
        SuppliedAssets.init(game.toFile());
        assertTrue(Files.isDirectory(game.resolve(SuppliedAssets.FOLDER)));
        SuppliedAssets.Requirement r = SuppliedAssets.declare("proj", "Original 1.0", "somewhere", "original");
        assertFalse(r.isPresent());
        assertFalse(SuppliedAssets.isPresent("proj"));
        assertEquals(1, SuppliedAssets.missing().size());
        assertEquals("proj", SuppliedAssets.missing().get(0).projectId());
    }

    @Test
    void findsJarProvidingAllDomains() throws IOException {
        jar("a.jar", "assets/one/x.txt", "1");
        jar("b.jar", "assets/one/x.txt", "1", "assets/two/y.txt", "2");
        SuppliedAssets.init(game.toFile());
        SuppliedAssets.Requirement both = SuppliedAssets.declare("p", "X", "w", "one", "two");
        assertTrue(both.isPresent());
        assertEquals("b.jar", both.jar().getName());
        SuppliedAssets.Requirement none = SuppliedAssets.declare("q", "Y", "w", "one", "three");
        assertFalse(none.isPresent());
        assertTrue(SuppliedAssets.isPresent("p"));
        assertEquals(1, SuppliedAssets.missing().size());
    }

    @Test
    void readsCaseInsensitivelyThroughAliasesAndFirstJarWins() throws IOException {
        jar("a.jar", "assets/mod/Textures/Icon.png", "from-a");
        jar("b.jar", "assets/mod/textures/other.png", "from-b");
        SuppliedAssets.init(game.toFile());
        assertEquals("from-a", new String(SuppliedAssets.read("mod", "textures/icon.png"), StandardCharsets.UTF_8));
        assertNull(SuppliedAssets.read("mod", "textures/new_name.png"));
        SuppliedAssets.alias("mod", "textures/new_name.png", "textures/other.png");
        assertEquals("from-b", new String(SuppliedAssets.read("mod", "textures/new_name.png"), StandardCharsets.UTF_8));
    }

    @Test
    void ignoresBrokenAndUnrelatedFiles() throws IOException {
        Path folder = game.resolve(SuppliedAssets.FOLDER);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("broken.jar"), "not a zip");
        Files.writeString(folder.resolve("notes.txt"), "hello");
        jar("code_only.jar", "com/example/A.class", "x");
        SuppliedAssets.init(game.toFile());
        assertTrue(SuppliedAssets.sources().isEmpty());
        assertFalse(SuppliedAssets.declare("p", "X", "w", "any").isPresent());
    }

    @Test
    void minecraftDomainIsNeverExposed() throws IOException {
        jar("a.jar", "assets/minecraft/textures/block/stone.png", "no", "assets/mod/a.txt", "yes");
        SuppliedAssets.init(game.toFile());
        assertNull(SuppliedAssets.read("minecraft", "textures/block/stone.png"));
        assertNotNull(SuppliedAssets.read("mod", "a.txt"));
    }

    @Test
    void rescanPicksUpNewJarsAndReresolvesRequirements() throws IOException {
        SuppliedAssets.init(game.toFile());
        assertFalse(SuppliedAssets.declare("p", "X", "w", "mod").isPresent());
        jar("late.jar", "assets/mod/a.txt", "1");
        SuppliedAssets.init(game.toFile());
        assertTrue(SuppliedAssets.isPresent("p"));
    }

    @Test
    void declareNeedsADomain() {
        assertThrows(IllegalArgumentException.class, () -> SuppliedAssets.declare("p", "X", "w"));
    }
}
