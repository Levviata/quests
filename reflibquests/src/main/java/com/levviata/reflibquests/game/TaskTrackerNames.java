/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.game;

import com.levviata.reflib.api.data.DataNode;

/** Names for the tasks' things that both the server and the book window need. */
public final class TaskTrackerNames {

    private TaskTrackerNames() {}

    /** A readable name for the fluid of a fluid task ("Water"); the id as written when the fluid is not known here. */
    public static String fluidLabel(DataNode.Compound params) {
        String name = TaskTracker.fluidNameOf(params);
        net.minecraftforge.fluids.Fluid fluid = net.minecraftforge.fluids.FluidRegistry.getFluid(name);
        return fluid == null ? name : fluid.getLocalizedName(new net.minecraftforge.fluids.FluidStack(fluid, 1000));
    }
}
