/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.game;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
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

    /**
     * A resolved item reference. {@code meta} is {@link OreDictionary#WILDCARD_VALUE} when any damage matches;
     * {@code nbt} (null for none) is the item's extra data: it is given with the item, and a stack only matches if it has
     * at least those entries.
     */
    public record Resolved(Item item, int meta, int count, @Nullable NBTTagCompound nbt) {

        public Resolved(Item item, int meta, int count) {
            this(item, meta, count, null);
        }

        public ItemStack toStack(int amount) {
            ItemStack stack = new ItemStack(item, amount, meta == OreDictionary.WILDCARD_VALUE ? 0 : meta);
            if (nbt != null) {
                stack.setTagCompound(nbt.copy());
            }
            return stack;
        }

        public boolean matches(ItemStack have) {
            return matches(have, false);
        }

        /** {@code exactNbt}: the stack's data must equal the item's, not just contain it. */
        public boolean matches(ItemStack have, boolean exactNbt) {
            return !have.isEmpty() && have.getItem() == item
                    && (meta == OreDictionary.WILDCARD_VALUE || have.getMetadata() == meta)
                    && (nbt == null || NBTUtil.areNBTEquals(nbt, have.getTagCompound(), !exactNbt));
        }
    }

    /** The extra data of an item spec ({@code nbt}, or {@code tag} as the older files write it), or null. */
    @Nullable
    private static NBTTagCompound nbtOf(DataNode.Compound c) {
        DataNode.Compound data = c.has("nbt") ? c.getCompound("nbt") : c.getCompound("tag");
        if (data.entries().isEmpty()) {
            return null;
        }
        try {
            return JsonToNBT.getTagFromJson(com.levviata.reflib.api.data.Snbt.printCompact(data));
        } catch (NBTException | RuntimeException ex) {
            return null; // data that is not valid NBT is ignored; the item itself still works
        }
    }

    private ItemResolver() {}

    @Nullable
    public static Resolved resolve(@Nullable DataNode spec, IdMap map) {
        String id;
        int count = 1;
        int damage = -1;
        NBTTagCompound nbt = null;
        if (spec instanceof DataNode.Str s) {
            id = s.value();
        } else if (spec instanceof DataNode.Compound c) {
            id = c.getString("id", "");
            count = Math.max(1, c.getInt("count", c.getInt("Count", 1)));
            damage = c.has("damage") ? c.getInt("damage", -1) : c.has("Damage") ? c.getInt("Damage", -1) : c.getInt("meta", -1);
            nbt = nbtOf(c);
        } else {
            return null;
        }
        if (id.isEmpty()) {
            return null;
        }
        IdMap.ItemTarget target = map.item(id);
        if (!target.name().equals(id) && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id))) {
            // a name this game already has wins over a newer game's meaning of it ("grass" is the grass block here)
            target = new IdMap.ItemTarget(id, 0, false);
        }
        ResourceLocation rl = new ResourceLocation(target.name());
        if (!ForgeRegistries.ITEMS.containsKey(rl)) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null) {
            return null;
        }
        int meta = target.hasMeta() ? target.meta() : damage >= 0 ? damage : OreDictionary.WILDCARD_VALUE;
        return new Resolved(item, meta, count, nbt);
    }
}
