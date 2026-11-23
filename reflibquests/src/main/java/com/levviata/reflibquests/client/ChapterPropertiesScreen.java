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
import com.levviata.reflibquests.model.Definitions.Tristate;
import com.levviata.reflibquests.text.RichText;

@SideOnly(Side.CLIENT)
final class ChapterPropertiesScreen extends SettingsScreen {

    private static final List<String> SHAPE_LABELS = List.of("Default", "Circle", "Square", "Rounded Square", "Diamond", "Pentagon",
            "Hexagon", "Octagon", "Heart", "Gear", "None");
    private static final List<String> SHAPE_NAMES = List.of("", "circle", "square", "rsquare", "diamond", "pentagon",
            "hexagon", "octagon", "heart", "gear", "none");
    static final String ICON_KEEP = "Unchanged";
    static final List<String> ICON_CHOICES = List.of(ICON_KEEP, "Held Item", "Clear");
    private static final List<String> MODES = List.of("Default", "Linear", "Flexible");
    private static final List<String> TRI = List.of("Default", "True", "False");

    private final Chapter chapter;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, String> initial = new LinkedHashMap<>();

    ChapterPropertiesScreen(GuiScreen parent, Chapter c) {
        super(parent, "Chapter Properties: " + RichText.stripCodes(c.title.isEmpty() ? c.id : c.title));
        this.chapter = c;
        values.put("title", c.title);
        values.put("icon", ICON_KEEP);
        values.put("tags", String.join(", ", c.tags));
        values.put("subtitle", String.join("\\n", c.subtitle));
        values.put("disable_toast", String.valueOf(c.disableToast));
        values.put("min_width", String.valueOf(c.minWindowWidth));
        values.put("shape", SHAPE_LABELS.get(Math.max(0, SHAPE_NAMES.indexOf(c.defaultQuestShape.equals("default") ? "" : c.defaultQuestShape))));
        values.put("quest_size", String.valueOf(c.defaultQuestSize));
        values.put("preset", c.preset.isEmpty() ? "None" : c.preset);
        values.put("always_invisible", String.valueOf(c.alwaysInvisible));
        values.put("hide_dep_lines", String.valueOf(c.defaultHideDependencyLines));
        values.put("hide_details", String.valueOf(c.hideQuestDetailsUntilStartable));
        values.put("hide_deps_complete", String.valueOf(c.hideQuestUntilDepsComplete));
        values.put("hide_deps_visible", String.valueOf(c.hideQuestUntilDepsVisible));
        values.put("hide_text", String.valueOf(c.hideTextUntilComplete));
        values.put("autofocus", c.autofocusId);
        values.put("progression_mode", MODES.get(c.progressionMode.ordinal()));
        values.put("repeatable", String.valueOf(c.defaultRepeatable));
        values.put("consume_items", TRI.get(c.defaultConsumeItems.ordinal()));
        values.put("sequential", String.valueOf(c.requireSequentialTasks));
        initial.putAll(values);

        section("Chapter");
        text("Title", () -> values.get("title"), v -> values.put("title", v)).tip("Name of the chapter. Color codes like &6 work.");
        IconPicker.decorate(choice("Icon", ICON_CHOICES, () -> values.get("icon"), v -> values.put("icon", v))
                .tip("Icon of the chapter. None shows the quests' icons instead.").defaults("None"), chapter.icon);
        text("Tags", () -> values.get("tags"), v -> values.put("tags", v)).tip("Tags for the chapter, separated by commas.");
        text("Subtitle", () -> values.get("subtitle"), v -> values.put("subtitle", v)).tip("Goes under the chapter name. \\n makes a new line.");
        flag("Disable Completion Toast", "disable_toast", "No pop-up when a quest here is completed.");

        section("Chapter → Appearance");
        number("Min Quest Window Width (default)", true, () -> values.get("min_width"),
                v -> values.put("min_width", String.valueOf(Math.max(0, Math.min(1000, Integer.parseInt(v))))))
                .tip("Quest windows are at least this wide. 0 keeps the normal width.").defaults("0");
        choice("Default Quest Shape", SHAPE_LABELS, () -> values.get("shape"), v -> values.put("shape", v))
                .tip("Shape for quests here that don't pick their own.").defaults("Default");
        number("Default Quest Size", false, () -> values.get("quest_size"),
                v -> values.put("quest_size", String.valueOf(Math.max(0.0625, Math.min(64.0, Double.parseDouble(v))))))
                .tip("Size new quests here start with.").defaults("1");
        java.util.List<String> presetNames = new java.util.ArrayList<>();
        presetNames.add("None");
        com.levviata.reflibquests.model.QuestBook presetBook = com.levviata.reflibquests.client.ClientQuests.book();
        if (presetBook != null) {
            presetNames.addAll(presetBook.settings.getCompound("presets").entries().keySet());
        }
        if (!presetNames.contains(values.get("preset"))) {
            presetNames.add(values.get("preset"));
        }
        choice("Default Visual Preset", presetNames, () -> values.get("preset"), v -> values.put("preset", v))
                .tip("Preset every quest here uses unless it picks its own").defaults("None");

        section("Chapter → Visibility");
        flag("Always Invisible", "always_invisible", "Chapter doesn't show in the chapter list.");
        flag("Hide Dependency Lines by Default", "hide_dep_lines", "Quests hide their dependency lines unless they say otherwise (still shown on hover).");
        flag("Hide Details Until You Can Start", "hide_details", "Quests here hide their text and tasks until they can be started.");
        flag("Hide Until Dependencies Are Done", "hide_deps_complete", "Quests stay hidden until the ones they need are completed.");
        flag("Hide Until Dependencies Show Up", "hide_deps_visible", "Quests stay hidden until the ones they need are visible.");
        flag("Hide Text Until Done", "hide_text", "You can open the quest, but the description stays blank until it's completed.");

        section("Chapter → Misc");
        text("Autofocus Quest ID", () -> values.get("autofocus"), v -> values.put("autofocus", v))
                .tip("Quest the chapter opens centered on. Empty for none.");
        choice("Default 'Progression Mode'", MODES, () -> values.get("progression_mode"), v -> values.put("progression_mode", v))
                .tip("Linear: finish quests before dependants start. Flexible: tasks can progress any time.").defaults("Default");
        flag("Default 'Repeatable Quest'", "repeatable", "Quests here can be repeated unless they say otherwise.");
        choice("Item Tasks Consume Items by Default", TRI, () -> values.get("consume_items"), v -> values.put("consume_items", v))
                .tip("Whether item tasks here take the items when handed in. Default follows the book.").defaults("Default");
        flag("Sequential Task Completion", "sequential", "Tasks must be done in order.");
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
            if (key.equals("icon")) {
                DataNode.Compound iconEdit = IconPicker.edit("chapter", chapter.id, e.getValue());
                if (iconEdit != null) {
                    TableEdits.send("set_icon", iconEdit);
                }
                continue;
            }
            String v = e.getValue();
            switch (key) {
                case "shape" -> v = SHAPE_NAMES.get(SHAPE_LABELS.indexOf(v));
                case "progression_mode" -> v = v.toUpperCase(java.util.Locale.ROOT);
                case "consume_items" -> v = v.toLowerCase(java.util.Locale.ROOT);
                default -> { }
            }
            TableEdits.send("set_chapter_prop", DataNode.Compound.empty().with("id", chapter.id).with("key", key).with("value", v));
        }
    }

    @Override
    protected void cancelled() {

    }
}
