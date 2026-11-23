/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

public final class QuestBookItem extends Item {

    public QuestBookItem() {
        setRegistryName(ReflibQuestsMod.ID, "quest_book");
        setTranslationKey(ReflibQuestsMod.ID + ".quest_book");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.MISC);
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public void addInformation(ItemStack stack, @javax.annotation.Nullable World world, java.util.List<String> tooltip,
                               net.minecraft.client.util.ITooltipFlag flag) {
        tooltip.add("Right-click to open the quest book");
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (world.isRemote) {
            com.levviata.reflibquests.client.ClientBootstrap.requestOpen();
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }
}
