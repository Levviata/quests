/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.impl.assets;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssetIndexTest {

    @TempDir
    Path dir;

    static File makeZip(Path dir, String name, String... namesAndContents) throws IOException {
        File f = dir.resolve(name).toFile();
        try (OutputStream out = Files.newOutputStream(f.toPath()); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                zip.putNextEntry(new ZipEntry(namesAndContents[i]));
                zip.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return f;
    }

    @Test
    void indexesOnlyAssetsAndNeverTheMinecraftDomain() throws IOException {
        File f = makeZip(dir, "a.jar",
                "assets/modone/textures/gui/Icon.PNG", "png",
                "assets/modtwo/lang/en_us.json", "{}",
                "assets/minecraft/textures/x.png", "no",
                "com/example/Code.class", "code",
                "META-INF/MANIFEST.MF", "m");
        try (ZipAssetIndex idx = new ZipAssetIndex(f, null)) {
            assertEquals(java.util.Set.of("modone", "modtwo"), idx.domains());
            assertEquals(2, idx.size());
            assertFalse(idx.has("minecraft", "textures/x.png"));
        }
    }

    @Test
    void lookupIsCaseInsensitiveAndReadsContent() throws IOException {
        File f = makeZip(dir, "a.jar", "assets/ModOne/textures/gui/Icon.PNG", "hello");
        try (ZipAssetIndex idx = new ZipAssetIndex(f, null)) {
            assertTrue(idx.has("modone", "textures/gui/icon.png"));
            assertEquals("hello", new String(idx.read("modone", "TEXTURES/gui/ICON.png"), StandardCharsets.UTF_8));
            assertNull(idx.read("modone", "textures/missing.png"));
        }
    }

    @Test
    void domainFilterRestrictsExposure() throws IOException {
        File f = makeZip(dir, "a.jar", "assets/one/a.txt", "1", "assets/two/b.txt", "2");
        try (ZipAssetIndex idx = new ZipAssetIndex(f, List.of("TWO"))) {
            assertEquals(java.util.Set.of("two"), idx.domains());
            assertFalse(idx.has("one", "a.txt"));
            assertTrue(idx.has("two", "b.txt"));
        }
    }

    @Test
    void brokenArchiveIsAnIoError() throws IOException {
        File f = dir.resolve("broken.jar").toFile();
        Files.writeString(f.toPath(), "this is not a zip");
        assertThrows(IOException.class, () -> new ZipAssetIndex(f, null));
    }

    @Test
    void langJsonConvertsStringsOnly() {
        String json = "{\"a.b\": \"Hello %s\", \"multi\": \"one\\ntwo\", \"n\": 5, \"obj\": {\"x\": \"y\"}, \"uni\": \"caf\\u00e9\"}";
        String lang = new String(LangJson.toLang(json.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        assertTrue(lang.contains("a.b=Hello %s\n"));
        assertTrue(lang.contains("multi=one two\n"));
        assertTrue(lang.contains("uni=café\n"));
        assertFalse(lang.contains("n="));
        assertFalse(lang.contains("obj"));
    }
}
