/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.ProgressionMode;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Task;

public final class QuestRules {

    private QuestRules() {}

    public static boolean taskDone(TeamProgress p, Task t) {
        return p.taskProgress(t.id()) >= t.maxProgress();
    }

    public static int tasksDone(TeamProgress p, Quest q) {
        int n = 0;
        for (Task t : q.tasks) {
            if (taskDone(p, t)) {
                n++;
            }
        }
        return n;
    }

    public static boolean allRequiredTasksDone(TeamProgress p, Quest q) {
        if (q.orTasks) {
            return tasksDone(p, q) > 0;
        }
        boolean anyRequired = false;
        for (Task t : q.tasks) {
            if (t.optionalTask()) {
                continue;
            }
            anyRequired = true;
            if (!taskDone(p, t)) {
                return false;
            }
        }
        return anyRequired;
    }

    public static boolean sequential(QuestBook book, Quest q) {
        Chapter c = book.chapterOf(q.id);
        return q.requireSequentialTasks.resolve(c != null && c.requireSequentialTasks);
    }

    public static Task requiredNextTask(QuestBook book, TeamProgress p, Quest q) {
        if (!sequential(book, q)) {
            return null;
        }
        for (Task t : q.tasks) {
            if (!taskDone(p, t)) {
                return t;
            }
        }
        return null;
    }

    public static boolean repeatable(QuestBook book, Quest q) {
        Chapter c = book.chapterOf(q.id);
        return q.canRepeat.resolve(c != null && c.defaultRepeatable);
    }

    public static boolean excluded(QuestBook book, TeamProgress p, Quest q) {
        if (p.isCompleted(q.id)) {
            return false;
        }
        for (String id : q.dependencies) {
            Quest dep = book.quest(id);
            if (dep != null && dep.maxCompletableDependents > 0) {
                int done = 0;
                for (Quest other : book.allQuests()) {
                    if (other != q && other.dependencies.contains(dep.id) && p.isCompleted(other.id)) {
                        done++;
                    }
                }
                if (done >= dep.maxCompletableDependents) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean dependenciesMet(QuestBook book, TeamProgress p, Quest q) {
        List<Quest> required = new ArrayList<>();
        for (String id : q.dependencies) {
            Quest dep = book.quest(id);
            if (dep != null) {
                required.add(dep);
            }
        }
        if (required.isEmpty()) {
            return true;
        }
        if (!p.isCompleted(q.id)) {

            for (Quest dep : required) {
                if (dep.maxCompletableDependents > 0) {
                    int done = 0;
                    for (Quest other : book.allQuests()) {
                        if (other != q && other.dependencies.contains(dep.id) && p.isCompleted(other.id)) {
                            done++;
                        }
                    }
                    if (done >= dep.maxCompletableDependents) {
                        return false;
                    }
                }
            }
        }
        int satisfied = 0;
        for (Quest dep : required) {
            boolean ok = q.dependencyMode.requiresCompletion()
                    ? p.isCompleted(dep.id)
                    : p.isStarted(dep.id) || p.isCompleted(dep.id);
            if (ok) {
                satisfied++;
            }
        }
        int needed = q.minRequiredDependencies > 0
                ? Math.min(q.minRequiredDependencies, required.size())
                : (q.dependencyMode.requiresAll() ? required.size() : 1);
        return satisfied >= needed;
    }

    public static boolean canProgress(QuestBook book, TeamProgress p, Quest q) {
        if (p.isLocked()) {
            return false;
        }
        return book.effectiveMode(q) == ProgressionMode.FLEXIBLE || dependenciesMet(book, p, q);
    }

    public static boolean isVisible(QuestBook book, TeamProgress p, Quest q) {
        return isVisible(book, p, q, new HashSet<>(), new java.util.HashMap<>());
    }

    private static boolean isVisible(QuestBook book, TeamProgress p, Quest q, Set<String> visiting, java.util.Map<String, Boolean> memo) {
        Boolean known = memo.get(q.id);
        if (known != null) {
            return known;
        }
        boolean result = visibleNow(book, p, q, visiting, memo);
        memo.put(q.id, result);
        return result;
    }

    private static boolean visibleNow(QuestBook book, TeamProgress p, Quest q, Set<String> visiting, java.util.Map<String, Boolean> memo) {
        if (p.isCompleted(q.id)) {
            return true;
        }
        Chapter c = book.chapterOf(q.id);
        if (c != null && c.alwaysInvisible) {
            return false;
        }
        if (q.invisible) {
            return false;
        }
        if (q.invisibleUntilTasks > 0 && tasksDone(p, q) < q.invisibleUntilTasks) {
            return false;
        }
        if (book.settings.getBool("hide_excluded_quests", false) && excluded(book, p, q)) {
            return false;
        }

        boolean anyDependency = false;
        boolean anyVisible = false;
        if (visiting.add(q.id)) {
            try {
                for (String id : q.dependencies) {
                    Quest dep = book.quest(id);
                    if (dep != null) {
                        anyDependency = true;
                        if (isVisible(book, p, dep, visiting, memo)) {
                            anyVisible = true;
                            break;
                        }
                    }
                }
            } finally {
                visiting.remove(q.id);
            }
        }
        if (anyDependency && !anyVisible) {
            return false;
        }
        boolean needDepsVisible = q.hideUntilDepsVisible.resolve(c != null && c.hideQuestUntilDepsVisible);
        if (needDepsVisible) {
            if (!visiting.add(q.id)) {
                return false;
            }
            try {
                for (String id : q.dependencies) {
                    Quest dep = book.quest(id);
                    if (dep != null && !isVisible(book, p, dep, visiting, memo)) {
                        return false;
                    }
                }
            } finally {
                visiting.remove(q.id);
            }
        }
        boolean needDepsComplete = q.hideUntilDepsComplete.resolve(c != null && c.hideQuestUntilDepsComplete);
        if (needDepsComplete && !dependenciesMet(book, p, q)) {
            return false;
        }
        return true;
    }

    public static boolean detailsVisible(QuestBook book, TeamProgress p, Quest q) {
        Chapter c = book.chapterOf(q.id);
        boolean hide = q.hideDetailsUntilStartable.resolve(c != null && c.hideQuestDetailsUntilStartable);
        return !hide || p.isCompleted(q.id) || dependenciesMet(book, p, q);
    }

    public static boolean chapterCompleted(TeamProgress p, Chapter c) {
        boolean any = false;
        for (Quest q : c.quests) {
            if (q.optional) {
                continue;
            }
            any = true;
            if (!p.isCompleted(q.id)) {
                return false;
            }
        }
        return any;
    }

    public static int percent(TeamProgress p, Quest q) {
        if (p.isCompleted(q.id)) {
            return 100;
        }
        if (q.tasks.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Task t : q.tasks) {
            sum += Math.min(1.0, (double) p.taskProgress(t.id()) / t.maxProgress());
        }
        return (int) Math.floor(100.0 * sum / q.tasks.size());
    }
}
