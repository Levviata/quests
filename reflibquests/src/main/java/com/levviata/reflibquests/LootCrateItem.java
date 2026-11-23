/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflibquests.game.QuestServer;
import com.levviata.reflibquests.model.Definitions.RewardTable;

public final class LootCrateItem extends Item {

    public LootCrateItem() {
        setRegistryName(ReflibQuestsMod.ID, "loot_crate");
        setTranslationKey(ReflibQuestsMod.ID + ".loot_crate");
        setCreativeTab(CreativeTabs.MISC);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        RewardTable t = LootCrates.tableOf(LootCrates.book(), stack);
        String name = t == null ? "" : t.raw.getCompound("loot_crate").getString("item_name", "");
        return name.isEmpty() ? super.getItemStackDisplayName(stack) : name;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        RewardTable t = LootCrates.tableOf(LootCrates.book(), stack);
        return t != null && t.raw.getCompound("loot_crate").getBool("glow", false);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, net.minecraft.client.util.ITooltipFlag flag) {
        RewardTable t = LootCrates.tableOf(LootCrates.book(), stack);
        if (t != null && !t.title.isEmpty()) {
            tooltip.add("§7" + t.title);
        }
        tooltip.add("Right-click to open");
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) {
            return;
        }
        com.levviata.reflibquests.model.QuestBook book = LootCrates.book();
        if (book == null) {
            return;
        }
        for (RewardTable t : book.tables().values()) {
            if (LootCrates.enabled(t)) {
                items.add(LootCrates.stackFor(t));
            }
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        QuestServer qs = QuestServer.get();
        RewardTable t = qs == null ? null : LootCrates.tableOf(qs.book(), stack);
        if (t == null) {
            player.sendMessage(new TextComponentString("[Quests] This loot crate has no reward table."));
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        qs.rewards().openCrate((EntityPlayerMP) player, t);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
}
