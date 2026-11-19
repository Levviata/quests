/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.gui.ReflibScreen;
import com.levviata.reflib.gui.Rect;
import com.levviata.reflib.gui.Widget;

/** The quest book screen: hosts {@link QuestBookWidget} and keeps the world paused-looking backdrop. */
@SideOnly(Side.CLIENT)
final class QuestScreen extends ReflibScreen {

    @Override
    protected Widget buildRoot(int screenWidth, int screenHeight) {
        return new QuestBookWidget(new Rect(8, 8, screenWidth - 16, screenHeight - 16));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
