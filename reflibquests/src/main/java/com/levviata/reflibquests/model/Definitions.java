/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.levviata.reflib.api.data.DataNode;

public final class Definitions {

    private Definitions() {}

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

    public record Task(String id, String type, DataNode.Compound params) {
        public Task {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("task id must not be blank");
            }
        }

        public static long itemCount(DataNode.Compound params) {
            if (params.has("count")) {
                return Math.max(1, params.getLong("count", 1));
            }
            return params.get("item") instanceof DataNode.Compound spec ? Math.max(1, spec.getLong("count", 1)) : 1;
        }

        public long maxProgress() {
            switch (type) {
                case "item":
                    return itemCount(params);
                case "fluid":
                    return params.get("fluid") instanceof DataNode.Compound f ? Math.max(1, f.getLong("amount", 1000)) : 1000;
                case "xp":
                case "kill":
                case "stat":
                case "energy":
                case "forge_energy":
                    return Math.max(1, params.getLong("value", 1));
                default:
                    return 1;
            }
        }

        public boolean optionalTask() {
            return params.getBool("optional_task", false);
        }

        /** Forge Energy: most FE taken per hand-in (FTB Quests' "Max Input Rate", default 1000). */
        public long maxInput() {
            return Math.max(1L, params.getLong("max_input", 1000L));
        }

        /** Forge Energy: a hand-in drains as much as the battery can give instead of one Max Input Rate chunk. */
        public boolean drainMax() {
            return params.getBool("drain_max", false);
        }
    }

    public record Reward(String id, String type, DataNode.Compound params) {
        public Reward {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("reward id must not be blank");
            }
        }

        public String autoClaim() {
            return params.getString("auto", "default");
        }
    }

    public record WeightedReward(float weight, Reward reward) {}

    public static final class RewardTable {
        public final String id;
        public String filename = "";
        public String title = "";
        public int order;
        public float emptyWeight;
        public int lootSize;
        public final List<WeightedReward> entries = new ArrayList<>();

        public DataNode.Compound raw = DataNode.Compound.empty();

        public RewardTable(String id) {
            this.id = id;
        }
    }

    public static final class ChapterGroup {
        public final String id;
        public String title = "";
        public DataNode.Compound raw = DataNode.Compound.empty();

        public ChapterGroup(String id) {
            this.id = id;
        }
    }

    public record QuestLink(String id, String linkedQuest, double x, double y, DataNode.Compound raw) {}

    public record ChapterImage(String id, DataNode.Compound raw) {}

    public static final class Quest {
        public final String id;
        public String title = "";
        public String subtitle = "";
        public List<String> description = new ArrayList<>();
        public double x;
        public double y;
        public String shape = "";
        public double size = 1.0;

        public double z;
        public DataNode.Compound icon = DataNode.Compound.empty();
        public final List<Task> tasks = new ArrayList<>();
        public final List<Reward> rewards = new ArrayList<>();
        public final List<String> dependencies = new ArrayList<>();

        public java.util.Map<String, List<Double>> depControlPts = new java.util.LinkedHashMap<>();
        public DependencyMode dependencyMode = DependencyMode.ALL_COMPLETED;

        public int minRequiredDependencies;

        public int maxCompletableDependents;

        public List<String> tags = new ArrayList<>();

        public double iconScale = 1.0;

        public String guidePage = "";

        public String preset = "";
        public boolean optional;

        public boolean invisible;

        public int invisibleUntilTasks;
        public int repeatCooldown;
        public ProgressionMode progressionMode = ProgressionMode.DEFAULT;
        public Tristate canRepeat = Tristate.DEFAULT;
        public Tristate requireSequentialTasks = Tristate.DEFAULT;
        public Tristate hideDetailsUntilStartable = Tristate.DEFAULT;
        public Tristate hideUntilDepsVisible = Tristate.DEFAULT;
        public Tristate hideUntilDepsComplete = Tristate.DEFAULT;
        public boolean ignoreRewardBlocking;

        public boolean disableToast;

        public boolean hideLockIcon;

        public Tristate hideTextUntilComplete = Tristate.DEFAULT;

        public Tristate disableJei = Tristate.DEFAULT;

        public boolean orTasks;

        public Tristate hideDependencyLines = Tristate.DEFAULT;
        public Tristate hideDependentLines = Tristate.DEFAULT;

        public DataNode.Compound raw = DataNode.Compound.empty();

        public Quest(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("quest id must not be blank");
            }
            this.id = id;
        }
    }

    public static final class Chapter {
        public final String id;
        public String filename = "";
        public String title = "";
        public List<String> subtitle = new ArrayList<>();
        public DataNode.Compound icon = DataNode.Compound.empty();

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

        public String autofocusId = "";

        public boolean disableToast;

        public boolean hideTextUntilComplete;

        public Tristate defaultConsumeItems = Tristate.DEFAULT;

        public double defaultQuestSize = 1.0;

        public int minWindowWidth;
        public List<String> tags = new ArrayList<>();

        public String defaultQuestShape = "";

        public String preset = "";
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
