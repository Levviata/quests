/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TeamProgress {

    private final UUID teamId;
    final Map<String, Long> taskProgress = new LinkedHashMap<>();
    final Map<String, Long> started = new LinkedHashMap<>();
    final Map<String, Long> completed = new LinkedHashMap<>();
    final Map<String, Integer> completionCount = new LinkedHashMap<>();
    final Map<String, Long> repeatReadyAt = new LinkedHashMap<>();

    final Map<String, Long> claimed = new LinkedHashMap<>();
    private boolean locked;
    private boolean rewardsBlocked;

    public TeamProgress(UUID teamId) {
        this.teamId = teamId;
    }

    public UUID teamId() {
        return teamId;
    }

    public long taskProgress(String taskId) {
        return taskProgress.getOrDefault(taskId, 0L);
    }

    public boolean isStarted(String questId) {
        return started.containsKey(questId);
    }

    public boolean isCompleted(String questId) {
        return completed.containsKey(questId);
    }

    public Long startedAt(String questId) {
        return started.get(questId);
    }

    public Long completedAt(String questId) {
        return completed.get(questId);
    }

    public int completionCount(String questId) {
        return completionCount.getOrDefault(questId, 0);
    }

    public boolean isClaimed(String rewardId, UUID player) {
        return claimed.containsKey(claimKey(rewardId, player)) || claimed.containsKey(claimKey(rewardId, teamId));
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean areRewardsBlocked() {
        return rewardsBlocked;
    }

    public void setRewardsBlocked(boolean rewardsBlocked) {
        this.rewardsBlocked = rewardsBlocked;
    }

    static String claimKey(String rewardId, UUID player) {
        return rewardId + "@" + player;
    }
}
