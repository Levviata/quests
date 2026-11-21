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

/** Read-only rules over a {@link QuestBook} and a {@link TeamProgress}: dependencies, visibility, sequencing. */
public final class QuestRules {

    private QuestRules() {}

    public static boolean taskDone(TeamProgress p, Task t) {
        return p.taskProgress(t.id()) >= t.maxProgress();
    }

    /** Number of finished tasks. Optional tasks count too (they simply are not required, see {@link #allRequiredTasksDone}). */
    public static int tasksDone(TeamProgress p, Quest q) {
        int n = 0;
        for (Task t : q.tasks) {
            if (taskDone(p, t)) {
                n++;
            }
        }
        return n;
    }

    /** True if every non-optional task is done; a quest with no tasks never completes by itself. */
    public static boolean allRequiredTasksDone(TeamProgress p, Quest q) {
        if (q.orTasks) {
            return tasksDone(p, q) > 0; // any one task is enough
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

    /** @return the first unfinished task if tasks must be done in order, otherwise null (any task may progress) */
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

    /**
     * Dependency check. Dependencies on quests that do not exist are ignored. An optional quest still has to be
     * completed before the quests that depend on it, as in FTB Quests. A quest with no remaining dependencies is open.
     */
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

    /** Whether tasks of the quest may currently make progress. */
    public static boolean canProgress(QuestBook book, TeamProgress p, Quest q) {
        if (p.isLocked()) {
            return false;
        }
        return book.effectiveMode(q) == ProgressionMode.FLEXIBLE || dependenciesMet(book, p, q);
    }

    /** Whether the quest is shown on the map at all. */
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
        // a quest whose dependencies are all hidden is hidden as well
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
                return false; // dependency loop: hide rather than recurse forever
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

    /** Whether title/description/tasks of a visible quest are readable. */
    public static boolean detailsVisible(QuestBook book, TeamProgress p, Quest q) {
        Chapter c = book.chapterOf(q.id);
        boolean hide = q.hideDetailsUntilStartable.resolve(c != null && c.hideQuestDetailsUntilStartable);
        return !hide || p.isCompleted(q.id) || dependenciesMet(book, p, q);
    }

    /** A chapter is complete when it has at least one non-optional quest and all of those are completed. */
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

    /** @return 0..100, share of task progress over the quest's tasks (completed quests count as 100) */
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
