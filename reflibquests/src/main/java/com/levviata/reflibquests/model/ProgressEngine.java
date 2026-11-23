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

public final class ProgressEngine {

    private final QuestBook book;

    public ProgressEngine(QuestBook book) {
        this.book = book;
    }

    public List<ProgressEvent> setTaskProgress(TeamProgress p, String taskId, long value, long now) {
        List<ProgressEvent> events = new ArrayList<>();
        Task task = book.task(taskId);
        Quest quest = book.questOfTask(taskId);
        if (task == null || quest == null) {
            return events;
        }
        if (p.isCompleted(quest.id)) {
            return events;
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

    public List<ProgressEvent> addTaskProgress(TeamProgress p, String taskId, long delta, long now) {
        return setTaskProgress(p, taskId, p.taskProgress(taskId) + delta, now);
    }

    private void settle(TeamProgress p, long now, List<ProgressEvent> events) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Quest q : book.allQuests()) {
                if (p.isCompleted(q.id) || !QuestRules.allRequiredTasksDone(p, q)) {
                    continue;
                }
                if (!QuestRules.dependenciesMet(book, p, q)) {
                    continue;
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
        if (QuestRules.repeatable(book, q) && q.rewards.isEmpty()) {
            p.repeatReadyAt.put(q.id, now + q.repeatCooldown * 1000L);
        }
        events.add(new ProgressEvent.QuestCompleted(q.id));
        Chapter c = book.chapterOf(q.id);
        if (c != null && QuestRules.chapterCompleted(p, c)) {
            events.add(new ProgressEvent.ChapterCompleted(c.id));
        }
    }

    public List<ProgressEvent> forceComplete(TeamProgress p, String questId, long now) {
        return forceComplete(p, questId, now, false);
    }

    public List<ProgressEvent> forceComplete(TeamProgress p, String questId, long now, boolean withDependencies) {
        List<ProgressEvent> events = new ArrayList<>();
        Quest q = book.quest(questId);
        if (q == null) {
            return events;
        }
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        collect(q, withDependencies, seen);
        for (String id : seen) {
            Quest target = book.quest(id);
            if (target == null || p.isCompleted(id)) {
                continue;
            }
            for (Task t : target.tasks) {
                long old = p.taskProgress(t.id());
                if (old < t.maxProgress()) {
                    p.taskProgress.put(t.id(), t.maxProgress());
                    events.add(new ProgressEvent.TaskProgressed(t.id(), old, t.maxProgress()));
                }
            }
            if (!p.isStarted(id)) {
                p.started.put(id, now);
                events.add(new ProgressEvent.QuestStarted(id));
            }
        }
        settle(p, now, events);
        return events;
    }

    public List<ProgressEvent> completeTask(TeamProgress p, String taskId, long now) {
        List<ProgressEvent> events = new ArrayList<>();
        Task t = book.task(taskId);
        Quest q = book.questOfTask(taskId);
        if (t == null || q == null || p.isCompleted(q.id)) {
            return events;
        }
        long old = p.taskProgress(taskId);
        if (old < t.maxProgress()) {
            p.taskProgress.put(taskId, t.maxProgress());
            events.add(new ProgressEvent.TaskProgressed(taskId, old, t.maxProgress()));
        }
        if (!p.isStarted(q.id)) {
            p.started.put(q.id, now);
            events.add(new ProgressEvent.QuestStarted(q.id));
        }
        settle(p, now, events);
        return events;
    }

    public List<ProgressEvent> resetTask(TeamProgress p, String taskId) {
        List<ProgressEvent> events = new ArrayList<>();
        Task t = book.task(taskId);
        Quest q = book.questOfTask(taskId);
        if (t == null || q == null) {
            return events;
        }
        long old = p.taskProgress(taskId);
        if (p.taskProgress.remove(taskId) != null) {
            events.add(new ProgressEvent.TaskProgressed(taskId, old, 0));
        }
        if (p.completed.remove(q.id) != null) {
            p.repeatReadyAt.remove(q.id);
            events.add(new ProgressEvent.QuestReset(q.id));
        }
        return events;
    }

    private void collect(Quest q, boolean withDependencies, java.util.Set<String> out) {
        if (!out.add(q.id) || !withDependencies) {
            return;
        }
        for (String d : q.dependencies) {
            Quest dep = book.quest(d);
            if (dep != null) {
                collect(dep, true, out);
            }
        }
    }

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

    public boolean canClaim(TeamProgress p, UUID player, Reward reward) {
        Quest q = questOf(reward);
        if (q == null || !p.isCompleted(q.id) || p.isClaimed(reward.id(), player)) {
            return false;
        }
        return !p.areRewardsBlocked() || q.ignoreRewardBlocking || reward.params().getBool("ignore_reward_blocking", false);
    }

    public boolean isTeamReward(Reward reward) {
        if (reward.params().has("team_reward")) {
            return reward.params().getBool("team_reward", false);
        }
        var byType = book.settings.getCompound("default_reward_team_by_type");
        String key = "ftbquests:" + reward.type();
        if (byType.has(key)) {
            return byType.getBool(key, false);
        }
        return book.settings.getBool("default_reward_team", false);
    }

    public boolean claim(TeamProgress p, UUID player, Reward reward, long now) {
        if (!canClaim(p, player, reward)) {
            return false;
        }

        p.claimed.put(TeamProgress.claimKey(reward.id(), isTeamReward(reward) ? p.teamId() : player), now);
        Quest owner = questOf(reward);
        if (owner != null && QuestRules.repeatable(book, owner) && owner.rewards.stream().allMatch(r -> p.isClaimed(r.id(), player))) {

            p.repeatReadyAt.put(owner.id, now + owner.repeatCooldown * 1000L);
        }
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

    public boolean isLocked(TeamProgress p, Quest q) {
        return book.effectiveMode(q) != ProgressionMode.FLEXIBLE && !QuestRules.dependenciesMet(book, p, q);
    }
}
