/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.DependencyMode;
import com.levviata.reflibquests.model.Definitions.ProgressionMode;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.model.Definitions.Tristate;

class ProgressTest {

    private QuestBook book;
    private Chapter chapter;
    private ProgressEngine engine;
    private TeamProgress team;
    private final UUID player = UUID.randomUUID();

    private static Task item(String id, int count) {
        return new Task(id, "item", DataNode.Compound.empty().with("count", count));
    }

    private static Task check(String id) {
        return new Task(id, "checkmark", DataNode.Compound.empty());
    }

    private Quest quest(String id, Task... tasks) {
        Quest q = new Quest(id);
        q.tasks.addAll(List.of(tasks));
        chapter.quests.add(q);
        return q;
    }

    @BeforeEach
    void setUp() {
        book = new QuestBook();
        chapter = new Chapter("C1");
        book.chapters().add(chapter);
        team = new TeamProgress(UUID.randomUUID());
    }

    private void ready() {
        book.reindex();
        engine = new ProgressEngine(book);
    }

    @Test
    void maxProgressByType() {
        assertEquals(5, item("t", 5).maxProgress());
        assertEquals(1, check("c").maxProgress());
        assertEquals(30, new Task("k", "kill", DataNode.Compound.empty().with("value", 30L)).maxProgress());
        assertEquals(1, new Task("u", "unknown_type", DataNode.Compound.empty()).maxProgress());
    }

    @Test
    void taskProgressStartsAndCompletesQuest() {
        quest("Q1", item("T1", 3));
        ready();
        List<ProgressEvent> e1 = engine.setTaskProgress(team, "T1", 1, 100);
        assertTrue(e1.contains(new ProgressEvent.QuestStarted("Q1")));
        assertTrue(team.isStarted("Q1"));
        assertFalse(team.isCompleted("Q1"));
        assertEquals(33, QuestRules.percent(team, book.quest("Q1")));
        List<ProgressEvent> e2 = engine.addTaskProgress(team, "T1", 5, 200);
        assertEquals(3, team.taskProgress("T1"), "clamped to the task maximum");
        assertTrue(e2.contains(new ProgressEvent.QuestCompleted("Q1")));
        assertTrue(e2.contains(new ProgressEvent.ChapterCompleted("C1")));
        assertEquals(200L, team.completedAt("Q1"));
        assertEquals(100, QuestRules.percent(team, book.quest("Q1")));
    }

    @Test
    void progressNeverDecreasesAndNoDuplicateEvents() {
        quest("Q1", item("T1", 3));
        ready();
        engine.setTaskProgress(team, "T1", 2, 1);
        assertTrue(engine.setTaskProgress(team, "T1", 1, 2).isEmpty());
        assertEquals(2, team.taskProgress("T1"));
        engine.setTaskProgress(team, "T1", 3, 3);
        assertTrue(engine.setTaskProgress(team, "T1", 3, 4).isEmpty());
    }

    @Test
    void unknownTaskIsIgnored() {
        ready();
        assertTrue(engine.setTaskProgress(team, "nope", 1, 1).isEmpty());
    }

    @Test
    void multipleTasksAllRequiredOptionalNot() {
        Task optional = new Task("T3", "checkmark", DataNode.Compound.empty().with("optional_task", true));
        quest("Q1", check("T1"), check("T2"), optional);
        ready();
        engine.setTaskProgress(team, "T1", 1, 1);
        assertFalse(team.isCompleted("Q1"));
        engine.setTaskProgress(team, "T2", 1, 2);
        assertTrue(team.isCompleted("Q1"), "optional task is not required");
    }

    @Test
    void sequentialTasksOnlyAllowTheNextOne() {
        Quest q = quest("Q1", check("T1"), check("T2"));
        q.requireSequentialTasks = Tristate.TRUE;
        ready();
        assertTrue(engine.setTaskProgress(team, "T2", 1, 1).isEmpty(), "T1 must be done first");
        engine.setTaskProgress(team, "T1", 1, 2);
        assertFalse(engine.setTaskProgress(team, "T2", 1, 3).isEmpty());
        assertTrue(team.isCompleted("Q1"));
    }

    @Test
    void chapterDefaultAppliesWhenQuestIsDefault() {
        chapter.requireSequentialTasks = true;
        quest("Q1", check("T1"), check("T2"));
        Quest q2 = quest("Q2", check("T3"), check("T4"));
        q2.requireSequentialTasks = Tristate.FALSE;
        ready();
        assertTrue(engine.setTaskProgress(team, "T2", 1, 1).isEmpty());
        assertFalse(engine.setTaskProgress(team, "T4", 1, 1).isEmpty(), "quest overrides chapter default");
    }

    @Test
    void linearDependenciesBlockProgress() {
        quest("A", check("TA"));
        Quest b = quest("B", check("TB"));
        b.dependencies.add("A");
        ready();
        assertTrue(engine.setTaskProgress(team, "TB", 1, 1).isEmpty());
        engine.setTaskProgress(team, "TA", 1, 2);
        assertFalse(engine.setTaskProgress(team, "TB", 1, 3).isEmpty());
    }

    @Test
    void flexibleQuestProgressesButCompletesOnlyAfterDependencies() {
        quest("A", check("TA"));
        Quest b = quest("B", check("TB"));
        b.dependencies.add("A");
        b.progressionMode = ProgressionMode.FLEXIBLE;
        ready();
        engine.setTaskProgress(team, "TB", 1, 1);
        assertEquals(1, team.taskProgress("TB"));
        assertFalse(team.isCompleted("B"));
        List<ProgressEvent> e = engine.setTaskProgress(team, "TA", 1, 2);
        assertTrue(team.isCompleted("B"), "waiting quest completes once its dependency does");
        assertTrue(e.contains(new ProgressEvent.QuestCompleted("A")));
        assertTrue(e.contains(new ProgressEvent.QuestCompleted("B")));
    }

    @Test
    void dependencyModes() {
        quest("A", check("TA"));
        quest("B", check("TB"));
        Quest c = quest("C", check("TC"));
        c.dependencies.addAll(List.of("A", "B"));
        ready();

        engine.setTaskProgress(team, "TA", 1, 1);
        assertFalse(QuestRules.dependenciesMet(book, team, c));
        engine.setTaskProgress(team, "TB", 1, 2);
        assertTrue(QuestRules.dependenciesMet(book, team, c));

        TeamProgress t2 = new TeamProgress(UUID.randomUUID());
        c.dependencyMode = DependencyMode.ONE_COMPLETED;
        assertFalse(QuestRules.dependenciesMet(book, t2, c));
        engine.setTaskProgress(t2, "TA", 1, 1);
        assertTrue(QuestRules.dependenciesMet(book, t2, c));

        TeamProgress t3 = new TeamProgress(UUID.randomUUID());
        c.dependencyMode = DependencyMode.ALL_STARTED;
        quest("D", item("TD", 2));
        book.reindex();
        engine.setTaskProgress(t3, "TA", 1, 1);
        assertFalse(QuestRules.dependenciesMet(book, t3, c), "B has not started");
        Quest bq = book.quest("B");
        bq.tasks.set(0, item("TB", 2));
        book.reindex();
        engine.setTaskProgress(t3, "TB", 1, 2);
        assertTrue(QuestRules.dependenciesMet(book, t3, c), "B started but not completed");

        TeamProgress t4 = new TeamProgress(UUID.randomUUID());
        c.dependencyMode = DependencyMode.ALL_COMPLETED;
        c.minRequiredDependencies = 1;
        engine.setTaskProgress(t4, "TA", 1, 1);
        assertTrue(QuestRules.dependenciesMet(book, t4, c), "min required = 1");
    }

    @Test
    void optionalDependenciesStillBlockButMissingOnesDoNot() {
        Quest a = quest("A", check("TA"));
        a.optional = true;
        Quest b = quest("B", check("TB"));
        b.dependencies.addAll(List.of("A", "MISSING"));
        ready();
        assertFalse(QuestRules.dependenciesMet(book, team, b), "an optional quest still has to be completed first");
        engine.setTaskProgress(team, "TA", 1, 1000);
        assertTrue(QuestRules.dependenciesMet(book, team, b));
    }

    @Test
    void visibilityRules() {
        quest("A", check("TA"));
        Quest b = quest("B", check("TB"));
        b.dependencies.add("A");
        b.hideUntilDepsVisible = Tristate.TRUE;
        ready();
        assertTrue(QuestRules.isVisible(book, team, book.quest("A")));
        book.quest("A").invisible = true;
        assertFalse(QuestRules.isVisible(book, team, book.quest("A")));
        assertFalse(QuestRules.isVisible(book, team, book.quest("B")), "dependency not visible");
        engine.forceComplete(team, "A", 1);
        assertTrue(QuestRules.isVisible(book, team, book.quest("A")), "completed quests always show");
        assertTrue(QuestRules.isVisible(book, team, book.quest("B")));

        Quest c = quest("C", check("TC"), check("TC2"));
        c.invisibleUntilTasks = 1;
        book.reindex();
        assertFalse(QuestRules.isVisible(book, team, c));
        engine.setTaskProgress(team, "TC", 1, 2);
        assertTrue(QuestRules.isVisible(book, team, c));

        Quest d = quest("D", check("TD"));
        d.dependencies.add("Z");
        d.hideUntilDepsComplete = Tristate.TRUE;
        quest("Z", check("TZ"));
        book.reindex();
        assertFalse(QuestRules.isVisible(book, team, d));
        engine.forceComplete(team, "Z", 3);
        assertTrue(QuestRules.isVisible(book, team, d));
    }

    @Test
    void dependantOfHiddenQuestIsHidden() {
        quest("H", check("TH")).invisible = true;
        Quest s = quest("S", check("TS"));
        s.dependencies.add("H");
        Quest t = quest("T", check("TT"));
        t.dependencies.addAll(List.of("H", "S"));
        ready();
        assertFalse(QuestRules.isVisible(book, team, s), "its only dependency is hidden");
        assertFalse(QuestRules.isVisible(book, team, t), "every dependency is hidden");
        engine.forceComplete(team, "H", 1);
        assertTrue(QuestRules.isVisible(book, team, s));
    }

    @Test
    void forceCompleteWaitsForDependenciesUnlessAskedToCompleteThemToo() {
        quest("A", check("TA"));
        Quest b = quest("B", check("TB"));
        b.dependencies.add("A");
        Quest c = quest("C", check("TC"));
        c.dependencies.add("B");
        ready();
        engine.forceComplete(team, "C", 1);
        assertFalse(team.isCompleted("C"), "a locked quest does not complete until its dependencies are resolved");
        assertEquals(1, team.taskProgress("TC"), "its tasks are done, though");
        engine.forceComplete(team, "A", 2);
        assertTrue(team.isCompleted("A"));
        assertFalse(team.isCompleted("C"));
        engine.forceComplete(team, "B", 3);
        assertTrue(team.isCompleted("B"));
        assertTrue(team.isCompleted("C"), "it completes by itself once the last dependency is done");
    }

    @Test
    void forceCompleteWithDependenciesCompletesTheWholeChain() {
        quest("A", check("TA"));
        Quest b = quest("B", check("TB"));
        b.dependencies.add("A");
        Quest c = quest("C", check("TC"));
        c.dependencies.add("B");
        ready();
        engine.forceComplete(team, "C", 1, true);
        assertTrue(team.isCompleted("A"));
        assertTrue(team.isCompleted("B"));
        assertTrue(team.isCompleted("C"));
    }

    @Test
    void aSingleTaskCanBeCompletedAndResetByAnAdmin() {
        Quest a = quest("A", check("TA1"), check("TA2"));
        ready();
        engine.completeTask(team, "TA1", 1);
        assertEquals(1, team.taskProgress("TA1"));
        assertFalse(team.isCompleted("A"), "the other task is still open");
        engine.completeTask(team, "TA2", 2);
        assertTrue(team.isCompleted("A"), "the last task completes the quest");
        engine.resetTask(team, "TA2");
        assertEquals(0, team.taskProgress("TA2"));
        assertFalse(team.isCompleted("A"), "the quest is open again");
        assertEquals(1, team.taskProgress("TA1"), "the other task keeps its progress");
    }

    @Test
    void dependencyLoopDoesNotHang() {
        Quest a = quest("A", check("TA"));
        Quest b = quest("B", check("TB"));
        a.dependencies.add("B");
        b.dependencies.add("A");
        a.hideUntilDepsVisible = Tristate.TRUE;
        b.hideUntilDepsVisible = Tristate.TRUE;
        ready();
        assertFalse(QuestRules.isVisible(book, team, a));
    }

    @Test
    void detailsHiddenUntilStartable() {
        quest("A", check("TA"));
        Quest b = quest("B", check("TB"));
        b.dependencies.add("A");
        b.hideDetailsUntilStartable = Tristate.TRUE;
        ready();
        assertFalse(QuestRules.detailsVisible(book, team, b));
        engine.forceComplete(team, "A", 1);
        assertTrue(QuestRules.detailsVisible(book, team, b));
    }

    @Test
    void maxCompletableDependentsMakesTheOtherBranchesUnavailable() {
        Quest root = quest("ROOT", check("TR"));
        root.maxCompletableDependents = 1;
        Quest a = quest("A", check("TA"));
        Quest b = quest("B", check("TB"));
        a.dependencies.add("ROOT");
        b.dependencies.add("ROOT");
        ready();
        engine.forceComplete(team, "ROOT", 1);
        assertTrue(QuestRules.dependenciesMet(book, team, a));
        assertTrue(QuestRules.dependenciesMet(book, team, b));
        engine.forceComplete(team, "A", 2);
        assertFalse(QuestRules.dependenciesMet(book, team, b), "one dependent is done: the other branch is closed");
        assertTrue(team.isCompleted("A"));
    }

    @Test
    void perTeamRewardByTypeSettingDecidesWhenTheRewardSaysNothing() {
        Quest q = quest("Q1", check("T1"));
        Reward item = new Reward("R1", "item", DataNode.Compound.empty());
        Reward cmd = new Reward("R2", "command", DataNode.Compound.empty());
        Reward own = new Reward("R3", "item", DataNode.Compound.empty().with("team_reward", false));
        q.rewards.add(item);
        q.rewards.add(cmd);
        q.rewards.add(own);

        book.settings = DataNode.Compound.empty().with("default_reward_team", true).with("default_reward_team_by_type",
                DataNode.Compound.empty().with("ftbquests:item", true).with("ftbquests:command", false));
        ready();
        assertTrue(engine.isTeamReward(item));
        assertFalse(engine.isTeamReward(cmd), "the type overrides the book default");
        assertFalse(engine.isTeamReward(own), "the reward's own setting wins");
        book.settings = DataNode.Compound.empty().with("default_reward_team", true);
        assertTrue(engine.isTeamReward(cmd), "without a type entry the book default applies");
    }

    @Test
    void teamRewardIsClaimedOnceForTheWholeTeam() {
        Quest q = quest("Q1", check("T1"));
        Reward r = new Reward("R1", "xp", DataNode.Compound.empty().with("xp", 10).with("team_reward", true));
        q.rewards.add(r);
        ready();
        engine.setTaskProgress(team, "T1", 1, 1);
        UUID other = UUID.randomUUID();
        assertTrue(engine.claim(team, player, r, 2));
        assertFalse(engine.canClaim(team, other, r), "a team reward is gone for the other members once one claimed it");
        assertTrue(team.isClaimed("R1", other));
    }

    @Test
    void rewardsClaimOncePerPlayerAndRespectBlocking() {
        Quest q = quest("Q1", check("T1"));
        Reward r = new Reward("R1", "xp", DataNode.Compound.empty().with("xp", 10));
        q.rewards.add(r);
        ready();
        assertFalse(engine.canClaim(team, player, r), "quest not completed yet");
        engine.setTaskProgress(team, "T1", 1, 1);
        assertTrue(engine.claim(team, player, r, 2));
        assertFalse(engine.claim(team, player, r, 3), "only once");
        UUID other = UUID.randomUUID();
        assertTrue(engine.canClaim(team, other, r), "claims are per player");
        team.setRewardsBlocked(true);
        assertFalse(engine.canClaim(team, other, r));
        q.ignoreRewardBlocking = true;
        assertTrue(engine.canClaim(team, other, r));
        assertEquals(List.of(r), engine.unclaimedRewards(team, other, q));
    }

    @Test
    void repeatableQuestResetsAfterCooldownAndKeepsCount() {
        Quest q = quest("Q1", check("T1"));
        q.canRepeat = Tristate.TRUE;
        q.repeatCooldown = 1;
        Reward r = new Reward("R1", "xp", DataNode.Compound.empty().with("xp", 1));
        q.rewards.add(r);
        ready();
        engine.setTaskProgress(team, "T1", 1, 1000);
        assertTrue(engine.tickRepeats(team, 5000).isEmpty(), "the cooldown only starts once all rewards are claimed");
        engine.claim(team, player, r, 1001);
        assertEquals(1, team.completionCount("Q1"));
        assertTrue(engine.tickRepeats(team, 1500).isEmpty(), "cooldown not over");
        List<ProgressEvent> e = engine.tickRepeats(team, 2002);
        assertTrue(e.contains(new ProgressEvent.QuestReset("Q1")));
        assertFalse(team.isCompleted("Q1"));
        assertEquals(0, team.taskProgress("T1"));
        assertEquals(1, team.completionCount("Q1"), "completion count survives the reset");
        assertTrue(engine.canClaim(team, player, r) == false, "not completed again yet");
        engine.setTaskProgress(team, "T1", 1, 2100);
        assertEquals(2, team.completionCount("Q1"));
        assertTrue(engine.canClaim(team, player, r), "reward can be claimed again after a repeat");
    }

    @Test
    void lockedTeamCannotProgress() {
        quest("Q1", check("T1"));
        ready();
        team.setLocked(true);
        assertTrue(engine.setTaskProgress(team, "T1", 1, 1).isEmpty());
        team.setLocked(false);
        assertFalse(engine.setTaskProgress(team, "T1", 1, 2).isEmpty());
    }

    @Test
    void resetQuestClearsEverything() {
        Quest q = quest("Q1", check("T1"));
        Reward r = new Reward("R1", "xp", DataNode.Compound.empty().with("xp", 1));
        q.rewards.add(r);
        ready();
        engine.setTaskProgress(team, "T1", 1, 1);
        engine.claim(team, player, r, 2);
        assertEquals(List.of(new ProgressEvent.QuestReset("Q1")), engine.resetQuest(team, "Q1"));
        assertFalse(team.isStarted("Q1"));
        assertFalse(team.isClaimed("R1", player));
        assertTrue(engine.resetQuest(team, "Q1").isEmpty(), "nothing left to reset");
    }

    @Test
    void chapterCompletionIgnoresOptionalQuests() {
        quest("A", check("TA"));
        Quest opt = quest("B", check("TB"));
        opt.optional = true;
        ready();
        engine.setTaskProgress(team, "TA", 1, 1);
        assertTrue(QuestRules.chapterCompleted(team, chapter));
    }
}
