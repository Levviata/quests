/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.levviata.reflib.api.data.Snbt;

class BuiltinIdMapTest {

    private static IdMap.ItemTarget t(String name) {
        return IdMap.withBuiltin(com.levviata.reflib.api.data.DataNode.Compound.empty()).item(name);
    }

    @Test
    void colourFamiliesUseTheDamageValuesOf112() {
        assertEquals(new IdMap.ItemTarget("minecraft:wool", 14, true), t("minecraft:red_wool"));
        assertEquals(new IdMap.ItemTarget("minecraft:wool", 0, true), t("minecraft:white_wool"));
        assertEquals(new IdMap.ItemTarget("minecraft:wool", 15, true), t("minecraft:black_wool"));
        assertEquals(new IdMap.ItemTarget("minecraft:wool", 8, true), t("minecraft:light_gray_wool"));
        assertEquals(new IdMap.ItemTarget("minecraft:stained_hardened_clay", 3, true), t("minecraft:light_blue_terracotta"));
        assertEquals(new IdMap.ItemTarget("minecraft:concrete", 5, true), t("minecraft:lime_concrete"));
        assertEquals(new IdMap.ItemTarget("minecraft:stained_glass_pane", 2, true), t("minecraft:magenta_stained_glass_pane"));
        assertEquals(new IdMap.ItemTarget("minecraft:carpet", 11, true), t("minecraft:blue_carpet"));
    }

    @Test
    void dyesAndBannersUseTheDyeOrder() {
        assertEquals(new IdMap.ItemTarget("minecraft:dye", 1, true), t("minecraft:red_dye"));
        assertEquals(new IdMap.ItemTarget("minecraft:dye", 15, true), t("minecraft:bone_meal"));
        assertEquals(new IdMap.ItemTarget("minecraft:dye", 4, true), t("minecraft:lapis_lazuli"));
        assertEquals(new IdMap.ItemTarget("minecraft:dye", 0, true), t("minecraft:ink_sac"));
        assertEquals(new IdMap.ItemTarget("minecraft:banner", 15, true), t("minecraft:white_banner"));
        assertEquals(new IdMap.ItemTarget("minecraft:bed", 0, true), t("minecraft:white_bed"));
        assertEquals(new IdMap.ItemTarget("minecraft:silver_shulker_box", 0, false), t("minecraft:light_gray_shulker_box"));
    }

    @Test
    void woodFamilies() {
        assertEquals(new IdMap.ItemTarget("minecraft:log", 0, true), t("minecraft:oak_log"));
        assertEquals(new IdMap.ItemTarget("minecraft:log", 3, true), t("minecraft:jungle_log"));
        assertEquals(new IdMap.ItemTarget("minecraft:log2", 1, true), t("minecraft:dark_oak_log"));
        assertEquals(new IdMap.ItemTarget("minecraft:planks", 4, true), t("minecraft:acacia_planks"));
        assertEquals(new IdMap.ItemTarget("minecraft:leaves2", 0, true), t("minecraft:acacia_leaves"));
        assertEquals(new IdMap.ItemTarget("minecraft:wooden_slab", 2, true), t("minecraft:birch_slab"));
        assertEquals(new IdMap.ItemTarget("minecraft:wooden_door", 0, false), t("minecraft:oak_door"));
        assertEquals(new IdMap.ItemTarget("minecraft:log", 12, true), t("minecraft:oak_wood"));
    }

    @Test
    void renamesAndVariants() {
        assertEquals(new IdMap.ItemTarget("minecraft:stone", 1, true), t("minecraft:granite"));
        assertEquals(new IdMap.ItemTarget("minecraft:reeds", 0, false), t("minecraft:sugar_cane"));
        assertEquals(new IdMap.ItemTarget("minecraft:coal", 1, true), t("minecraft:charcoal"));
        assertEquals(new IdMap.ItemTarget("minecraft:grass", 0, false), t("minecraft:grass_block"));
        assertEquals(new IdMap.ItemTarget("minecraft:melon_block", 0, false), t("minecraft:melon"));
        assertEquals(new IdMap.ItemTarget("minecraft:melon", 0, false), t("minecraft:melon_slice"));
        assertEquals(new IdMap.ItemTarget("minecraft:record_cat", 0, false), t("minecraft:music_disc_cat"));
        assertEquals(new IdMap.ItemTarget("minecraft:fish", 1, true), t("minecraft:salmon"));
    }

    @Test
    void thingsThatDidNotChangeOrDoNotExistAreLeftAlone() {
        assertEquals(new IdMap.ItemTarget("minecraft:diamond", 0, false), t("minecraft:diamond"));
        assertEquals(new IdMap.ItemTarget("minecraft:netherite_ingot", 0, false), t("minecraft:netherite_ingot"));
        assertEquals(new IdMap.ItemTarget("minecraft:stripped_oak_log", 0, false), t("minecraft:stripped_oak_log"));
    }

    @Test
    void entityRenames() {
        IdMap m = IdMap.withBuiltin(com.levviata.reflib.api.data.DataNode.Compound.empty());
        assertEquals("minecraft:snowman", m.entity("minecraft:snow_golem"));
        assertEquals("minecraft:villager_golem", m.entity("minecraft:iron_golem"));
        assertEquals("minecraft:zombie_pigman", m.entity("minecraft:zombified_piglin"));
        assertEquals("minecraft:zombie", m.entity("minecraft:zombie"));
    }

    @Test
    void userEntriesOverrideTheBuiltInOnes() {
        IdMap m = IdMap.withBuiltin(Snbt.parse("{ items: { \"minecraft:red_wool\": \"mymod:red_cloth\" } entities: { \"minecraft:iron_golem\": \"mymod:golem\" } }"));
        assertEquals(new IdMap.ItemTarget("mymod:red_cloth", 0, false), m.item("minecraft:red_wool"));
        assertEquals("mymod:golem", m.entity("minecraft:iron_golem"));
        assertEquals(new IdMap.ItemTarget("minecraft:wool", 0, true), m.item("minecraft:white_wool"), "other built-ins stay");
    }

    @Test
    void tableIsReasonablyLargeAndWellFormed() {
        assertTrue(BuiltinIdMap.items().size() > 250, "got " + BuiltinIdMap.items().size());
        for (var e : BuiltinIdMap.items().entrySet()) {
            assertTrue(e.getKey().startsWith("minecraft:"), e.getKey());
            assertTrue(e.getValue().startsWith("minecraft:"), e.getValue());
            String target = e.getValue();
            int at = target.indexOf('@');
            if (at >= 0) {
                int meta = Integer.parseInt(target.substring(at + 1));
                assertTrue(meta >= 0 && meta <= 15, e.getKey() + " -> " + target);
            }
        }
    }
}
