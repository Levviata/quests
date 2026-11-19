/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in names of newer game versions mapped to their 1.12.2 equivalents (item name plus damage value, written
 * {@code name@meta}). Generated from the rules of the 1.13 "flattening" (colours and wood types that used to be
 * damage values became separate names) plus explicit renames. Names that did not change need no entry, and
 * things that do not exist in 1.12.2 (netherite, stripped logs, bamboo...) are deliberately absent, so tasks that
 * use them stay inert instead of silently turning into something else.
 *
 * <p>The table is checked against the real 1.12.2 registries by the in-game smoke test.
 */
public final class BuiltinIdMap {

    private static final String MC = "minecraft:";

    /** Colour name and its dye damage value in 1.12.2 (black 0 ... white 15). Block colours use {@code 15 - dye}. */
    private static final Object[][] COLORS = {
            {"black", 0}, {"red", 1}, {"green", 2}, {"brown", 3}, {"blue", 4}, {"purple", 5}, {"cyan", 6},
            {"light_gray", 7}, {"gray", 8}, {"pink", 9}, {"lime", 10}, {"yellow", 11}, {"light_blue", 12},
            {"magenta", 13}, {"orange", 14}, {"white", 15},
    };

    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};

    private static final Map<String, String> ITEMS;
    private static final Map<String, String> ENTITIES;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        colors(m);
        woods(m);
        stone(m);
        misc(m);
        ITEMS = Collections.unmodifiableMap(m);

        Map<String, String> e = new LinkedHashMap<>();
        e.put(MC + "snow_golem", MC + "snowman");
        e.put(MC + "iron_golem", MC + "villager_golem");
        e.put(MC + "zombified_piglin", MC + "zombie_pigman");
        e.put(MC + "illusioner", MC + "illusion_illager");
        e.put(MC + "evoker", MC + "evocation_illager");
        e.put(MC + "vindicator", MC + "vindication_illager");
        ENTITIES = Collections.unmodifiableMap(e);
    }

    private BuiltinIdMap() {}

    /** @return new name to 1.12.2 target ({@code name} or {@code name@meta}) */
    public static Map<String, String> items() {
        return ITEMS;
    }

    public static Map<String, String> entities() {
        return ENTITIES;
    }

    private static void put(Map<String, String> m, String from, String to) {
        if (m.put(MC + from, MC + to) != null) {
            throw new IllegalStateException("duplicate mapping for " + from);
        }
    }

    private static void colors(Map<String, String> m) {
        for (Object[] c : COLORS) {
            String name = (String) c[0];
            int dye = (Integer) c[1];
            int block = 15 - dye;
            put(m, name + "_wool", "wool@" + block);
            put(m, name + "_terracotta", "stained_hardened_clay@" + block);
            put(m, name + "_concrete", "concrete@" + block);
            put(m, name + "_concrete_powder", "concrete_powder@" + block);
            put(m, name + "_stained_glass", "stained_glass@" + block);
            put(m, name + "_stained_glass_pane", "stained_glass_pane@" + block);
            put(m, name + "_carpet", "carpet@" + block);
            put(m, name + "_bed", "bed@" + block);
            put(m, name + "_banner", "banner@" + dye);
            put(m, name + "_dye", "dye@" + dye);
            if (name.equals("light_gray")) {
                put(m, "light_gray_shulker_box", "silver_shulker_box");
                put(m, "light_gray_glazed_terracotta", "silver_glazed_terracotta");
            }
        }
        put(m, "terracotta", "hardened_clay");
        put(m, "ink_sac", "dye@0");
        put(m, "cocoa_beans", "dye@3");
        put(m, "lapis_lazuli", "dye@4");
        put(m, "bone_meal", "dye@15");
    }

    private static void woods(Map<String, String> m) {
        for (int i = 0; i < WOODS.length; i++) {
            String w = WOODS[i];
            put(m, w + "_planks", "planks@" + i);
            put(m, w + "_sapling", "sapling@" + i);
            put(m, w + "_slab", "wooden_slab@" + i);
            if (i < 4) {
                put(m, w + "_leaves", "leaves@" + i);
                put(m, w + "_log", "log@" + i);
                put(m, w + "_wood", "log@" + (12 + i));
            } else {
                put(m, w + "_leaves", "leaves2@" + (i - 4));
                put(m, w + "_log", "log2@" + (i - 4));
                put(m, w + "_wood", "log2@" + (12 + i - 4));
            }
        }
        put(m, "oak_fence", "fence");
        put(m, "oak_fence_gate", "fence_gate");
        put(m, "oak_door", "wooden_door");
        put(m, "oak_trapdoor", "trapdoor");
        put(m, "oak_button", "wooden_button");
        put(m, "oak_pressure_plate", "wooden_pressure_plate");
        put(m, "oak_sign", "sign");
        put(m, "oak_boat", "boat");
    }

    private static void stone(Map<String, String> m) {
        // plain names that were one variant of a damage-value block: pin the variant instead of matching any
        put(m, "stone", "stone@0");
        put(m, "dirt", "dirt@0");
        put(m, "sand", "sand@0");
        put(m, "sandstone", "sandstone@0");
        put(m, "red_sandstone", "red_sandstone@0");
        put(m, "sponge", "sponge@0");
        put(m, "quartz_block", "quartz_block@0");
        put(m, "prismarine", "prismarine@0");
        put(m, "anvil", "anvil@0");
        put(m, "cobblestone_wall", "cobblestone_wall@0");
        put(m, "golden_apple", "golden_apple@0");
        put(m, "coal", "coal@0");
        put(m, "granite", "stone@1");
        put(m, "polished_granite", "stone@2");
        put(m, "diorite", "stone@3");
        put(m, "polished_diorite", "stone@4");
        put(m, "andesite", "stone@5");
        put(m, "polished_andesite", "stone@6");
        put(m, "coarse_dirt", "dirt@1");
        put(m, "podzol", "dirt@2");
        put(m, "red_sand", "sand@1");
        put(m, "chiseled_sandstone", "sandstone@1");
        put(m, "cut_sandstone", "sandstone@2");
        put(m, "chiseled_red_sandstone", "red_sandstone@1");
        put(m, "cut_red_sandstone", "red_sandstone@2");
        put(m, "wet_sponge", "sponge@1");
        put(m, "chiseled_quartz_block", "quartz_block@1");
        put(m, "quartz_pillar", "quartz_block@2");
        put(m, "stone_bricks", "stonebrick@0");
        put(m, "mossy_stone_bricks", "stonebrick@1");
        put(m, "cracked_stone_bricks", "stonebrick@2");
        put(m, "chiseled_stone_bricks", "stonebrick@3");
        put(m, "mossy_cobblestone_wall", "cobblestone_wall@1");
        put(m, "prismarine_bricks", "prismarine@1");
        put(m, "dark_prismarine", "prismarine@2");
        put(m, "chipped_anvil", "anvil@1");
        put(m, "damaged_anvil", "anvil@2");
        put(m, "stone_slab", "stone_slab@0");
        put(m, "sandstone_slab", "stone_slab@1");
        put(m, "cobblestone_slab", "stone_slab@3");
        put(m, "brick_slab", "stone_slab@4");
        put(m, "stone_brick_slab", "stone_slab@5");
        put(m, "nether_brick_slab", "stone_slab@6");
        put(m, "quartz_slab", "stone_slab@7");
        put(m, "red_sandstone_slab", "stone_slab2@0");
        put(m, "cobblestone_stairs", "stone_stairs");
        put(m, "grass_block", "grass");
        put(m, "end_stone_bricks", "end_bricks");
        put(m, "bricks", "brick_block");
        put(m, "nether_bricks", "nether_brick");
        put(m, "red_nether_bricks", "red_nether_brick");
        put(m, "snow_block", "snow");
        put(m, "snow", "snow_layer");
        put(m, "magma_block", "magma");
        put(m, "slime_block", "slime");
        put(m, "nether_quartz_ore", "quartz_ore");
    }

    private static void misc(Map<String, String> m) {
        put(m, "charcoal", "coal@1");
        put(m, "cod", "fish@0");
        put(m, "salmon", "fish@1");
        put(m, "tropical_fish", "fish@2");
        put(m, "pufferfish", "fish@3");
        put(m, "cooked_cod", "cooked_fish@0");
        put(m, "cooked_salmon", "cooked_fish@1");
        put(m, "enchanted_golden_apple", "golden_apple@1");
        put(m, "sugar_cane", "reeds");
        put(m, "melon", "melon_block");
        put(m, "melon_slice", "melon");
        put(m, "jack_o_lantern", "lit_pumpkin");
        put(m, "dead_bush", "deadbush");
        put(m, "lily_pad", "waterlily");
        put(m, "cobweb", "web");
        put(m, "note_block", "noteblock");
        put(m, "eye_of_ender", "ender_eye");
        put(m, "firework_rocket", "fireworks");
        put(m, "firework_star", "firework_charge");
        put(m, "grass", "tallgrass@1");
        put(m, "fern", "tallgrass@2");
        put(m, "tall_grass", "double_plant@2");
        put(m, "large_fern", "double_plant@3");
        put(m, "dandelion", "yellow_flower@0");
        put(m, "poppy", "red_flower@0");
        put(m, "blue_orchid", "red_flower@1");
        put(m, "allium", "red_flower@2");
        put(m, "azure_bluet", "red_flower@3");
        put(m, "red_tulip", "red_flower@4");
        put(m, "orange_tulip", "red_flower@5");
        put(m, "white_tulip", "red_flower@6");
        put(m, "pink_tulip", "red_flower@7");
        put(m, "oxeye_daisy", "red_flower@8");
        put(m, "sunflower", "double_plant@0");
        put(m, "lilac", "double_plant@1");
        put(m, "rose_bush", "double_plant@4");
        put(m, "peony", "double_plant@5");
        put(m, "skeleton_skull", "skull@0");
        put(m, "wither_skeleton_skull", "skull@1");
        put(m, "zombie_head", "skull@2");
        put(m, "player_head", "skull@3");
        put(m, "creeper_head", "skull@4");
        put(m, "dragon_head", "skull@5");
        String[] discs = {"13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal", "strad", "ward", "11", "wait"};
        for (String d : discs) {
            put(m, "music_disc_" + d, "record_" + d);
        }
    }
}
