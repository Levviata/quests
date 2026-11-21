/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import com.levviata.reflib.api.data.DataFiles;
import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.QuestBook;

/** Loads {@code lang/<locale>/*.<ext>} translation maps into the book. */
final class Translations {

    private Translations() {}

    static void load(Path langDir, String ext, QuestBook book, List<String> problems) {
        if (!Files.isDirectory(langDir)) {
            return;
        }
        // one file per language: lang/<locale>.snbt (what the "lang splitter" tool leaves merged next to its split files)
        try (Stream<Path> single = Files.list(langDir)) {
            for (Path file : (Iterable<Path>) single.filter(Files::isRegularFile).sorted()::iterator) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(ext)) {
                    continue;
                }
                String locale = name.substring(0, name.length() - ext.length());
                if (!locale.matches("\\w+")) {
                    continue;
                }
                try {
                    book.translations().computeIfAbsent(locale, l -> new LinkedHashMap<>()).putAll(DataFiles.readLenient(file).entries());
                } catch (IOException ex) {
                    problems.add(ex.getMessage());
                }
            }
        } catch (IOException ex) {
            problems.add("cannot read " + langDir + ": " + ex.getMessage());
        }
        try (Stream<Path> locales = Files.list(langDir)) {
            for (Path localeDir : (Iterable<Path>) locales.filter(Files::isDirectory).sorted()::iterator) {
                String locale = localeDir.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!locale.matches("\\w+")) {
                    continue;
                }
                Map<String, DataNode> table = book.translations().computeIfAbsent(locale, l -> new LinkedHashMap<>());
                // text files may sit directly in the locale folder or in subfolders such as chapters/
                try (Stream<Path> files = Files.walk(localeDir)) {
                    List<Path> list = new ArrayList<>();
                    files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ext)).sorted().forEach(list::add);
                    for (Path file : list) {
                        try {
                            table.putAll(DataFiles.readLenient(file).entries());
                        } catch (IOException ex) {
                            problems.add(ex.getMessage());
                        }
                    }
                }
            }
        } catch (IOException ex) {
            problems.add("cannot read " + langDir + ": " + ex.getMessage());
        }
    }
}
