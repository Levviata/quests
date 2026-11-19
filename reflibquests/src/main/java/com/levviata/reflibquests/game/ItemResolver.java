/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.game;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.io.IdMap;

/**
 * Turns an item spec read from a quest file (a bare id string or an object with {@code id}, optional
 * {@code count} and {@code damage}/{@code meta}) into a 1.12.2 item, going through the id map. Specs that do
 * not resolve to a registered item yield null; the task or reward is then inert and reported at load time.
 */
public final class ItemResolver {

    /** A resolved item reference. {@code meta} is {@link OreDictionary#WILDCARD_VALUE} when any damage matches. */
    public record Resolved(Item item, int meta, int count) {

        public ItemStack toStack(int amount) {
            return new ItemStack(item, amount, meta == OreDictionary.WILDCARD_VALUE ? 0 : meta);
        }

        public boolean matches(ItemStack have) {
            return !have.isEmpty() && have.getItem() == item
                    && (meta == OreDictionary.WILDCARD_VALUE || have.getMetadata() == meta);
        }
    }

    private ItemResolver() {}

    @Nullable
    public static Resolved resolve(@Nullable DataNode spec, IdMap map) {
        String id;
        int count = 1;
        int damage = -1;
        if (spec instanceof DataNode.Str s) {
            id = s.value();
        } else if (spec instanceof DataNode.Compound c) {
            id = c.getString("id", "");
            count = Math.max(1, c.getInt("count", c.getInt("Count", 1)));
            damage = c.has("damage") ? c.getInt("damage", -1) : c.has("Damage") ? c.getInt("Damage", -1) : c.getInt("meta", -1);
        } else {
            return null;
        }
        if (id.isEmpty()) {
            return null;
        }
        IdMap.ItemTarget target = map.item(id);
        ResourceLocation rl = new ResourceLocation(target.name());
        if (!ForgeRegistries.ITEMS.containsKey(rl)) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null) {
            return null;
        }
        int meta = target.hasMeta() ? target.meta() : damage >= 0 ? damage : OreDictionary.WILDCARD_VALUE;
        return new Resolved(item, meta, count);
    }
}
