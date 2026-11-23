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
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ConvertLegacyPackTool {

    @Test
    void convert() throws IOException {
        String pack = System.getProperty("reflibquests.pack", "");
        String out = System.getProperty("reflibquests.out", "");
        String report = System.getProperty("reflibquests.report", "");
        Assumptions.assumeFalse(pack.isBlank() || out.isBlank(), "no -Ppack / -Pout given");

        var imported = LegacyImporter.loadWide(Path.of(pack));
        var book = imported.book();
        LegacyModernizer m = LegacyModernizer.builtin();
        m.modernize(book);
        Path folder = Path.of(out);
        if (Files.exists(folder) && Files.list(folder).findAny().isPresent()) {
            throw new IllegalStateException("output folder is not empty: " + folder);
        }
        UpstreamExporter.export(book, folder);
        System.out.println("=== CONVERTED === chapters=" + book.chapters().size() + " quests=" + imported.quests() + " tasks=" + imported.tasks()
                + " rewards=" + imported.rewards() + " tables=" + book.tables().size());
        System.out.println("importer problems: " + imported.problems().size());
        imported.problems().forEach(p -> System.out.println("  - " + p));
        System.out.println("decisions: " + m.counts());
        if (!report.isBlank()) {
            Files.writeString(Path.of(report), report(imported, m));
        }
    }

    private static String report(UpstreamImporter.Result imported, LegacyModernizer m) {
        StringBuilder sb = new StringBuilder();
        sb.append("# LvNTM conversion report (generated)\n\n");
        sb.append("Quests ").append(imported.quests()).append(", tasks ").append(imported.tasks()).append(", rewards ").append(imported.rewards()).append(".\n\n");
        sb.append("Decisions taken: ").append(m.counts()).append("\n\n");
        if (!imported.problems().isEmpty()) {
            sb.append("## Importer problems\n\n");
            imported.problems().forEach(p -> sb.append("- ").append(p).append('\n'));
            sb.append('\n');
        }
        Map<String, Map<String, List<LegacyModernizer.Note>>> byKind = new TreeMap<>();
        for (var n : m.notes()) {
            byKind.computeIfAbsent(n.kind(), k -> new LinkedHashMap<>()).computeIfAbsent(n.from() + " -> " + n.to(), k -> new ArrayList<>()).add(n);
        }
        for (String kind : List.of("unresolved", "substitute-weak", "substitute", "inferred", "kept", "dropped", "renamed", "by-name", "derived", "derived-renamed", "component", "mapped")) {
            var group = byKind.get(kind);
            if (group == null) {
                continue;
            }
            sb.append("## ").append(kind).append(" (").append(group.size()).append(" distinct, ")
                    .append(group.values().stream().mapToInt(List::size).sum()).append(" uses)\n\n");
            sb.append("| from -> to | uses | note | first seen in |\n|---|---|---|---|\n");
            for (var e : group.entrySet()) {
                var first = e.getValue().get(0);
                sb.append("| ").append(cell(e.getKey())).append(" | ").append(e.getValue().size()).append(" | ").append(cell(first.note()))
                        .append(" | ").append(cell(first.where())).append(" |\n");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String cell(String s) {
        return s.replace("|", "\\|").replace("\n", " ");
    }
}
