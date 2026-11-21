/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflib.api.data.Snbt;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.Task;

/**
 * Developer tool, not a normal test: run with {@code -Ppack=<path to a quest folder>} to print how the importer
 * copes with a real pack (counts, problems, missing texts, id namespaces). It asserts nothing and does nothing
 * when no pack is given. It only reads the folder; it never prints quest content beyond ids and counts.
 */
class RealPackReportTest {

    @Test
    void report() {
        String path = System.getProperty("reflibquests.pack", "");
        Assumptions.assumeFalse(path.isBlank(), "no -Ppack given");
        Path folder = Path.of(path);
        Assumptions.assumeTrue(Files.isDirectory(folder), "not a folder: " + folder);

        long t0 = System.nanoTime();
        UpstreamImporter.Result r = UpstreamImporter.load(folder);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        var book = r.book();
        System.out.println("=== PACK REPORT ===");
        System.out.println("imported in " + ms + " ms: chapters=" + book.chapters().size() + " groups=" + book.groups().size()
                + " quests=" + r.quests() + " tasks=" + r.tasks() + " rewards=" + r.rewards() + " tables=" + book.tables().size());
        System.out.println("locales=" + book.translations().keySet() + " fallback=" + book.fallbackLocale
                + " translation keys(en_us)=" + (book.translations().containsKey("en_us") ? book.translations().get("en_us").size() : 0));
        System.out.println("problems: " + r.problems().size());
        for (int i = 0; i < Math.min(25, r.problems().size()); i++) {
            System.out.println("  - " + r.problems().get(i));
        }

        int untitledQuests = 0;
        int untitledChapters = 0;
        int codes = 0;
        int links = 0;
        int images = 0;
        Map<String, Integer> taskTypes = new TreeMap<>();
        Map<String, Integer> rewardTypes = new TreeMap<>();
        Map<String, Integer> itemNamespaces = new TreeMap<>();
        Map<String, Integer> shapes = new TreeMap<>();
        for (Chapter c : book.chapters()) {
            if (c.title.isEmpty()) {
                untitledChapters++;
            }
            links += c.links.size();
            images += c.images.size();
            for (Quest q : c.quests) {
                if (q.title.isEmpty()) {
                    untitledQuests++;
                }
                if (q.title.contains("&") || q.description.stream().anyMatch(l -> l.contains("&"))) {
                    codes++;
                }
                shapes.merge(q.shape.isEmpty() ? "(default)" : q.shape, 1, Integer::sum);
                for (Task t : q.tasks) {
                    taskTypes.merge(t.type(), 1, Integer::sum);
                    countItem(itemNamespaces, t.params().get("item"));
                }
                for (Reward rw : q.rewards) {
                    rewardTypes.merge(rw.type(), 1, Integer::sum);
                    countItem(itemNamespaces, rw.params().get("item"));
                }
            }
        }
        System.out.println("untitled chapters=" + untitledChapters + " untitled quests=" + untitledQuests
                + " quests/chapters with '&' colour codes=" + codes + " quest links=" + links + " images=" + images);
        System.out.println("task types: " + taskTypes);
        System.out.println("reward types: " + rewardTypes);
        System.out.println("shapes: " + shapes);
        System.out.println("item namespaces (tasks+rewards): " + itemNamespaces);
        System.out.println("settings keys: " + book.settings.entries().keySet());
        // round trip: export the whole pack to a temporary folder, import it again and compare
        try {
            Path tmp = Files.createTempDirectory("rq-pack-roundtrip");
            long e0 = System.nanoTime();
            UpstreamExporter.export(book, tmp);
            long exportMs = (System.nanoTime() - e0) / 1_000_000;
            UpstreamImporter.Result again = UpstreamImporter.load(tmp);
            boolean same = BookCodec.write(book, DataNode.Compound.empty()).without("settings")
                    .equals(BookCodec.write(again.book(), DataNode.Compound.empty()).without("settings"));
            System.out.println("round trip: exported in " + exportMs + " ms, re-import problems=" + again.problems().size()
                    + ", book identical=" + same + ", translations identical=" + book.translations().equals(again.book().translations()));
            if (!same) {
                DataNode.Compound a = BookCodec.write(book, DataNode.Compound.empty());
                DataNode.Compound b = BookCodec.write(again.book(), DataNode.Compound.empty());
                for (String k : a.entries().keySet()) {
                    if (!java.util.Objects.equals(a.get(k), b.get(k))) {
                        System.out.println("differs at top-level key: " + k);
                        if (a.get(k) instanceof DataNode.Lst la && b.get(k) instanceof DataNode.Lst lb) {
                            System.out.println("  sizes " + la.items().size() + " vs " + lb.items().size());
                            for (int i = 0; i < Math.min(la.items().size(), lb.items().size()); i++) {
                                if (!la.items().get(i).equals(lb.items().get(i))) {
                                    String x = Snbt.printCompact(la.items().get(i) instanceof DataNode.Compound c1 ? c1 : DataNode.Compound.empty());
                                    String y = Snbt.printCompact(lb.items().get(i) instanceof DataNode.Compound c2 ? c2 : DataNode.Compound.empty());
                                    int d = 0;
                                    while (d < Math.min(x.length(), y.length()) && x.charAt(d) == y.charAt(d)) d++;
                                    System.out.println("  first diff item " + i + " at char " + d + ": A=" + x.substring(Math.max(0, d - 40), Math.min(x.length(), d + 60)) + " | B=" + y.substring(Math.max(0, d - 40), Math.min(y.length(), d + 60)));
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            try (var walk = Files.walk(tmp)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        } catch (java.io.IOException ex) {
            System.out.println("round trip failed: " + ex);
        }
        System.out.println("=== END ===");
    }

    private static void countItem(Map<String, Integer> into, DataNode spec) {
        String id = spec instanceof DataNode.Str s ? s.value() : spec instanceof DataNode.Compound c ? c.getString("id", "") : "";
        if (id.isEmpty()) {
            return;
        }
        int colon = id.indexOf(':');
        into.merge(colon < 0 ? "(none)" : id.substring(0, colon), 1, Integer::sum);
    }
}
