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

@SideOnly(Side.CLIENT)
final class QuestScreen extends ReflibScreen {

    @Override
    protected Widget buildRoot(int screenWidth, int screenHeight) {
        return new QuestBookWidget(new Rect(0, 0, screenWidth, screenHeight));
    }

    @Override
    public void initGui() {
        super.initGui();
        org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        org.lwjgl.input.Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        com.levviata.reflibquests.model.QuestBook b = ClientQuests.book();
        return b != null && b.settings.getBool("pause_game", false) && net.minecraft.client.Minecraft.getMinecraft().isSingleplayer();
    }
}
