/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import com.levviata.reflibquests.ReflibQuestsMod;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.model.QuestRules;
import com.levviata.reflibquests.model.TeamProgress;

/**
 * {@code /rquests}. Player commands (anyone): list, info, check, submit, claim, claimall. Admin commands
 * (permission level 2): complete, reset, reload, lock, blockrewards, problems.
 */
public final class QuestCommand extends CommandBase {

    private static final List<String> PLAYER = List.of("list", "info", "check", "submit", "claim", "claimall");
    private static final List<String> ADMIN = List.of("complete", "reset", "reload", "lock", "blockrewards", "problems");

    @Override
    public String getName() {
        return "rquests";
    }

    @Override
    public List<String> getAliases() {
        return List.of("rq");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rquests <list|info <quest>|check <task>|submit <task>|claim <reward> [choice]|claimall>"
                + " (admin: complete|reset <quest> [player], reload, lock|blockrewards <true|false> [player], problems)";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, net.minecraft.util.math.BlockPos pos) {
        if (args.length == 1) {
            List<String> all = new ArrayList<>(PLAYER);
            if (sender.canUseCommand(2, getName())) {
                all.addAll(ADMIN);
            }
            return getListOfStringsMatchingLastWord(args, all);
        }
        return List.of();
    }

    private static void say(ICommandSender sender, String text) {
        sender.sendMessage(new TextComponentString(text));
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        QuestServer qs = QuestServer.get();
        if (qs == null) {
            throw new CommandException("Quests are not loaded");
        }
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        String sub = args[0].toLowerCase();
        if (ADMIN.contains(sub) && !sender.canUseCommand(2, getName())) {
            throw new CommandException("You need permission level 2 for /rquests " + sub);
        }
        switch (sub) {
            case "reload":
                qs.reload();
                say(sender, "Reloaded: " + qs.book().chapters().size() + " chapters, " + qs.book().allQuests().size() + " quests.");
                return;
            case "problems":
                say(sender, qs.inertTasks().isEmpty() ? "No inert tasks." : qs.inertTasks().size() + " inert task(s):");
                for (int i = 0; i < Math.min(15, qs.inertTasks().size()); i++) {
                    say(sender, " - " + qs.inertTasks().get(i));
                }
                return;
            case "list":
                list(qs, getCommandSenderAsPlayer(sender), sender);
                return;
            case "info":
                info(qs, getCommandSenderAsPlayer(sender), sender, arg(args, 1));
                return;
            case "check":
                say(sender, qs.tracker().check(getCommandSenderAsPlayer(sender), arg(args, 1)));
                return;
            case "submit":
                say(sender, qs.tracker().submit(getCommandSenderAsPlayer(sender), arg(args, 1)));
                return;
            case "claim":
                claim(qs, getCommandSenderAsPlayer(sender), sender, arg(args, 1), args.length > 2 ? parseInt(args[2], 1) - 1 : -1);
                return;
            case "claimall":
                claimAll(qs, getCommandSenderAsPlayer(sender), sender);
                return;
            case "complete":
            case "reset":
            case "lock":
            case "blockrewards":
                admin(server, qs, sender, sub, args);
                return;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    private static String arg(String[] args, int i) throws WrongUsageException {
        if (args.length <= i) {
            throw new WrongUsageException("Missing argument");
        }
        return args[i];
    }

    private void list(QuestServer qs, EntityPlayerMP player, ICommandSender sender) {
        TeamProgress team = qs.progressOf(player);
        for (Chapter c : qs.book().chapters()) {
            int done = 0;
            int shown = 0;
            for (Quest q : c.quests) {
                if (QuestRules.isVisible(qs.book(), team, q)) {
                    shown++;
                    if (team.isCompleted(q.id)) {
                        done++;
                    }
                }
            }
            say(sender, "§e" + (c.title.isEmpty() ? c.id : c.title) + "§r: " + done + "/" + shown);
            for (Quest q : c.quests) {
                if (QuestRules.isVisible(qs.book(), team, q)) {
                    String state = team.isCompleted(q.id) ? "§a[done]" : qs.engine().isLocked(team, q) ? "§8[locked]" : "§f[" + QuestRules.percent(team, q) + "%]";
                    say(sender, "  " + state + "§r " + (q.title.isEmpty() ? q.id : q.title) + " §7(" + q.id + ")");
                }
            }
        }
    }

    private void info(QuestServer qs, EntityPlayerMP player, ICommandSender sender, String id) {
        Quest q = qs.book().quest(id);
        if (q == null) {
            say(sender, "Unknown quest '" + id + "'.");
            return;
        }
        TeamProgress team = qs.progressOf(player);
        if (!QuestRules.isVisible(qs.book(), team, q)) {
            say(sender, "Unknown quest '" + id + "'.");
            return;
        }
        say(sender, "§e" + (q.title.isEmpty() ? q.id : q.title) + "§r " + QuestRules.percent(team, q) + "%");
        if (!QuestRules.detailsVisible(qs.book(), team, q)) {
            say(sender, "  (details hidden until this quest is available)");
            return;
        }
        for (String line : q.description) {
            say(sender, "  " + line);
        }
        for (Task t : q.tasks) {
            say(sender, "  task " + t.id() + " [" + t.type() + "]: " + team.taskProgress(t.id()) + "/" + t.maxProgress());
        }
        for (Reward r : q.rewards) {
            say(sender, "  reward " + r.id() + " [" + r.type() + "]" + (team.isClaimed(r.id(), player.getUniqueID()) ? " claimed" : qs.engine().canClaim(team, player.getUniqueID(), r) ? " ready" : ""));
        }
    }

    private void claim(QuestServer qs, EntityPlayerMP player, ICommandSender sender, String rewardId, int choice) {
        Reward reward = findReward(qs, rewardId);
        if (reward == null) {
            say(sender, "Unknown reward '" + rewardId + "'.");
            return;
        }
        report(sender, qs.claim(player, reward, choice, false), reward);
    }

    private void claimAll(QuestServer qs, EntityPlayerMP player, ICommandSender sender) {
        TeamProgress team = qs.progressOf(player);
        int claimed = 0;
        for (Quest q : qs.book().allQuests()) {
            for (Reward r : q.rewards) {
                if (!r.type().equals("choice") && qs.engine().canClaim(team, player.getUniqueID(), r)
                        && qs.claim(player, r, -1, true) == RewardGranter.Result.GRANTED) {
                    claimed++;
                }
            }
        }
        say(sender, claimed + " reward(s) claimed.");
    }

    private void report(ICommandSender sender, RewardGranter.Result result, Reward reward) {
        switch (result) {
            case GRANTED:
                break; // the claim already told the player
            case NEEDS_CHOICE:
                say(sender, "Choose one: /rquests claim " + reward.id() + " <number>");
                break;
            case UNSUPPORTED:
                say(sender, "This reward type is not supported yet (" + reward.type() + "); it stays unclaimed.");
                break;
            default:
                say(sender, "You cannot claim that reward now.");
        }
    }

    private static Reward findReward(QuestServer qs, String id) {
        for (Quest q : qs.book().allQuests()) {
            for (Reward r : q.rewards) {
                if (r.id().equals(id)) {
                    return r;
                }
            }
        }
        return null;
    }

    private void admin(MinecraftServer server, QuestServer qs, ICommandSender sender, String sub, String[] args) throws CommandException {
        boolean isFlag = sub.equals("lock") || sub.equals("blockrewards");
        String targetName = args.length > 2 ? args[2] : null;
        EntityPlayerMP target = targetName != null ? getPlayer(server, sender, targetName) : getCommandSenderAsPlayer(sender);
        TeamProgress team = qs.progressOf(target);
        UUID teamId = team.teamId();
        if (isFlag) {
            boolean value = parseBoolean(arg(args, 1));
            if (sub.equals("lock")) {
                team.setLocked(value);
            } else {
                team.setRewardsBlocked(value);
            }
            qs.markDirty(teamId);
            say(sender, sub + " = " + value + " for " + target.getName() + "'s team");
            return;
        }
        String questId = arg(args, 1);
        if (qs.book().quest(questId) == null) {
            throw new CommandException("Unknown quest " + questId);
        }
        if (sub.equals("complete")) {
            qs.apply(team, qs.engine().forceComplete(team, questId, System.currentTimeMillis()));
        } else {
            qs.apply(team, qs.engine().resetQuest(team, questId));
        }
        say(sender, sub + " " + questId + " done for " + target.getName() + "'s team");
        ReflibQuestsMod.LOGGER.info("{} ran /rquests {} {}", sender.getName(), sub, questId);
    }
}
