/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.game;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;

import com.levviata.reflib.api.data.DataFiles;
import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflib.api.team.Teams;
import com.levviata.reflibquests.ReflibQuestsMod;
import com.levviata.reflibquests.api.QuestEvents;
import com.levviata.reflibquests.io.BookCodec;
import com.levviata.reflibquests.io.Gz;
import com.levviata.reflibquests.io.IdMap;
import com.levviata.reflibquests.net.QuestNet;
import com.levviata.reflibquests.io.UpstreamImporter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.model.PinStore;
import com.levviata.reflibquests.model.ProgressCodec;
import com.levviata.reflibquests.model.ProgressEngine;
import com.levviata.reflibquests.model.ProgressEvent;
import com.levviata.reflibquests.model.QuestBook;
import com.levviata.reflibquests.model.TeamProgress;

/**
 * Server-side owner of the quest book and all teams' progress: loads definitions from
 * {@code <game>/config/reflibquests/quests}, keeps progress per team in the world's
 * {@code data/reflibquests} folder, applies progress changes, posts events, notifies players, auto-claims.
 */
public final class QuestServer {

    /** Task types the game layer can currently detect. Others are kept and shown but never progress. */
    public static final Set<String> SUPPORTED_TASKS = Set.of("item", "checkmark", "xp", "kill", "advancement", "dimension", "location", "custom");

    private static QuestServer instance;

    private final MinecraftServer server;
    private final File configDir;
    private QuestBook book = new QuestBook();
    private ProgressEngine engine = new ProgressEngine(book);
    private IdMap idMap = IdMap.withBuiltin(DataNode.Compound.empty());
    private DataNode.Compound idMapRaw = DataNode.Compound.empty();
    private byte[] bookBytes = new byte[0];
    private PinStore pins = new PinStore();
    private final Map<UUID, TeamProgress> teams = new LinkedHashMap<>();
    private final Set<UUID> dirty = new HashSet<>();
    /** teams whose progress must be re-sent to their online members at the end of this tick */
    private final Set<UUID> syncDirty = new HashSet<>();
    private final RewardGranter rewards = new RewardGranter(this);
    private final TaskTracker tracker = new TaskTracker(this);
    private final List<String> inertTasks = new ArrayList<>();

    /** When a player's team changes (party joined or left), show them their new team's progress. */
    private final Teams.ChangeListener teamListener;

    private QuestServer(MinecraftServer server) {
        this.server = server;
        this.teamListener = player -> server.addScheduledTask(() -> {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(player);
            if (p != null) {
                QuestNet.sendProgress(p, Gz.pack(ProgressCodec.write(progressOf(p))));
            }
        });
        this.configDir = new File(server.getDataDirectory(), "config/reflibquests");
    }

    @Nullable
    public static QuestServer get() {
        return instance;
    }

    public static void start(MinecraftServer server) {
        instance = new QuestServer(server);
        Teams.addListener(instance.teamListener);
        instance.loadPins();
        instance.reload();
    }

    public static void stop() {
        if (instance != null) {
            Teams.removeListener(instance.teamListener);
            instance.saveAll();
            instance = null;
        }
    }

    public MinecraftServer server() {
        return server;
    }

    public QuestBook book() {
        return book;
    }

    public ProgressEngine engine() {
        return engine;
    }

    public IdMap idMap() {
        return idMap;
    }

    public RewardGranter rewards() {
        return rewards;
    }

    public TaskTracker tracker() {
        return tracker;
    }

    public List<String> inertTasks() {
        return inertTasks;
    }

    public File questFolder() {
        return new File(configDir, "quests");
    }

    /** (Re)read definitions and the id map; progress already in memory is kept. */
    public void reload() {
        Path mapFile = configDir.toPath().resolve("id_map.snbt");
        idMapRaw = DataNode.Compound.empty();
        idMap = IdMap.withBuiltin(idMapRaw);
        if (Files.exists(mapFile)) {
            try {
                idMapRaw = DataFiles.read(mapFile);
                idMap = IdMap.withBuiltin(idMapRaw);
                ReflibQuestsMod.LOGGER.info("Loaded {} id mappings", idMap.size());
            } catch (IOException ex) {
                ReflibQuestsMod.LOGGER.error("Cannot read {}: {}", mapFile, ex.getMessage());
            }
        }
        UpstreamImporter.Result r = UpstreamImporter.load(questFolder().toPath());
        book = r.book();
        engine = new ProgressEngine(book);
        ReflibQuestsMod.LOGGER.info("Loaded {} chapters, {} quests, {} tasks, {} rewards, {} reward tables from {}",
                book.chapters().size(), r.quests(), r.tasks(), r.rewards(), book.tables().size(), questFolder());
        int shown = 0;
        for (String problem : r.problems()) {
            if (shown++ < 20) {
                ReflibQuestsMod.LOGGER.warn("Quest data problem: {}", problem);
            }
        }
        if (r.problems().size() > 20) {
            ReflibQuestsMod.LOGGER.warn("... and {} more problems", r.problems().size() - 20);
        }
        tracker.rebuild();
        findInertTasks();
        bookBytes = Gz.pack(BookCodec.write(book, idMapRaw));
        pins.prune(book);
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            syncPlayer(p);
        }
    }

    // ------------------------------------------------------------ sync to clients

    /** Send the whole book and the player's team progress (on join and after a reload). */
    public void syncPlayer(EntityPlayerMP player) {
        QuestNet.sendBook(player, bookBytes);
        QuestNet.sendProgress(player, Gz.pack(ProgressCodec.write(progressOf(player))));
        QuestNet.sendPins(player, pins.pinsOf(player.getUniqueID()));
    }

    /** A client asked to pin or unpin a quest. */
    public void handleTogglePin(EntityPlayerMP player, String questId) {
        if (book.quest(questId) == null) {
            return;
        }
        if (pins.toggle(player.getUniqueID(), questId) == PinStore.Result.FULL) {
            player.sendMessage(new TextComponentString("[Quests] You can pin at most " + PinStore.MAX_PER_PLAYER + " quests."));
        }
        QuestNet.sendPins(player, pins.pinsOf(player.getUniqueID()));
    }

    private void loadPins() {
        Path file = dataDir().toPath().resolve("pins.snbt");
        if (Files.exists(file)) {
            try {
                pins = PinStore.fromData(DataFiles.read(file));
            } catch (IOException | IllegalArgumentException ex) {
                ReflibQuestsMod.LOGGER.error("Cannot read {}; starting with no pins (the file is left untouched): {}", file, ex.getMessage());
            }
        }
    }

    private void savePins() {
        try {
            DataFiles.write(dataDir().toPath().resolve("pins.snbt"), pins.toData());
            pins.clearDirty();
        } catch (IOException ex) {
            ReflibQuestsMod.LOGGER.error("Cannot save pins: {}", ex.getMessage());
        }
    }

    private void flushSync() {
        if (syncDirty.isEmpty()) {
            return;
        }
        for (UUID team : new ArrayList<>(syncDirty)) {
            byte[] packed = Gz.pack(ProgressCodec.write(progressOf(team)));
            for (EntityPlayerMP p : onlineMembers(team)) {
                QuestNet.sendProgress(p, packed);
            }
        }
        syncDirty.clear();
    }

    @Nullable
    public Reward findReward(String id) {
        for (Quest q : book.allQuests()) {
            for (Reward r : q.rewards) {
                if (r.id().equals(id)) {
                    return r;
                }
            }
        }
        return null;
    }

    /** A client asked to claim a reward. The server re-checks everything. */
    public void handleClaim(EntityPlayerMP player, String rewardId, int choice) {
        Reward reward = findReward(rewardId);
        if (reward == null) {
            return;
        }
        switch (claim(player, reward, choice, false)) {
            case NEEDS_CHOICE:
                player.sendMessage(new TextComponentString("[Quests] Pick one of the options."));
                break;
            case UNSUPPORTED:
                player.sendMessage(new TextComponentString("[Quests] This reward type is not supported yet; it stays unclaimed."));
                break;
            case FAILED:
                player.sendMessage(new TextComponentString("[Quests] You cannot claim that reward now."));
                break;
            default:
                break;
        }
    }

    /** Claim every reward the player can claim right now, except choices (those need a decision). @return how many */
    public int claimAll(EntityPlayerMP player) {
        TeamProgress team = progressOf(player);
        int claimed = 0;
        for (Quest q : book.allQuests()) {
            for (Reward r : q.rewards) {
                if (!r.type().equals("choice") && engine.canClaim(team, player.getUniqueID(), r)
                        && claim(player, r, -1, true) == RewardGranter.Result.GRANTED) {
                    claimed++;
                }
            }
        }
        return claimed;
    }

    public void handleClaimAll(EntityPlayerMP player) {
        int n = claimAll(player);
        player.sendMessage(new TextComponentString("[Quests] " + n + " reward(s) claimed."));
    }

    public void handleCheck(EntityPlayerMP player, String taskId) {
        player.sendMessage(new TextComponentString("[Quests] " + tracker.check(player, taskId)));
    }

    public void handleSubmit(EntityPlayerMP player, String taskId) {
        player.sendMessage(new TextComponentString("[Quests] " + tracker.submit(player, taskId)));
    }

    private void findInertTasks() {
        inertTasks.clear();
        for (Quest q : book.allQuests()) {
            for (Task t : q.tasks) {
                if (!SUPPORTED_TASKS.contains(t.type())) {
                    inertTasks.add(q.id + "/" + t.id() + ": unsupported task type '" + t.type() + "'");
                } else if (t.type().equals("item") && ItemResolver.resolve(t.params().get("item"), idMap) == null) {
                    inertTasks.add(q.id + "/" + t.id() + ": item '" + describe(t.params().get("item")) + "' is not known in 1.12.2 (add it to id_map.snbt)");
                }
            }
        }
        if (!inertTasks.isEmpty()) {
            ReflibQuestsMod.LOGGER.warn("{} task(s) cannot progress; first: {}", inertTasks.size(), inertTasks.get(0));
        }
    }

    private static String describe(@Nullable DataNode n) {
        if (n instanceof DataNode.Compound c) {
            return c.getString("id", "?");
        }
        return n instanceof DataNode.Str s ? s.value() : "?";
    }

    // ------------------------------------------------------------ teams and persistence

    public UUID teamIdOf(UUID player) {
        return Teams.get().teamOf(player);
    }

    public TeamProgress progressOf(UUID teamId) {
        return teams.computeIfAbsent(teamId, this::loadTeam);
    }

    public TeamProgress progressOf(EntityPlayerMP player) {
        return progressOf(teamIdOf(player.getUniqueID()));
    }

    private File dataDir() {
        File root = DimensionManager.getCurrentSaveRootDirectory();
        return new File(root == null ? new File(".") : root, "data/reflibquests");
    }

    private TeamProgress loadTeam(UUID id) {
        Path file = dataDir().toPath().resolve("team-" + id + ".snbt");
        if (Files.exists(file)) {
            try {
                return ProgressCodec.read(id, DataFiles.read(file));
            } catch (IOException | IllegalArgumentException ex) {
                ReflibQuestsMod.LOGGER.error("Cannot read progress {}; starting empty (the file is left untouched): {}", file, ex.getMessage());
            }
        }
        return new TeamProgress(id);
    }

    public void markDirty(UUID teamId) {
        dirty.add(teamId);
        syncDirty.add(teamId);
    }

    public void saveDirty() {
        for (UUID id : new ArrayList<>(dirty)) {
            save(id);
        }
        if (pins.isDirty()) {
            savePins();
        }
    }

    public void saveAll() {
        saveDirty();
    }

    private void save(UUID id) {
        TeamProgress p = teams.get(id);
        if (p == null) {
            dirty.remove(id);
            return;
        }
        try {
            DataFiles.write(dataDir().toPath().resolve("team-" + id + ".snbt"), ProgressCodec.write(p));
            dirty.remove(id);
        } catch (IOException ex) {
            ReflibQuestsMod.LOGGER.error("Cannot save progress of team {}: {}", id, ex.getMessage());
        }
    }

    // ------------------------------------------------------------ applying changes

    public List<EntityPlayerMP> onlineMembers(UUID teamId) {
        List<EntityPlayerMP> out = new ArrayList<>();
        for (UUID member : Teams.get().membersOf(teamId)) {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(member);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    public void setTaskProgress(EntityPlayerMP player, Task task, long value) {
        TeamProgress team = progressOf(player);
        apply(team, engine.setTaskProgress(team, task.id(), value, System.currentTimeMillis()));
    }

    public void addTaskProgress(EntityPlayerMP player, Task task, long delta) {
        TeamProgress team = progressOf(player);
        apply(team, engine.addTaskProgress(team, task.id(), delta, System.currentTimeMillis()));
    }

    /** Record the events of a progress operation: save later, post Forge events, tell the team, auto-claim. */
    public void apply(TeamProgress team, List<ProgressEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        markDirty(team.teamId());
        for (ProgressEvent e : events) {
            if (e instanceof ProgressEvent.TaskProgressed t) {
                MinecraftForge.EVENT_BUS.post(new QuestEvents.TaskProgress(team.teamId(), t.taskId(), t.oldValue(), t.newValue()));
            } else if (e instanceof ProgressEvent.QuestStarted s) {
                MinecraftForge.EVENT_BUS.post(new QuestEvents.QuestStarted(team.teamId(), s.questId()));
            } else if (e instanceof ProgressEvent.QuestCompleted c) {
                MinecraftForge.EVENT_BUS.post(new QuestEvents.QuestCompleted(team.teamId(), c.questId()));
                notifyTeam(team, "§a[Quests] Quest completed: " + titleOf(c.questId()));
                autoClaim(team, c.questId());
            } else if (e instanceof ProgressEvent.ChapterCompleted c) {
                MinecraftForge.EVENT_BUS.post(new QuestEvents.ChapterCompleted(team.teamId(), c.chapterId()));
                var chapter = book.chapter(c.chapterId());
                notifyTeam(team, "§b[Quests] Chapter completed: " + (chapter == null || chapter.title.isEmpty() ? c.chapterId() : chapter.title));
            }
        }
    }

    private String titleOf(String questId) {
        Quest q = book.quest(questId);
        return q == null || q.title.isEmpty() ? questId : q.title;
    }

    private void notifyTeam(TeamProgress team, String text) {
        for (EntityPlayerMP p : onlineMembers(team.teamId())) {
            p.sendMessage(new TextComponentString(text));
        }
    }

    private boolean autoClaims(Reward r) {
        String mode = r.autoClaim();
        if (mode.equals("default")) {
            mode = book.settings.getString("default_autoclaim_rewards", "disabled");
        }
        return mode.equals("enabled") || mode.equals("no_toast") || mode.equals("invisible");
    }

    private void autoClaim(TeamProgress team, String questId) {
        Quest q = book.quest(questId);
        if (q == null) {
            return;
        }
        for (Reward r : q.rewards) {
            if (autoClaims(r)) {
                for (EntityPlayerMP p : onlineMembers(team.teamId())) {
                    claim(p, r, -1, true);
                }
            }
        }
    }

    /**
     * Claim a reward for a player: checks the rules, grants it, and only then records the claim.
     *
     * @return the grant result, or {@code FAILED} if the rules do not allow a claim now
     */
    public RewardGranter.Result claim(EntityPlayerMP player, Reward reward, int choice, boolean quiet) {
        TeamProgress team = progressOf(player);
        if (!engine.canClaim(team, player.getUniqueID(), reward)) {
            return RewardGranter.Result.FAILED;
        }
        RewardGranter.Result result = rewards.grant(player, reward, choice);
        if (result == RewardGranter.Result.GRANTED) {
            engine.claim(team, player.getUniqueID(), reward, System.currentTimeMillis());
            markDirty(team.teamId());
            if (!quiet) {
                player.sendMessage(new TextComponentString("§a[Quests] Reward claimed."));
            }
        }
        return result;
    }

    /** Called every server tick. */
    public void tick(int serverTick) {
        flushSync();
        if (serverTick % 600 == 0) {
            saveDirty();
        }
        if (serverTick % 20 == 0) {
            long now = System.currentTimeMillis();
            for (TeamProgress team : new ArrayList<>(teams.values())) {
                apply(team, engine.tickRepeats(team, now));
            }
        }
    }
}
