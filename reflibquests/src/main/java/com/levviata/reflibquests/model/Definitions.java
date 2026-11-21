/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.levviata.reflib.api.data.DataNode;

/**
 * The building blocks of a quest book. Tasks and rewards are data-only: their {@code type} string is
 * interpreted by the game layer (task checkers, reward granters), so this package has no Minecraft dependency
 * and can be unit-tested directly. Every object keeps its original data ({@code raw}) so that values this mod
 * does not interpret survive import and export unchanged.
 */
public final class Definitions {

    private Definitions() {}

    /** Three-state option: {@code DEFAULT} means "use the parent's setting" (chapter, then book). */
    public enum Tristate {
        DEFAULT, TRUE, FALSE;

        public static Tristate read(DataNode.Compound c, String key) {
            if (!c.has(key)) {
                return DEFAULT;
            }
            return c.getBool(key, false) ? TRUE : FALSE;
        }

        public boolean resolve(boolean parent) {
            return this == DEFAULT ? parent : this == TRUE;
        }
    }

    /** How a quest's dependencies are evaluated. */
    public enum DependencyMode {
        ALL_COMPLETED, ONE_COMPLETED, ALL_STARTED, ONE_STARTED;

        public boolean requiresAll() {
            return this == ALL_COMPLETED || this == ALL_STARTED;
        }

        public boolean requiresCompletion() {
            return this == ALL_COMPLETED || this == ONE_COMPLETED;
        }

        public static DependencyMode parse(String text) {
            try {
                return valueOf(text.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException ex) {
                return ALL_COMPLETED;
            }
        }
    }

    /**
     * {@code LINEAR}: a quest's tasks only progress once its dependencies are met. {@code FLEXIBLE}: tasks may
     * progress at any time, but the quest only completes once its dependencies are met. (Our reading of the
     * documented "in order / any order"; to be confirmed against real packs.)
     */
    public enum ProgressionMode {
        DEFAULT, LINEAR, FLEXIBLE;

        public static ProgressionMode parse(String text, ProgressionMode fallback) {
            try {
                return valueOf(text.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException ex) {
                return fallback;
            }
        }
    }

    /**
     * One thing the player must do. {@code params} holds all type-specific data exactly as read.
     * {@link #maxProgress()} is the value at which the task counts as done.
     */
    public record Task(String id, String type, DataNode.Compound params) {
        public Task {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("task id must not be blank");
            }
        }

        public long maxProgress() {
            switch (type) {
                case "item":
                    return Math.max(1, params.getLong("count", 1));
                case "xp":
                case "kill":
                case "stat":
                case "energy":
                    return Math.max(1, params.getLong("value", 1));
                default:
                    return 1;
            }
        }

        public boolean optionalTask() {
            return params.getBool("optional_task", false);
        }
    }

    /** One thing granted on completion. */
    public record Reward(String id, String type, DataNode.Compound params) {
        public Reward {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("reward id must not be blank");
            }
        }

        /** @return default, disabled, enabled, no_toast or invisible */
        public String autoClaim() {
            return params.getString("auto", "default");
        }
    }

    /** A reward with a relative weight inside a {@link RewardTable}. */
    public record WeightedReward(float weight, Reward reward) {}

    /** A named pool of rewards, used by choice/random/all-table/loot reward types. */
    public static final class RewardTable {
        public final String id;
        public String filename = "";
        public String title = "";
        public int order;
        public float emptyWeight;
        public int lootSize;
        public final List<WeightedReward> entries = new ArrayList<>();
        /** Original table data (without the rewards array). */
        public DataNode.Compound raw = DataNode.Compound.empty();

        public RewardTable(String id) {
            this.id = id;
        }
    }

    /** A named group of chapters. */
    public static final class ChapterGroup {
        public final String id;
        public String title = "";
        public DataNode.Compound raw = DataNode.Compound.empty();

        public ChapterGroup(String id) {
            this.id = id;
        }
    }

    /** A second placement of an existing quest on another chapter's map. */
    public record QuestLink(String id, String linkedQuest, double x, double y, DataNode.Compound raw) {}

    /** A decorative image on a chapter's map. Data kept as read; interpretation is the client's job. */
    public record ChapterImage(String id, DataNode.Compound raw) {}

    /** A quest. Mutable so that the editor can change it. */
    public static final class Quest {
        public final String id;
        public String title = "";
        public String subtitle = "";
        public List<String> description = new ArrayList<>();
        public double x;
        public double y;
        public String shape = "";
        public double size = 1.0;
        public DataNode.Compound icon = DataNode.Compound.empty();
        public final List<Task> tasks = new ArrayList<>();
        public final List<Reward> rewards = new ArrayList<>();
        public final List<String> dependencies = new ArrayList<>();
        public DependencyMode dependencyMode = DependencyMode.ALL_COMPLETED;
        /** If above 0, this many dependencies suffice instead of the mode's default. */
        public int minRequiredDependencies;
        public boolean optional;
        /** Invisible until completed. */
        public boolean invisible;
        /** If above 0, invisible until this many tasks are completed. */
        public int invisibleUntilTasks;
        public int repeatCooldown;
        public ProgressionMode progressionMode = ProgressionMode.DEFAULT;
        public Tristate canRepeat = Tristate.DEFAULT;
        public Tristate requireSequentialTasks = Tristate.DEFAULT;
        public Tristate hideDetailsUntilStartable = Tristate.DEFAULT;
        public Tristate hideUntilDepsVisible = Tristate.DEFAULT;
        public Tristate hideUntilDepsComplete = Tristate.DEFAULT;
        public boolean ignoreRewardBlocking;
        /** No pop-up when this quest is completed. */
        public boolean disableToast;
        /** Hide the lock icon that shows while the quest waits for its dependencies. */
        public boolean hideLockIcon;
        /** The description is not shown until the quest is completed (outside edit mode). */
        public Tristate hideTextUntilComplete = Tristate.DEFAULT;
        /** No recipe view when a task or reward of this quest is clicked. */
        public Tristate disableJei = Tristate.DEFAULT;
        /** The quest is complete as soon as any one of its tasks is done (older quest files call this "or_tasks"). */
        public boolean orTasks;
        /** Hide the lines to this quest's dependencies / from this quest to its dependants. */
        public Tristate hideDependencyLines = Tristate.DEFAULT;
        public Tristate hideDependentLines = Tristate.DEFAULT;
        /** Original quest data without the tasks and rewards arrays. */
        public DataNode.Compound raw = DataNode.Compound.empty();

        public Quest(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("quest id must not be blank");
            }
            this.id = id;
        }
    }

    /** A chapter holding quests, links and images. */
    public static final class Chapter {
        public final String id;
        public String filename = "";
        public String title = "";
        public List<String> subtitle = new ArrayList<>();
        public DataNode.Compound icon = DataNode.Compound.empty();
        /** Id of the {@link ChapterGroup}, or empty for the default group. */
        public String groupId = "";
        public int order;
        public boolean alwaysInvisible;
        public ProgressionMode progressionMode = ProgressionMode.DEFAULT;
        public boolean requireSequentialTasks;
        public boolean hideQuestDetailsUntilStartable;
        public boolean hideQuestUntilDepsVisible;
        public boolean hideQuestUntilDepsComplete;
        public boolean defaultRepeatable;
        public boolean defaultHideDependencyLines;
        /** The quest the chapter opens centred on (empty: none). */
        public String autofocusId = "";
        /** Shape for quests that do not name one (empty: the book's default). */
        public String defaultQuestShape = "";
        public final List<Quest> quests = new ArrayList<>();
        public final List<QuestLink> links = new ArrayList<>();
        public final List<ChapterImage> images = new ArrayList<>();
        public DataNode.Compound raw = DataNode.Compound.empty();

        public Chapter(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("chapter id must not be blank");
            }
            this.id = id;
        }
    }
}
