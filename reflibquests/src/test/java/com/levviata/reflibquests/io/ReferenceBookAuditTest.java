/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.text.RichText;

/**
 * Developer tool (run with -Ppack=): prints how the real FTB Quests 26.1.2 reference book fares in the importer,
 * property by property. It asserts nothing; it is the Phase 6 import report.
 */
class ReferenceBookAuditTest {

    @Test
    void report() {
        String path = System.getProperty("reflibquests.pack", "");
        Assumptions.assumeFalse(path.isBlank(), "no -Ppack given");
        Path folder = Path.of(path);
        Assumptions.assumeTrue(Files.isDirectory(folder));
        var r = UpstreamImporter.load(folder);
        var book = r.book();
        System.out.println("=== REFERENCE AUDIT ===");
        System.out.println("table ids in book: " + book.tables().keySet());
        for (Chapter c : book.chapters()) {
            System.out.println("chapter " + c.title + " group='" + c.raw.getString("group", "") + "' quests=" + c.quests.size() + " images=" + c.images.size());
            for (var img : c.images) {
                System.out.println("  image raw keys " + img.raw().entries().keySet() + " image=" + img.raw().getString("image", "?"));
            }
            for (Quest q : c.quests) {
                for (Task t : q.tasks) {
                    boolean sup = com.levviata.reflibquests.game.QuestServer.SUPPORTED_TASKS.contains(t.type());
                    System.out.println("  task " + t.type() + " supported=" + sup + " params=" + t.params().entries().keySet());
                }
                for (Reward rw : q.rewards) {
                    DataNode tid = rw.params().get("table_id");
                    String kind = tid == null ? "-" : tid.getClass().getSimpleName();
                    String asString = rw.params().getString("table_id", "<none>");
                    System.out.println("  reward " + rw.type() + " keys=" + rw.params().entries().keySet() + " table_id node=" + kind
                            + " getString='" + asString + "' tableFound=" + book.tables().containsKey(Ids.normalize(asString)));
                }
                if (!q.description.isEmpty()) {
                    System.out.println("  description raw: " + q.description);
                    for (var page : RichText.parse(q.description)) {
                        for (var line : page.lines()) {
                            StringBuilder sb = new StringBuilder();
                            for (var el : line.elements()) {
                                sb.append(el instanceof RichText.Span s ? "[" + s.text() + "]" : "<img>");
                            }
                            System.out.println("  rendered line: " + sb);
                        }
                    }
                }
                if (!q.dependencies.isEmpty()) {
                    System.out.println("  quest " + q.id + " dependencies=" + q.dependencies);
                }
            }
        }
        System.out.println("=== END ===");
    }
}
