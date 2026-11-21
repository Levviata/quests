/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.api;

import java.util.UUID;

import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * Forge events other mods can listen to (on {@code MinecraftForge.EVENT_BUS}). All are posted on the server
 * thread after the progress change has been applied; none can be cancelled.
 */
public final class QuestEvents {

    private QuestEvents() {}

    /** Base class: which team's progress changed. */
    public abstract static class Base extends Event {
        private final UUID teamId;

        protected Base(UUID teamId) {
            this.teamId = teamId;
        }

        public UUID getTeamId() {
            return teamId;
        }

        @Override
        public boolean isCancelable() {
            return false;
        }
    }

    public static class TaskProgress extends Base {
        private final String taskId;
        private final long oldValue;
        private final long newValue;

        public TaskProgress(UUID teamId, String taskId, long oldValue, long newValue) {
            super(teamId);
            this.taskId = taskId;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getTaskId() {
            return taskId;
        }

        public long getOldValue() {
            return oldValue;
        }

        public long getNewValue() {
            return newValue;
        }
    }

    public static class QuestStarted extends Base {
        private final String questId;

        public QuestStarted(UUID teamId, String questId) {
            super(teamId);
            this.questId = questId;
        }

        public String getQuestId() {
            return questId;
        }
    }

    public static class QuestCompleted extends Base {
        private final String questId;

        public QuestCompleted(UUID teamId, String questId) {
            super(teamId);
            this.questId = questId;
        }

        public String getQuestId() {
            return questId;
        }
    }

    public static class ChapterCompleted extends Base {
        private final String chapterId;

        public ChapterCompleted(UUID teamId, String chapterId) {
            super(teamId);
            this.chapterId = chapterId;
        }

        public String getChapterId() {
            return chapterId;
        }
    }
}
