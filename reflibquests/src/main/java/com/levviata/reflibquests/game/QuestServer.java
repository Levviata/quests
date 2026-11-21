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
import com.levviata.reflibquests.io.UpstreamExporter;
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
    public static final Set<String> SUPPORTED_TASKS = Set.of("item", "checkmark", "xp", "kill", "advancement", "dimension", "location", "custom",
            "biome", "structure", "observation");

    private static QuestServer instance;

    private final MinecraftServer server;
    private final File configDir;
    private QuestBook book = new QuestBook();
    private ProgressEngine engine = new ProgressEngine(book);
    private IdMap idMap = IdMap.withBuiltin(DataNode.Compound.empty());
    private DataNode.Compound idMapRaw = DataNode.Compound.empty();
    private byte[] bookBytes = new byte[0];
    private PinStore pins = new PinStore();
    private boolean bookDirty;
    private long resyncAt;
    private long resyncFirstAt;
    private boolean backedUp;
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

    /**
     * Where the quest files are: {@code config/reflibquests/quests/}, or the config folder itself when the files
     * were dropped there (a data file plus a chapters folder) and the {@code quests/} subfolder has no chapters.
     */
    public File questFolder() {
        File nested = new File(configDir, "quests");
        if (!hasChapters(nested) && hasChapters(configDir)) {
            return configDir;
        }
        return nested;
    }

    private static boolean hasChapters(File folder) {
        String[] names = new File(folder, "chapters").list();
        return names != null && names.length > 0;
    }

    /** (Re)read definitions and the id map; progress already in memory is kept. */
    public void reload() {
        waitForExport();
        history.clear(); // the book is about to be read again; copies of the old one no longer fit
        if (bookDirty) {
            exportBook(); // never throw away edits that were not saved yet
        }
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
        if (bookBytesStale) {
            bookBytes = Gz.pack(BookCodec.write(book, idMapRaw));
            bookBytesStale = false;
        }
        QuestNet.sendBook(player, bookBytes);
        QuestNet.sendProgress(player, Gz.pack(ProgressCodec.write(progressOf(player))));
        QuestNet.sendPins(player, pins.pinsOf(player.getUniqueID()));
        QuestNet.sendPerms(player, canEdit(player));
        sendHistory(player);
    }

    public boolean canEdit(EntityPlayerMP player) {
        return player.canUseCommand(2, "rquests");
    }

    /** A client asked for an edit. Needs permission level 2; the edit is validated, saved and sent to everyone. */
    public void handleEdit(EntityPlayerMP player, String op, String argsText) {
        if (!canEdit(player)) {
            player.sendMessage(new TextComponentString("[Quests] You need permission level 2 to edit quests."));
            return;
        }
        if (exporter != null) {
            deferredEdits.add(() -> handleEdit(player, op, argsText)); // a save is running: apply this right after it
            return;
        }
        com.levviata.reflib.api.data.DataNode.Compound args;
        try {
            args = com.levviata.reflib.api.data.Snbt.parse(argsText);
        } catch (RuntimeException ex) {
            player.sendMessage(new TextComponentString("[Quests] Edit refused: unreadable request."));
            return;
        }
        if (op.equals("save_now")) {
            exportBook();
            player.sendMessage(new TextComponentString("[Quests] Quest files saved on the server."));
            return;
        }
        if ((op.equals("reset_progress") || op.equals("complete_quest")) && args.getBool("all", false)) {
            // the whole book, for the player's team
            TeamProgress team = progressOf(player);
            long now = System.currentTimeMillis();
            for (com.levviata.reflibquests.model.Definitions.Quest cq : new java.util.ArrayList<>(book.allQuests())) {
                apply(team, op.equals("complete_quest") ? engine.forceComplete(team, cq.id, now, true) : engine.resetQuest(team, cq.id));
            }
            ReflibQuestsMod.LOGGER.info("{} ran {} on the whole book", player.getName(), op);
            return;
        }
        if ((op.equals("reset_progress") || op.equals("complete_quest")) && !args.getString("chapter", "").isEmpty()) {
            com.levviata.reflibquests.model.Definitions.Chapter ch = book.chapter(args.getString("chapter", ""));
            if (ch == null) {
                player.sendMessage(new TextComponentString("[Quests] That chapter no longer exists."));
                return;
            }
            TeamProgress team = progressOf(player);
            long now = System.currentTimeMillis();
            for (com.levviata.reflibquests.model.Definitions.Quest cq : new java.util.ArrayList<>(ch.quests)) {
                apply(team, op.equals("complete_quest") ? engine.forceComplete(team, cq.id, now, args.getBool("deps", false)) : engine.resetQuest(team, cq.id));
            }
            ReflibQuestsMod.LOGGER.info("{} ran {} on chapter {}", player.getName(), op, ch.id);
            return;
        }
        if (op.equals("reset_progress") || op.equals("complete_quest")) {
            String questId = args.getString("id", "");
            if (book.quest(questId) == null) {
                player.sendMessage(new TextComponentString("[Quests] That quest no longer exists."));
                return;
            }
            TeamProgress team = progressOf(player);
            long now = System.currentTimeMillis();
            apply(team, op.equals("complete_quest") ? engine.forceComplete(team, questId, now, args.getBool("deps", false)) : engine.resetQuest(team, questId));
            ReflibQuestsMod.LOGGER.info("{} ran {} on {}", player.getName(), op, questId);
            return;
        }
        if (op.equals("complete_task") || op.equals("reset_task")) {
            String taskId = args.getString("id", "");
            if (book.task(taskId) == null) {
                player.sendMessage(new TextComponentString("[Quests] That task no longer exists."));
                return;
            }
            TeamProgress team = progressOf(player);
            long now = System.currentTimeMillis();
            apply(team, op.equals("complete_task") ? engine.completeTask(team, taskId, now) : engine.resetTask(team, taskId));
            ReflibQuestsMod.LOGGER.info("{} ran {} on task {}", player.getName(), op, taskId);
            return;
        }
        if (op.equals("grab_item")) {
            com.levviata.reflibquests.model.Definitions.Task grabbed = book.task(args.getString("id", ""));
            com.levviata.reflib.api.data.DataNode spec = grabbed == null ? null : grabbed.params().get("item");
            java.util.List<ItemResolver.Resolved> found = spec == null ? java.util.List.of() : ItemSet.resolveAll(spec, idMap);
            if (found.isEmpty()) {
                player.sendMessage(new TextComponentString("[Quests] That task has no item to copy."));
                return;
            }
            // the player chose one of the task's items by its place in the list; the item itself is checked as well
            int at = args.getInt("index", 0);
            String wantId = args.getString("item", "");
            ItemResolver.Resolved chosen = at >= 0 && at < found.size() ? found.get(at) : found.get(0);
            if (!wantId.isEmpty()) {
                int wantDamage = args.getInt("damage", 0);
                ItemResolver.Resolved match = null;
                for (ItemResolver.Resolved r : found) {
                    String rid = r.toStack(1).getItem().getRegistryName() == null ? "" : r.toStack(1).getItem().getRegistryName().toString();
                    if (rid.equals(wantId) && (r.toStack(1).getMetadata() == wantDamage || match == null)) {
                        if (match == null || r.toStack(1).getMetadata() == wantDamage) {
                            match = r;
                        }
                    }
                }
                if (match == null) {
                    player.sendMessage(new TextComponentString("[Quests] That item is not one of the task's items."));
                    return;
                }
                chosen = match;
            }
            net.minecraft.item.ItemStack copy = chosen.toStack(Math.max(1, chosen.count())); // a full copy, with its data
            if (!player.addItemStackToInventory(copy)) {
                player.dropItem(copy, false);
            }
            return;
        }
        if (op.equals("undo") || op.equals("redo")) {
            String what = op.equals("undo") ? history.undo(book) : history.redo(book);
            if (what == null) {
                player.sendMessage(new TextComponentString("[Quests] Nothing to " + op + "."));
                return;
            }
            ReflibQuestsMod.LOGGER.info("{} ran {}: {}", player.getName(), op, what);
            book.reindex();
            tracker.rebuild();
            findInertTasks();
            pins.prune(book);
            bookDirty = true;
            if (history.lastNeedsFullSync) {
                changed(true); // chapters moved or groups changed: the whole book is sent again
            } else {
                bookBytesStale = true;
                sendChangedParts(); // only what was put back goes to the players, at once
            }
            return;
        }
        History.Open open = History.begin(book, op, args);
        String error = QuestEdits.apply(this, player, op, args);
        if (error != null) {
            player.sendMessage(new TextComponentString("[Quests] Edit refused: " + error));
            return;
        }
        if (open != null) {
            history.commit(book, open);
        }
        ReflibQuestsMod.LOGGER.info("{} edited the quest book: {}", player.getName(), op);
        editApplied(op);
    }

    private final History history = new History();
    private boolean bookBytesStale;

    /** Send the chapters and reward tables the last undo or redo changed, and nothing else. */
    private void sendChangedParts() {
        java.util.List<com.levviata.reflib.api.data.DataNode> chapters = new ArrayList<>();
        for (String id : history.lastChapterIds) {
            com.levviata.reflibquests.model.Definitions.Chapter c = book.chapter(id);
            chapters.add(com.levviata.reflib.api.data.DataNode.Compound.empty().with("id", id)
                    .with("index", c == null ? -1 : book.chapters().indexOf(c))
                    .with("data", c == null ? com.levviata.reflib.api.data.DataNode.Compound.empty() : BookCodec.chapterData(c)));
        }
        java.util.List<com.levviata.reflib.api.data.DataNode> tables = new ArrayList<>();
        for (String id : history.lastTableIds) {
            com.levviata.reflibquests.model.Definitions.RewardTable t = book.tables().get(id);
            tables.add(com.levviata.reflib.api.data.DataNode.Compound.empty().with("id", id)
                    .with("data", t == null ? com.levviata.reflib.api.data.DataNode.Compound.empty() : BookCodec.tableData(t)));
        }
        byte[] packed = Gz.pack(com.levviata.reflib.api.data.DataNode.Compound.empty()
                .with("chapters", new com.levviata.reflib.api.data.DataNode.Lst(chapters))
                .with("tables", new com.levviata.reflib.api.data.DataNode.Lst(tables)));
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            QuestNet.sendDelta(p, packed);
            QuestNet.sendPins(p, pins.pinsOf(p.getUniqueID()));
            sendHistory(p);
        }
    }

    /** Edits that add, remove or replace tasks (the tracker keeps its own list of them). */
    private static final java.util.Set<String> TASK_OPS = java.util.Set.of(
            "add_task", "remove_task", "task_value", "delete_quest", "delete_chapter", "create_quest");

    /**
     * The book changed. Only what the edit can have affected is rebuilt (the task list, the check for tasks that cannot
     * progress, the pins), and the whole book is sent once the edits pause instead of after every single one.
     */
    private void editApplied(String op) {
        changed(TASK_OPS.contains(op) || op.equals("delete_quest") || op.equals("delete_chapter"));
    }

    /** The book changed: rebuild what the change may have affected (when {@code structural}) and schedule sending it. */
    private void changed(boolean structural) {
        book.reindex();
        if (structural) {
            tracker.rebuild();
            findInertTasks();
            pins.prune(book);
        }
        bookDirty = true;
        long now = System.currentTimeMillis();
        if (resyncFirstAt == 0) {
            resyncFirstAt = now;
        }
        // the first edit after a pause is sent on the next tick (adding a task shows at once); edits in quick succession wait for a pause
        resyncAt = now - lastChangeAt > 300 ? now : now + 250;
        lastChangeAt = now;
    }

    private long lastChangeAt;

    private void flushTableSync() {
        if (resyncAt == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < resyncAt && now - resyncFirstAt < 1500) {
            return; // still being edited
        }
        resyncAt = 0;
        resyncFirstAt = 0;
        bookBytesStale = false;
        bookBytes = Gz.pack(BookCodec.write(book, idMapRaw));
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            QuestNet.sendBook(p, bookBytes); // only the book changed; progress, pins and rights are as they were
            QuestNet.sendPins(p, pins.pinsOf(p.getUniqueID()));
            sendHistory(p);
        }
    }

    private void sendHistory(EntityPlayerMP p) {
        QuestNet.sendHistory(p, history.undoCount(), history.redoCount(), history.undoLabel(), history.redoLabel());
    }

    private volatile Thread exporter;
    private final java.util.ArrayDeque<Runnable> deferredEdits = new java.util.ArrayDeque<>();

    /**
     * Save the book on a background thread so the game does not stall. While it runs the book is only read, and edits that
     * arrive are held back and applied right after (see {@link #tick}).
     */
    private void exportBookInBackground() {
        if (exporter != null || !bookDirty) {
            return;
        }
        bookDirty = false;
        Thread t = new Thread(() -> {
            try {
                writeBook();
            } finally {
                exporter = null;
            }
        }, "reflibquests-save");
        exporter = t;
        t.start();
    }

    private void waitForExport() {
        Thread t = exporter;
        if (t != null) {
            try {
                t.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Write the book to the quest folder now (keeps a copy of the previous folder once per session). */
    public void exportBook() {
        waitForExport();
        bookDirty = false;
        writeBook();
    }

    private void writeBook() {
        long started = System.currentTimeMillis();
        try {
            if (!backedUp && questFolder().isDirectory()) {
                backupFolder();
                backedUp = true;
            }
            UpstreamExporter.export(book, questFolder().toPath());
            ReflibQuestsMod.LOGGER.info("Saved the quest book to {} ({} ms)", questFolder(), System.currentTimeMillis() - started);
        } catch (IOException | RuntimeException ex) {
            bookDirty = true; // try again next time
            ReflibQuestsMod.LOGGER.error("Cannot save the quest book: {}", ex.getMessage(), ex);
        }
    }

    private void backupFolder() throws IOException {
        Path source = questFolder().toPath();
        Path target = new File(configDir, "backups/quests-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date())).toPath();
        try (java.util.stream.Stream<Path> walk = Files.walk(source)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (p.startsWith(configDir.toPath().resolve("backups"))) {
                    continue; // when the quests live in the config folder itself, do not copy the backups into a backup
                }
                Path dest = target.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest);
                }
            }
        }
        ReflibQuestsMod.LOGGER.info("Backed up the quest folder to {}", target);
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
            if (!team.isCompleted(q.id)) {
                continue; // only completed quests have rewards to claim
            }
            for (Reward r : q.rewards) {
                if (!r.type().equals("choice") && !r.params().getBool("exclude_from_claim_all", false)
                        && engine.canClaim(team, player.getUniqueID(), r)
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
        int before = inertTasks.size();
        inertTasks.clear();
        for (Quest q : book.allQuests()) {
            for (Task t : q.tasks) {
                if (!SUPPORTED_TASKS.contains(t.type())) {
                    inertTasks.add(q.id + "/" + t.id() + ": unsupported task type '" + t.type() + "'");
                } else if (t.type().equals("biome") && net.minecraftforge.fml.common.registry.ForgeRegistries.BIOMES.getValue(
                        new net.minecraft.util.ResourceLocation(t.params().getString("biome", "minecraft:none"))) == null) {
                    inertTasks.add(q.id + "/" + t.id() + ": biome '" + t.params().getString("biome", "?") + "' does not exist in 1.12.2");
                } else if (t.type().equals("structure") && TaskTracker.structureName(t.params().getString("structure", "")).isEmpty()) {
                    inertTasks.add(q.id + "/" + t.id() + ": structure '" + t.params().getString("structure", "?") + "' is not known in 1.12.2");
                } else if (t.type().equals("observation") && !t.params().getString("observation_type", "block").equals("block")) {
                    inertTasks.add(q.id + "/" + t.id() + ": only blocks can be observed so far");
                } else if (t.type().equals("item") && ItemSet.isFilter(t.params().get("item"))) {
                    // a "any of these items" filter works as long as at least one of its items exists in this game
                    if (ItemSet.resolveAll(t.params().get("item"), idMap).isEmpty()) {
                        inertTasks.add(q.id + "/" + t.id() + ": none of the filter's items exist in 1.12.2");
                    }
                } else if (t.type().equals("item") && ItemResolver.resolve(t.params().get("item"), idMap) == null) {
                    inertTasks.add(q.id + "/" + t.id() + ": item '" + describe(t.params().get("item")) + "' is not known in 1.12.2 (add it to id_map.snbt)");
                }
            }
        }
        if (!inertTasks.isEmpty() && inertTasks.size() != before) {
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
        if (bookDirty) {
            exportBookInBackground();
        }
        for (UUID id : new ArrayList<>(dirty)) {
            save(id);
        }
        if (pins.isDirty()) {
            savePins();
        }
    }

    /** Everything is written before this returns (used when the world closes), including a save that is still running. */
    public void saveAll() {
        waitForExport();
        if (bookDirty) {
            exportBook();
        }
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
        if (book.settings.getBool("suppress_all_autoclaiming", false)) {
            return false; // a setting for pack development: nothing is claimed by itself
        }
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
        while (exporter == null && !deferredEdits.isEmpty()) {
            deferredEdits.poll().run();
        }
        flushTableSync();
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
