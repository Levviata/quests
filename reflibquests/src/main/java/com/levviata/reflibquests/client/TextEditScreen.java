/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflib.gui.ReflibScreen;
import com.levviata.reflib.gui.Rect;
import com.levviata.reflib.gui.Widget;
import com.levviata.reflib.gui.Widgets;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.net.QuestNet;

/**
 * A small form for the editor: the title, subtitle and description of a quest, or the name of a chapter.
 * Tab moves between fields, Enter saves, Escape cancels. In the description, type {@code \n} (backslash and the
 * letter n) for a line break. Saving sends the change to the server, which validates it.
 */
@SideOnly(Side.CLIENT)
final class TextEditScreen extends ReflibScreen {

    private enum Kind { QUEST, CHAPTER_RENAME, CHAPTER_NEW, GROUP_NEW, GROUP_RENAME }

    private final Kind kind;
    private final String id;
    private final String[] values;
    private final String[] labels;
    private final int[] limits;
    private final List<Widgets.TextField> fields = new ArrayList<>();
    private int focus;

    private TextEditScreen(Kind kind, String id, String[] labels, String[] values, int[] limits) {
        this.kind = kind;
        this.id = id;
        this.labels = labels;
        this.values = values;
        this.limits = limits;
    }

    static TextEditScreen forQuest(Quest q) {
        return new TextEditScreen(Kind.QUEST, q.id,
                new String[] {"Title", "Subtitle", "Description (\\n = new line)"},
                new String[] {q.title, q.subtitle, String.join("\\n", q.description)},
                new int[] {120, 120, 2000});
    }

    static TextEditScreen forChapter(Chapter c) {
        return new TextEditScreen(Kind.CHAPTER_RENAME, c.id, new String[] {"Chapter name"}, new String[] {c.title}, new int[] {120});
    }

    static TextEditScreen forGroup(com.levviata.reflibquests.model.Definitions.ChapterGroup g) {
        return new TextEditScreen(Kind.GROUP_RENAME, g.id, new String[] {"Group name"}, new String[] {g.title}, new int[] {120});
    }

    static TextEditScreen newGroup() {
        return new TextEditScreen(Kind.GROUP_NEW, "", new String[] {"Name of the new group"}, new String[] {"New group"}, new int[] {120});
    }

    static TextEditScreen newChapter() {
        return new TextEditScreen(Kind.CHAPTER_NEW, "", new String[] {"Name of the new chapter"}, new String[] {"New chapter"}, new int[] {120});
    }

    @Override
    protected Widget buildRoot(int screenWidth, int screenHeight) {
        for (int i = 0; i < fields.size(); i++) {
            values[i] = fields.get(i).text(); // keep what was typed across a window resize
        }
        fields.clear();
        int w = Math.min(360, screenWidth - 20);
        int h = 36 + labels.length * 34 + 30;
        int x = (screenWidth - w) / 2;
        int y = (screenHeight - h) / 2;
        Widgets.Panel root = new Widgets.Panel(new Rect(x, y, w, h), 0xEE15151D);
        int cy = y + 8;
        for (int i = 0; i < labels.length; i++) {
            root.add(new Widgets.Label(new Rect(x + 10, cy, w - 20, 10), labels[i], 0xCCCCCC));
            Widgets.TextField f = new Widgets.TextField(new Rect(x + 10, cy + 11, w - 20, 16), labels[i], values[i], limits[i]);
            f.setFocused(i == focus);
            fields.add(f);
            root.add(f);
            cy += 34;
        }
        root.add(new Widgets.Button(new Rect(x + 10, y + h - 24, 90, 18), "Save", this::save));
        root.add(new Widgets.Button(new Rect(x + w - 100, y + h - 24, 90, 18), "Cancel", this::back));
        return root;
    }

    private void back() {
        Minecraft.getMinecraft().displayGuiScreen(new QuestScreen());
    }

    private void save() {
        String first = fields.get(0).text().trim();
        switch (kind) {
            case QUEST: {
                List<DataNode> lines = new ArrayList<>();
                String desc = fields.get(2).text();
                if (!desc.isEmpty()) {
                    for (String line : desc.split("\\\\n", -1)) {
                        lines.add(new DataNode.Str(line));
                    }
                }
                TableEdits.send("set_text", DataNode.Compound.empty().with("id", id).with("title", first)
                        .with("subtitle", fields.get(1).text().trim()).with("desc", new DataNode.Lst(lines)));
                break;
            }
            case CHAPTER_RENAME:
                TableEdits.send("rename_chapter", DataNode.Compound.empty().with("id", id).with("title", first));
                break;
            case GROUP_RENAME:
                TableEdits.send("rename_group", DataNode.Compound.empty().with("id", id).with("title", first));
                break;
            case GROUP_NEW:
                TableEdits.send("create_group", DataNode.Compound.empty().with("title", first).with("id", RewardTablesScreen.newId()));
                break;
            default:
                TableEdits.send("create_chapter", DataNode.Compound.empty().with("title", first));
                break;
        }
        back();
    }

    private void moveFocus(int to) {
        for (int i = 0; i < fields.size(); i++) {
            fields.get(i).setFocused(i == to);
        }
        focus = to;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).bounds().contains(mouseX, mouseY)) {
                moveFocus(i);
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        switch (keyCode) {
            case Keyboard.KEY_ESCAPE:
                back();
                return;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
                save();
                return;
            case Keyboard.KEY_TAB:
                moveFocus((focus + (isShiftKeyDown() ? fields.size() - 1 : 1)) % fields.size());
                return;
            default:
                super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
