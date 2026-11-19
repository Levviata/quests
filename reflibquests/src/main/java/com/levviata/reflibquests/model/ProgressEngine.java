/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.ProgressionMode;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.Task;

/**
 * The rules that change a team's progress. All operations return the events they caused, so the game layer can
 * post Forge events, notify players and queue sync deltas. Pure logic: no Minecraft, no clocks (the caller
 * passes the time), fully unit-tested.
 */
public final class ProgressEngine {

    private final QuestBook book;

    public ProgressEngine(QuestBook book) {
        this.book = book;
    }

    /**
     * Set a task's progress (clamped to 0..max). Ignored if the quest cannot currently progress, or (for
     * sequential quests) if an earlier task is unfinished. Progress never decreases through this method; use
     * {@link #resetQuest} to reset.
     */
    public List<ProgressEvent> setTaskProgress(TeamProgress p, String taskId, long value, long now) {
        List<ProgressEvent> events = new ArrayList<>();
        Task task = book.task(taskId);
        Quest quest = book.questOfTask(taskId);
        if (task == null || quest == null) {
            return events;
        }
        if (p.isCompleted(quest.id)) {
            return events; // repeatable quests are reset by tickRepeats before they progress again
        }
        if (!QuestRules.canProgress(book, p, quest)) {
            return events;
        }
        Task next = QuestRules.requiredNextTask(book, p, quest);
        if (next != null && !next.id().equals(taskId)) {
            return events;
        }
        long old = p.taskProgress(taskId);
        long clamped = Math.max(old, Math.min(value, task.maxProgress()));
        if (clamped == old) {
            return events;
        }
        p.taskProgress.put(taskId, clamped);
        events.add(new ProgressEvent.TaskProgressed(taskId, old, clamped));
        if (!p.isStarted(quest.id)) {
            p.started.put(quest.id, now);
            events.add(new ProgressEvent.QuestStarted(quest.id));
        }
        settle(p, now, events);
        return events;
    }

    /** Add to a task's progress. */
    public List<ProgressEvent> addTaskProgress(TeamProgress p, String taskId, long delta, long now) {
        return setTaskProgress(p, taskId, p.taskProgress(taskId) + delta, now);
    }

    /**
     * Completes every quest whose required tasks are done and which may complete (dependencies met, or any
     * mode for quests without dependencies). Repeats until nothing changes, because one completion can
     * unblock a flexible quest that was waiting.
     */
    private void settle(TeamProgress p, long now, List<ProgressEvent> events) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Quest q : book.allQuests()) {
                if (p.isCompleted(q.id) || !QuestRules.allRequiredTasksDone(p, q)) {
                    continue;
                }
                if (!QuestRules.dependenciesMet(book, p, q)) {
                    continue; // flexible quest waiting for its dependencies
                }
                complete(p, q, now, events);
                changed = true;
            }
        }
    }

    private void complete(TeamProgress p, Quest q, long now, List<ProgressEvent> events) {
        if (!p.isStarted(q.id)) {
            p.started.put(q.id, now);
            events.add(new ProgressEvent.QuestStarted(q.id));
        }
        p.completed.put(q.id, now);
        p.completionCount.merge(q.id, 1, Integer::sum);
        if (QuestRules.repeatable(book, q)) {
            p.repeatReadyAt.put(q.id, now + q.repeatCooldown * 50L); // cooldown is in ticks
        }
        events.add(new ProgressEvent.QuestCompleted(q.id));
        Chapter c = book.chapterOf(q.id);
        if (c != null && QuestRules.chapterCompleted(p, c)) {
            events.add(new ProgressEvent.ChapterCompleted(c.id));
        }
    }

    /** Administrative: complete a quest now, filling all tasks. */
    public List<ProgressEvent> forceComplete(TeamProgress p, String questId, long now) {
        List<ProgressEvent> events = new ArrayList<>();
        Quest q = book.quest(questId);
        if (q == null || p.isCompleted(questId)) {
            return events;
        }
        for (Task t : q.tasks) {
            long old = p.taskProgress(t.id());
            if (old < t.maxProgress()) {
                p.taskProgress.put(t.id(), t.maxProgress());
                events.add(new ProgressEvent.TaskProgressed(t.id(), old, t.maxProgress()));
            }
        }
        complete(p, q, now, events);
        settle(p, now, events);
        return events;
    }

    /** Administrative: clear a quest's progress and claims (completion count is kept for repeatables). */
    public List<ProgressEvent> resetQuest(TeamProgress p, String questId) {
        List<ProgressEvent> events = new ArrayList<>();
        Quest q = book.quest(questId);
        if (q == null) {
            return events;
        }
        boolean had = p.started.remove(questId) != null | p.completed.remove(questId) != null;
        p.repeatReadyAt.remove(questId);
        for (Task t : q.tasks) {
            had |= p.taskProgress.remove(t.id()) != null;
        }
        for (Reward r : q.rewards) {
            p.claimed.keySet().removeIf(k -> k.startsWith(r.id() + "@"));
        }
        if (had) {
            events.add(new ProgressEvent.QuestReset(questId));
        }
        return events;
    }

    /** Reset repeatable quests whose cooldown has passed. */
    public List<ProgressEvent> tickRepeats(TeamProgress p, long now) {
        List<ProgressEvent> events = new ArrayList<>();
        for (String id : new ArrayList<>(p.repeatReadyAt.keySet())) {
            if (p.repeatReadyAt.get(id) <= now && p.isCompleted(id)) {
                Quest q = book.quest(id);
                if (q == null) {
                    p.repeatReadyAt.remove(id);
                    continue;
                }
                int count = p.completionCount(id);
                events.addAll(resetQuest(p, id));
                p.completionCount.put(id, count);
            }
        }
        return events;
    }

    /** Whether the player can claim this reward now (quest completed, not claimed, not blocked). */
    public boolean canClaim(TeamProgress p, UUID player, Reward reward) {
        Quest q = questOf(reward);
        if (q == null || !p.isCompleted(q.id) || p.isClaimed(reward.id(), player)) {
            return false;
        }
        return !p.areRewardsBlocked() || q.ignoreRewardBlocking;
    }

    /** Record a claim. The caller grants the reward only if this returns true. */
    public boolean claim(TeamProgress p, UUID player, Reward reward, long now) {
        if (!canClaim(p, player, reward)) {
            return false;
        }
        p.claimed.put(TeamProgress.claimKey(reward.id(), player), now);
        return true;
    }

    public List<Reward> unclaimedRewards(TeamProgress p, UUID player, Quest q) {
        List<Reward> out = new ArrayList<>();
        for (Reward r : q.rewards) {
            if (canClaim(p, player, r)) {
                out.add(r);
            }
        }
        return out;
    }

    private Quest questOf(Reward reward) {
        for (Quest q : book.allQuests()) {
            if (q.rewards.contains(reward)) {
                return q;
            }
        }
        return null;
    }

    /** Linear/flexible helper used by the UI: true if the quest is greyed out as locked. */
    public boolean isLocked(TeamProgress p, Quest q) {
        return book.effectiveMode(q) != ProgressionMode.FLEXIBLE && !QuestRules.dependenciesMet(book, p, q);
    }
}
