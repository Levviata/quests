/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflibquests.ReflibQuestsMod;

/**
 * Opens Just Enough Items' recipe screen for an item when JEI is installed. JEI is not a dependency: it is reached by
 * name at run time, and every failure just means "no recipe screen".
 */
@SideOnly(Side.CLIENT)
final class JeiBridge {

    private JeiBridge() {}

    static boolean available() {
        return Loader.isModLoaded("jei");
    }

    /** Show the recipes that produce the item. @return false if JEI is missing or could not open the screen */
    static boolean showRecipes(ItemStack stack) {
        if (!available() || stack.isEmpty()) {
            return false;
        }
        try {
            Object runtime = Class.forName("mezz.jei.Internal").getMethod("getRuntime").invoke(null);
            if (runtime == null) {
                return false;
            }
            Object registry = runtime.getClass().getMethod("getRecipeRegistry").invoke(runtime);
            Class<?> mode = Class.forName("mezz.jei.api.recipe.IFocus$Mode");
            Object focus = registry.getClass().getMethod("createFocus", mode, Object.class)
                    .invoke(registry, Enum.valueOf(mode.asSubclass(Enum.class), "OUTPUT"), stack);
            Object gui = runtime.getClass().getMethod("getRecipesGui").invoke(runtime);
            gui.getClass().getMethod("show", Class.forName("mezz.jei.api.recipe.IFocus")).invoke(gui, focus);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            ReflibQuestsMod.LOGGER.debug("Cannot open the JEI recipe screen: {}", ex.toString());
            return false;
        }
    }
}
