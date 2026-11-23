/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.ChapterGroup;
import com.levviata.reflibquests.model.Definitions.ProgressionMode;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.RewardTable;
import com.levviata.reflibquests.model.Definitions.Task;

public final class QuestBook {

    public DataNode.Compound settings = DataNode.Compound.empty();
    public ProgressionMode progressionMode = ProgressionMode.LINEAR;
    public String fallbackLocale = "en_us";

    private final List<ChapterGroup> groups = new ArrayList<>();
    private final List<Chapter> chapters = new ArrayList<>();
    private final Map<String, RewardTable> tables = new LinkedHashMap<>();

    private final Map<String, Map<String, DataNode>> translations = new LinkedHashMap<>();

    private final Map<String, Quest> questIndex = new LinkedHashMap<>();
    private final Map<String, Task> taskIndex = new LinkedHashMap<>();
    private final Map<String, Quest> taskOwner = new LinkedHashMap<>();
    private final Map<String, Chapter> questChapter = new LinkedHashMap<>();

    public List<ChapterGroup> groups() {
        return groups;
    }

    public List<Chapter> chapters() {
        return chapters;
    }

    public Map<String, RewardTable> tables() {
        return tables;
    }

    public Map<String, Map<String, DataNode>> translations() {
        return translations;
    }

    public void reindex() {
        chapters.sort(Comparator.<Chapter>comparingInt(c -> c.order).thenComparing(c -> c.id));
        questIndex.clear();
        taskIndex.clear();
        taskOwner.clear();
        questChapter.clear();
        for (Chapter c : chapters) {
            for (Quest q : c.quests) {
                questIndex.putIfAbsent(q.id, q);
                questChapter.putIfAbsent(q.id, c);
                for (Task t : q.tasks) {
                    taskIndex.putIfAbsent(t.id(), t);
                    taskOwner.putIfAbsent(t.id(), q);
                }
            }
        }
    }

    public Quest quest(String id) {
        return questIndex.get(id);
    }

    public Task task(String id) {
        return taskIndex.get(id);
    }

    public Quest questOfTask(String taskId) {
        return taskOwner.get(taskId);
    }

    public Chapter chapterOf(String questId) {
        return questChapter.get(questId);
    }

    public Chapter chapter(String id) {
        for (Chapter c : chapters) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    public List<Quest> allQuests() {
        return Collections.unmodifiableList(new ArrayList<>(questIndex.values()));
    }

    public ProgressionMode effectiveMode(Quest q) {
        if (q.progressionMode != ProgressionMode.DEFAULT) {
            return q.progressionMode;
        }
        Chapter c = chapterOf(q.id);
        if (c != null && c.progressionMode != ProgressionMode.DEFAULT) {
            return c.progressionMode;
        }
        return progressionMode == ProgressionMode.DEFAULT ? ProgressionMode.LINEAR : progressionMode;
    }
}
