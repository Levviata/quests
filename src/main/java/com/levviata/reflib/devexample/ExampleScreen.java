/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.devexample;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.gui.ReflibScreen;
import com.levviata.reflib.gui.Rect;
import com.levviata.reflib.gui.Widget;
import com.levviata.reflib.gui.Widgets;

@SideOnly(Side.CLIENT)
final class ExampleScreen extends ReflibScreen {

    private int clicks;

    @Override
    protected Widget buildRoot(int screenWidth, int screenHeight) {
        int w = 220;
        int h = 170;
        int x = (screenWidth - w) / 2;
        int y = (screenHeight - h) / 2;
        Widgets.Panel root = new Widgets.Panel(new Rect(x, y, w, h), 0xDD202030);
        root.add(new Widgets.Label(new Rect(x + 8, y + 8, w - 16, 10), "Reflib GUI demo (scroll the list)", 0xFFFFFF));

        Widgets.ScrollPanel list = root.add(new Widgets.ScrollPanel(new Rect(x + 8, y + 24, w - 16, 90), 40 * 12));
        for (int i = 0; i < 40; i++) {
            list.add(new Widgets.Label(new Rect(2, i * 12, w - 24, 10), "Line " + (i + 1), 0xCCCCCC));
        }

        Widgets.Label counter = root.add(new Widgets.Label(new Rect(x + 8, y + 122, w - 16, 10), "Clicks: " + clicks, 0xFFFF55));
        root.add(new Widgets.Button(new Rect(x + 8, y + 138, 100, 20), "Click me", () -> {
            clicks++;
            counter.setText("Clicks: " + clicks);
        }));
        root.add(new Widgets.Button(new Rect(x + w - 108, y + 138, 100, 20), "Close", () -> mc.displayGuiScreen(null)));
        return root;
    }
}
