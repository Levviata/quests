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
        try (Stream<Path> locales = Files.list(langDir)) {
            for (Path localeDir : (Iterable<Path>) locales.filter(Files::isDirectory).sorted()::iterator) {
                String locale = localeDir.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!locale.matches("\\w+")) {
                    continue;
                }
                Map<String, DataNode> table = book.translations().computeIfAbsent(locale, l -> new LinkedHashMap<>());
                try (Stream<Path> files = Files.list(localeDir)) {
                    List<Path> list = new ArrayList<>();
                    files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ext)).sorted().forEach(list::add);
                    for (Path file : list) {
                        try {
                            table.putAll(DataFiles.read(file).entries());
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
