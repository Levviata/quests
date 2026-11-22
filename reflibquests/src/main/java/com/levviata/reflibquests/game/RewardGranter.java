/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.game;

import java.util.Random;

import net.minecraft.advancements.Advancement;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.io.Names;
import com.levviata.reflibquests.model.Definitions.Reward;
import com.levviata.reflibquests.model.Definitions.RewardTable;
import com.levviata.reflibquests.model.Definitions.WeightedReward;

/** Performs rewards in the game. Supported: item, xp, xp_levels, command, toast, advancement, custom, random, all_table, choice. */
public final class RewardGranter {

    /** Outcome of a grant attempt. Only {@code GRANTED} counts as claimed. */
    public enum Result { GRANTED, NEEDS_CHOICE, UNSUPPORTED, FAILED }

    private static final Random RANDOM = new Random();

    private final QuestServer quests;

    RewardGranter(QuestServer quests) {
        this.quests = quests;
    }

    /**
     * @param choice zero-based index for a {@code choice} reward, or -1 if the player has not chosen
     */
    public Result grant(EntityPlayerMP player, Reward reward, int choice) {
        DataNode.Compound p = reward.params();
        switch (reward.type()) {
            case "item": {
                ItemResolver.Resolved r = ItemResolver.resolve(p.get("item"), quests.idMap());
                if (r == null) {
                    return Result.UNSUPPORTED;
                }
                int count = (int) Math.min(Integer.MAX_VALUE, com.levviata.reflibquests.model.Definitions.Task.itemCount(p));
                int bonus = p.getInt("random_bonus", 0);
                if (bonus > 0) {
                    count += RANDOM.nextInt(bonus + 1);
                }
                give(player, r.toStack(count));
                return Result.GRANTED;
            }
            case "xp":
                player.addExperience(Math.max(1, p.getInt("xp", 1)));
                return Result.GRANTED;
            case "xp_levels":
                player.addExperienceLevel(Math.max(1, p.getInt("xp_levels", 1)));
                return Result.GRANTED;
            case "command":
                runCommand(player, p);
                return Result.GRANTED;
            case "toast":
                player.sendMessage(new TextComponentString("§6[Quests] " + p.getString("description", "")));
                return Result.GRANTED;
            case "advancement": {
                MinecraftServer server = player.getServer();
                Advancement adv = server == null ? null
                        : server.getAdvancementManager().getAdvancement(new ResourceLocation(p.getString("advancement", "")));
                if (adv == null) {
                    return Result.FAILED;
                }
                String criterion = p.getString("criterion", "");
                if (criterion.isEmpty()) {
                    for (String c : player.getAdvancements().getProgress(adv).getRemaningCriteria()) {
                        player.getAdvancements().grantCriterion(adv, c);
                    }
                } else {
                    player.getAdvancements().grantCriterion(adv, criterion);
                }
                return Result.GRANTED;
            }
            case "custom":
                return Result.GRANTED; // nothing to do; other mods react to the claim
            case "random":
            case "loot":
            case "all_table":
            case "choice":
                return grantFromTable(player, reward, choice);
            default:
                return Result.UNSUPPORTED;
        }
    }

    private Result grantFromTable(EntityPlayerMP player, Reward reward, int choice) {
        RewardTable table = quests.book().tables().get(
                com.levviata.reflibquests.io.Ids.tableId(reward.params()));
        if (table == null) {
            return Result.FAILED;
        }
        switch (reward.type()) {
            case "random": {
                WeightedReward w = Names.pick(table.entries, table.emptyWeight, RANDOM);
                if (w != null) {
                    Result r = grant(player, w.reward(), -1);
                    if (r != Result.GRANTED) {
                        return r;
                    }
                }
                return Result.GRANTED;
            }
            case "loot": {
                // like random, but as many rolls as the table's loot size says (at least one); a roll may give nothing
                int rolls = Math.max(1, table.lootSize);
                for (int i = 0; i < rolls; i++) {
                    WeightedReward w = Names.pick(table.entries, table.emptyWeight, RANDOM);
                    if (w != null) {
                        grant(player, w.reward(), -1);
                    }
                }
                return Result.GRANTED;
            }
            case "all_table":
                for (WeightedReward w : table.entries) {
                    grant(player, w.reward(), -1);
                }
                return Result.GRANTED;
            default: // choice
                if (choice < 0 || choice >= table.entries.size()) {
                    return Result.NEEDS_CHOICE;
                }
                return grant(player, table.entries.get(choice).reward(), -1);
        }
    }

    private static void give(EntityPlayerMP player, ItemStack stack) {
        if (!player.addItemStackToInventory(stack)) {
            player.dropItem(stack, false);
        }
    }

    private void runCommand(EntityPlayerMP player, DataNode.Compound p) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        String command = p.getString("command", "").replace("@p", player.getName());
        int level = Math.max(0, Math.min(4, p.getInt("permission_level", 0)));
        boolean silent = p.getBool("silent", false);
        server.getCommandManager().executeCommand(new RewardSender(server, player, level, silent), command);
        String feedback = p.getString("feedback_message", "");
        if (!feedback.isEmpty()) {
            player.sendMessage(new TextComponentString(feedback));
        }
    }

    /** Runs reward commands as the player's position with a fixed permission level. */
    private static final class RewardSender implements ICommandSender {
        private final MinecraftServer server;
        private final EntityPlayerMP player;
        private final int level;
        private final boolean silent;

        RewardSender(MinecraftServer server, EntityPlayerMP player, int level, boolean silent) {
            this.server = server;
            this.player = player;
            this.level = level;
            this.silent = silent;
        }

        @Override
        public String getName() {
            return "Quest reward";
        }

        @Override
        public boolean canUseCommand(int permLevel, String commandName) {
            return permLevel <= level;
        }

        @Override
        public World getEntityWorld() {
            return player.world;
        }

        @Override
        public MinecraftServer getServer() {
            return server;
        }

        @Override
        public BlockPos getPosition() {
            return player.getPosition();
        }

        @Override
        public net.minecraft.entity.Entity getCommandSenderEntity() {
            return player;
        }

        @Override
        public boolean sendCommandFeedback() {
            return !silent;
        }

        @Override
        public void sendMessage(ITextComponent component) {
            if (!silent) {
                player.sendMessage(component);
            }
        }
    }
}
