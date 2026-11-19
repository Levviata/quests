/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

/** What changed as a result of a progress operation; the game layer turns these into Forge events and sync. */
public sealed interface ProgressEvent {

    record TaskProgressed(String taskId, long oldValue, long newValue) implements ProgressEvent {}

    record QuestStarted(String questId) implements ProgressEvent {}

    record QuestCompleted(String questId) implements ProgressEvent {}

    record QuestReset(String questId) implements ProgressEvent {}

    record ChapterCompleted(String chapterId) implements ProgressEvent {}
}
