/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ApiBoundaryTest {

    private static final Path MAIN = Path.of("src/main/java/com/levviata/reflib");

    private static List<Path> javaFiles(String subDir) throws IOException {
        try (Stream<Path> s = Files.walk(MAIN.resolve(subDir))) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void exampleProjectOnlyImportsPublicApi() throws IOException {
        List<Path> files = javaFiles("devexample");
        assertFalse(files.isEmpty());
        for (Path file : files) {
            String text = Files.readString(file);
            assertFalse(text.contains("com.levviata.reflib.impl"), file + " imports reflib internals");
        }
    }

    @Test
    void apiDoesNotDependOnClientCodeOrImplDetails() throws IOException {
        for (Path file : javaFiles("api")) {
            String text = Files.readString(file);
            assertFalse(text.contains("import net.minecraft.client"), file + " imports client classes");
            assertFalse(text.contains("com.levviata.reflib.gui"), file + " depends on gui");
        }
    }

    @Test
    void commonCodeDoesNotUseGui() throws IOException {
        for (String dir : List.of("impl", "api")) {
            for (Path file : javaFiles(dir)) {
                assertFalse(Files.readString(file).contains("com.levviata.reflib.gui"), file + " depends on gui");
            }
        }
    }
}
