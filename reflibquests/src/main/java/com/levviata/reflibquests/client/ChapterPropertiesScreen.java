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

/** "Chapter Properties", in FTB Quests' own order and wording. Accept sends only what changed. */
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
        text("Title", () -> values.get("title"), v -> values.put("title", v)).tip("The name of the chapter. Formatting codes such as &6 work.");
        choice("Icon", ICON_CHOICES, () -> values.get("icon"), v -> values.put("icon", v))
                .tip("Held Item takes the item in your main hand. Clear lets the chapter show its quests' icons.").defaults(ICON_KEEP);
        text("Tags", () -> values.get("tags"), v -> values.put("tags", v)).tip("Words that mark the chapter, separated by commas.");
        text("Subtitle", () -> values.get("subtitle"), v -> values.put("subtitle", v)).tip("Shown under the chapter name. Use \\n for a new line.");
        flag("Disable Completion Toast", "disable_toast", "No pop-up when a quest of this chapter is completed.");

        section("Chapter → Appearance");
        number("Default Min Opened Quest Window Width", true, () -> values.get("min_width"),
                v -> values.put("min_width", String.valueOf(Math.max(0, Math.min(1000, Integer.parseInt(v))))))
                .tip("The quest window is at least this wide. 0 keeps the usual width.").defaults("0");
        choice("Default Quest Shape", SHAPE_LABELS, () -> values.get("shape"), v -> values.put("shape", v))
                .tip("The shape of quests in this chapter that do not choose their own.").defaults("Default");
        number("Default Quest Size", false, () -> values.get("quest_size"),
                v -> values.put("quest_size", String.valueOf(Math.max(0.0625, Math.min(64.0, Double.parseDouble(v))))))
                .tip("The size new quests of this chapter start with.").defaults("1");

        section("Chapter → Visibility");
        flag("Always Invisible", "always_invisible", "The chapter is not shown in the chapter list.");
        flag("Default 'Hide Dependency Lines'", "hide_dep_lines", "Quests hide their dependency lines unless they say otherwise (lines still show on hover).");
        flag("Hide Quest Details Until Startable", "hide_details", "Quests in this chapter hide their text and tasks until they can be started.");
        flag("Hide Quests Until Dependencies Complete", "hide_deps_complete", "Quests stay hidden until the quests they need are completed.");
        flag("Hide Quests Until Dependencies Visible", "hide_deps_visible", "Quests stay hidden until the quests they need are visible.");
        flag("Hide Text Until Quest is Completed", "hide_text", "Quest details can be opened, but no description text is displayed until the quest is completed.");

        section("Chapter → Misc");
        text("Autofocus Quest ID", () -> values.get("autofocus"), v -> values.put("autofocus", v))
                .tip("The quest the chapter opens centred on. Empty for none.");
        choice("Default 'Progression Mode'", MODES, () -> values.get("progression_mode"), v -> values.put("progression_mode", v))
                .tip("Linear: quests must be complete before dependants start. Flexible: tasks can be progressed any time.").defaults("Default");
        flag("Default 'Repeatable Quest'", "repeatable", "Quests in this chapter can be repeated unless they say otherwise.");
        choice("Default Item Task 'Consume Items'", TRI, () -> values.get("consume_items"), v -> values.put("consume_items", v))
                .tip("Whether item tasks of this chapter take the items when they are handed in. Default follows the book's setting.").defaults("Default");
        flag("Sequential Task Completion", "sequential", "Tasks have to be completed in order.");
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
                TableEdits.send("set_icon", DataNode.Compound.empty().with("kind", "chapter").with("id", chapter.id)
                        .with("mode", e.getValue().equals("Clear") ? "clear" : "held"));
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
        // nothing was sent
    }
}
