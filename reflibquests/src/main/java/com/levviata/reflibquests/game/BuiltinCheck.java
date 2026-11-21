/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import com.levviata.reflibquests.io.BuiltinIdMap;

/**
 * Checks the built-in id table against the real registries of the running game: every target item must exist,
 * and a target damage value must be a variant the item really has. Used by the smoke test so a wrong table entry
 * is found by a test run instead of by a player.
 */
public final class BuiltinCheck {

    private BuiltinCheck() {}

    /** @return one line per wrong entry; empty if the table is consistent with the game */
    public static List<String> problems() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : BuiltinIdMap.items().entrySet()) {
            String target = e.getValue();
            int at = target.lastIndexOf('@');
            String name = at < 0 ? target : target.substring(0, at);
            int meta = at < 0 ? -1 : Integer.parseInt(target.substring(at + 1));
            ResourceLocation rl = new ResourceLocation(name);
            if (!ForgeRegistries.ITEMS.containsKey(rl)) {
                out.add(e.getKey() + " -> " + target + ": no such item in 1.12.2");
                continue;
            }
            if (meta < 0) {
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null) {
                continue;
            }
            if (!item.getHasSubtypes()) {
                if (meta != 0) {
                    out.add(e.getKey() + " -> " + target + ": the item has no variants");
                }
                continue;
            }
            NonNullList<ItemStack> variants = NonNullList.create();
            item.getSubItems(CreativeTabs.SEARCH, variants);
            boolean found = false;
            for (ItemStack s : variants) {
                if (s.getMetadata() == meta) {
                    found = true;
                    break;
                }
            }
            // bark-on-all-sides logs (damage 12 to 15) exist but are not offered in the creative inventory
            boolean hiddenBark = (name.equals("minecraft:log") || name.equals("minecraft:log2")) && meta >= 12;
            if (!found && !hiddenBark) {
                out.add(e.getKey() + " -> " + target + ": the item has no variant " + meta);
            }
        }
        for (Map.Entry<String, String> e : BuiltinIdMap.entities().entrySet()) {
            if (EntityList.getClass(new ResourceLocation(e.getValue())) == null) {
                out.add(e.getKey() + " -> " + e.getValue() + ": no such entity in 1.12.2");
            }
        }
        return out;
    }
}
