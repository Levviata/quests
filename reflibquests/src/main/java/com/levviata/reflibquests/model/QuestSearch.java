/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import com.levviata.reflibquests.text.RichText;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;

public final class QuestSearch {

    public record Result(String questId, String chapterId, String title, String chapterTitle) {}

    private QuestSearch() {}

    public static List<Result> search(QuestBook book, TeamProgress p, String query, int limit) {
        List<String> words = new ArrayList<>();
        for (String w : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        if (words.isEmpty() || limit <= 0) {
            return List.of();
        }
        record Scored(int rank, int order, Result result) {}
        List<Scored> hits = new ArrayList<>();
        int order = 0;
        for (Chapter c : book.chapters()) {
            if (c.alwaysInvisible) {
                continue;
            }
            for (Quest q : c.quests) {
                order++;
                if (!QuestRules.isVisible(book, p, q) || !QuestRules.detailsVisible(book, p, q)) {
                    continue;
                }
                String title = q.title.isEmpty() ? q.id : q.title;
                String titleLower = RichText.stripCodes(title).toLowerCase(Locale.ROOT);
                StringBuilder hay = new StringBuilder(titleLower).append('\n')
                        .append(RichText.stripCodes(q.subtitle).toLowerCase(Locale.ROOT))
                        .append('\n').append(q.id.toLowerCase(Locale.ROOT));
                for (String line : q.description) {
                    hay.append('\n').append(RichText.stripCodes(line).toLowerCase(Locale.ROOT));
                }
                boolean all = true;
                for (String w : words) {
                    if (hay.indexOf(w) < 0) {
                        all = false;
                        break;
                    }
                }
                if (!all) {
                    continue;
                }
                int rank = titleLower.startsWith(words.get(0)) ? 0 : titleLower.contains(words.get(0)) ? 1 : 2;
                hits.add(new Scored(rank, order, new Result(q.id, c.id, title, c.title.isEmpty() ? c.id : c.title)));
            }
        }
        hits.sort(Comparator.<Scored>comparingInt(Scored::rank).thenComparingInt(Scored::order));
        List<Result> out = new ArrayList<>();
        for (Scored s : hits) {
            if (out.size() >= limit) {
                break;
            }
            out.add(s.result());
        }
        return out;
    }
}
