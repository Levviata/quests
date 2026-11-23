/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class DataFiles {

    public static final class DataFileException extends IOException {
        public DataFileException(Path file, String message, Throwable cause) {
            super(file + ": " + message, cause);
        }
    }

    private DataFiles() {}

    public static void write(Path file, DataNode.Compound data) throws IOException {
        writeText(file, Snbt.print(data) + "\n");
    }

    public static void writeJson5(Path file, DataNode.Compound data) throws IOException {
        writeText(file, Json5.print(data) + "\n");
    }

    private static void writeText(Path file, String text) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = dir.resolve(file.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        if (Files.exists(file)) {
            Files.copy(file, dir.resolve(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static DataNode.Compound readLenient(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        try {
            return Snbt.parseLenient(text);
        } catch (SnbtException ex) {
            throw new DataFileException(file, ex.getMessage(), ex);
        }
    }

    public static DataNode.Compound read(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        try {
            return Snbt.parse(text);
        } catch (SnbtException ex) {
            throw new DataFileException(file, ex.getMessage(), ex);
        }
    }
}
