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

/**
 * Safe reading and writing of data files in the {@link Snbt} text format. Writes go to a temporary file first
 * and replace the target in one step, so a crash never leaves a half-written file; the previous version is
 * kept as {@code <name>.bak}. Parse errors are reported with the file name, line and column.
 */
public final class DataFiles {

    /** A data file could not be read or parsed. */
    public static final class DataFileException extends IOException {
        public DataFileException(Path file, String message, Throwable cause) {
            super(file + ": " + message, cause);
        }
    }

    private DataFiles() {}

    public static void write(Path file, DataNode.Compound data) throws IOException {
        writeText(file, Snbt.print(data) + "\n");
    }

    /** Like {@link #write} but in {@link Json5} form (commas, quoted keys), for files other programs read. */
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

    /**
     * @throws DataFileException if the text is not valid
     * @throws IOException       if the file cannot be read
     */
    public static DataNode.Compound read(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        try {
            return Snbt.parse(text);
        } catch (SnbtException ex) {
            throw new DataFileException(file, ex.getMessage(), ex);
        }
    }
}
