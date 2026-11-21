/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Chapter;
import com.levviata.reflibquests.net.QuestNet;
import com.levviata.reflibquests.text.RichText;

/** "Chapter Properties": the chapter's name and the defaults its quests start from. Accept sends only what changed. */
@SideOnly(Side.CLIENT)
final class ChapterPropertiesScreen extends SettingsScreen {

    private static final List<String> SHAPE_LABELS = List.of("Default", "Circle", "Square", "Rounded Square", "Diamond", "Pentagon",
            "Hexagon", "Octagon", "Heart", "Gear", "None");
    private static final List<String> SHAPE_NAMES = List.of("", "circle", "square", "rsquare", "diamond", "pentagon",
            "hexagon", "octagon", "heart", "gear", "none");
    private static final List<String> MODES = List.of("Default", "Linear", "Flexible");

    private final Chapter chapter;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, String> initial = new LinkedHashMap<>();

    ChapterPropertiesScreen(GuiScreen parent, Chapter c) {
        super(parent, "Chapter Properties: " + RichText.stripCodes(c.title.isEmpty() ? c.id : c.title));
        this.chapter = c;
        values.put("title", c.title);
        values.put("shape", SHAPE_LABELS.get(Math.max(0, SHAPE_NAMES.indexOf(c.defaultQuestShape.equals("default") ? "" : c.defaultQuestShape))));
        values.put("always_invisible", String.valueOf(c.alwaysInvisible));
        values.put("hide_details", String.valueOf(c.hideQuestDetailsUntilStartable));
        values.put("hide_deps_visible", String.valueOf(c.hideQuestUntilDepsVisible));
        values.put("hide_deps_complete", String.valueOf(c.hideQuestUntilDepsComplete));
        values.put("progression_mode", MODES.get(c.progressionMode.ordinal()));
        values.put("repeatable", String.valueOf(c.defaultRepeatable));
        values.put("sequential", String.valueOf(c.requireSequentialTasks));
        values.put("hide_dep_lines", String.valueOf(c.defaultHideDependencyLines));
        initial.putAll(values);

        section("Chapter");
        text("Title", () -> values.get("title"), v -> values.put("title", v)).tip("The name of the chapter. Formatting codes such as &6 work.");
        section("Chapter → Appearance");
        choice("Default Quest Shape", SHAPE_LABELS, () -> values.get("shape"), v -> values.put("shape", v))
                .tip("The shape of quests in this chapter that do not choose their own.").defaults("Default");
        section("Chapter → Visibility");
        flag("Always Invisible", "always_invisible", "The chapter is not shown in the chapter list.");
        flag("Hide Quest Details Until Startable", "hide_details", "Quests in this chapter hide their text and tasks until they can be started.");
        flag("Hide Quests Until Dependencies Visible", "hide_deps_visible", "Quests stay hidden until the quests they need are visible.");
        flag("Hide Quests Until Dependencies Complete", "hide_deps_complete", "Quests stay hidden until the quests they need are completed.");
        section("Chapter → Misc");
        choice("Default 'Progression Mode'", MODES, () -> values.get("progression_mode"), v -> values.put("progression_mode", v))
                .tip("Linear: quests must be complete before dependants start. Flexible: tasks can be progressed any time.").defaults("Default");
        flag("Default 'Repeatable Quest'", "repeatable", "Quests in this chapter can be repeated unless they say otherwise.");
        flag("Sequential Task Completion", "sequential", "Tasks have to be completed in order.");
        flag("Default 'Hide Dependency Lines'", "hide_dep_lines", "Quests hide their dependency lines unless they say otherwise (lines still show on hover).");
    }

    private void flag(String label, String key, String tip) {
        bool(label, () -> Boolean.parseBoolean(values.get(key)), v -> values.put(key, String.valueOf(v))).tip(tip).defaults("False");
    }

    @Override
    protected void accepted() {
        String title = values.get("title").strip();
        if (!title.equals(initial.get("title").strip()) && !title.isEmpty()) {
            TableEdits.send("rename_chapter", DataNode.Compound.empty().with("id", chapter.id).with("title", title));
        }
        for (Map.Entry<String, String> e : values.entrySet()) {
            String key = e.getKey();
            if (key.equals("title") || e.getValue().equals(initial.get(key))) {
                continue;
            }
            String v = e.getValue();
            if (key.equals("shape")) {
                v = SHAPE_NAMES.get(SHAPE_LABELS.indexOf(v));
            } else if (key.equals("progression_mode")) {
                v = v.toUpperCase(java.util.Locale.ROOT);
            }
            TableEdits.send("set_chapter_prop", DataNode.Compound.empty().with("id", chapter.id).with("key", key).with("value", v));
        }
    }

    @Override
    protected void cancelled() {
        // nothing was sent
    }
}
