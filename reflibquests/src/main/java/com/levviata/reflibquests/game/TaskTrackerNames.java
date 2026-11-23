/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.game;

import com.levviata.reflib.api.data.DataNode;

public final class TaskTrackerNames {

    private TaskTrackerNames() {}

    public static String fluidLabel(DataNode.Compound params) {
        String name = TaskTracker.fluidNameOf(params);
        net.minecraftforge.fluids.Fluid fluid = net.minecraftforge.fluids.FluidRegistry.getFluid(name);
        return fluid == null ? name : fluid.getLocalizedName(new net.minecraftforge.fluids.FluidStack(fluid, 1000));
    }
}
