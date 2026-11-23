/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.game.QuestServer;
import com.levviata.reflibquests.model.Definitions.RewardTable;
import com.levviata.reflibquests.model.QuestBook;

public final class LootCrates {

    public static volatile Supplier<QuestBook> clientBook;

    private LootCrates() {}

    @Nullable
    public static QuestBook book() {
        Supplier<QuestBook> client = clientBook;
        if (client != null) {
            QuestBook b = client.get();
            if (b != null) {
                return b;
            }
        }
        QuestServer qs = QuestServer.get();
        return qs == null ? null : qs.book();
    }

    public static String typeOf(RewardTable t) {
        DataNode.Compound crate = t.raw.getCompound("loot_crate");
        String id = crate.getString("string_id", "");
        return id.isEmpty() ? t.id : id;
    }

    public static ItemStack stackFor(RewardTable t) {
        ItemStack stack = new ItemStack(ReflibQuestsMod.LOOT_CRATE);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("type", typeOf(t));
        stack.setTagCompound(tag);
        return stack;
    }

    @Nullable
    public static RewardTable tableOf(@Nullable QuestBook book, ItemStack stack) {
        if (book == null || !stack.hasTagCompound()) {
            return null;
        }
        String type = stack.getTagCompound().getString("type");
        for (RewardTable t : book.tables().values()) {
            if (typeOf(t).equals(type)) {
                return t;
            }
        }
        return book.tables().get(type);
    }

    public static boolean enabled(RewardTable t) {
        return t.raw.has("loot_crate");
    }

    public static int color(ItemStack stack) {
        RewardTable t = tableOf(book(), stack);
        return t == null ? 0xFFFFFF : t.raw.getCompound("loot_crate").getInt("color", 0xFFFFFF) & 0xFFFFFF;
    }
}
