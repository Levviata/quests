/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.DependencyMode;
import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Tristate;
import com.levviata.reflibquests.net.QuestNet;
import com.levviata.reflibquests.text.RichText;

/** "Quest Properties": the quest's settings in groups, with a hover tip for each; Accept sends only what changed. */
@SideOnly(Side.CLIENT)
final class QuestPropertiesScreen extends SettingsScreen {

    private static final List<String> SHAPE_LABELS = List.of("Default", "Circle", "Square", "Rounded Square", "Diamond", "Pentagon",
            "Hexagon", "Octagon", "Heart", "Gear", "None");
    private static final List<String> SHAPE_NAMES = List.of("", "circle", "square", "rsquare", "diamond", "pentagon",
            "hexagon", "octagon", "heart", "gear", "none");
    private static final List<String> TRI = List.of("Default", "True", "False");
    private static final List<String> REQUIREMENTS = List.of("All Completed", "One Completed", "All Started", "One Started");

    private final Quest quest;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, String> initial = new LinkedHashMap<>();

    QuestPropertiesScreen(GuiScreen parent, Quest q) {
        super(parent, "Quest Properties: " + RichText.stripCodes(QuestText.rawTitle(q)));
        this.quest = q;
        values.put("title", q.title);
        values.put("subtitle", q.subtitle);
        values.put("shape", SHAPE_LABELS.get(Math.max(0, SHAPE_NAMES.indexOf(q.shape.equals("default") ? "" : q.shape))));
        values.put("size", String.valueOf(q.size));
        values.put("hide_details", tri(q.hideDetailsUntilStartable));
        values.put("hide_deps_complete", tri(q.hideUntilDepsComplete));
        values.put("hide_deps_visible", tri(q.hideUntilDepsVisible));
        values.put("invisible", String.valueOf(q.invisible));
        values.put("invisible_until_tasks", String.valueOf(q.invisibleUntilTasks));
        values.put("dependency_requirement", REQUIREMENTS.get(q.dependencyMode.ordinal()));
        values.put("hide_dep_lines", tri(q.hideDependencyLines));
        values.put("hide_dependent_lines", tri(q.hideDependentLines));
        values.put("min_required_dependencies", String.valueOf(q.minRequiredDependencies));
        values.put("optional", String.valueOf(q.optional));
        values.put("repeatable", tri(q.canRepeat));
        values.put("sequential", tri(q.requireSequentialTasks));
        initial.putAll(values);

        section("Quest");
        text("Title", () -> values.get("title"), v -> values.put("title", v)).tip("The name of the quest. Formatting codes such as &6 work.");
        text("Subtitle", () -> values.get("subtitle"), v -> values.put("subtitle", v)).tip("A short line shown under the sections of the quest window.");
        section("Quest → Appearance");
        choice("Shape", SHAPE_LABELS, () -> values.get("shape"), v -> values.put("shape", v))
                .tip("How the quest is drawn on the map.").defaults("Default");
        number("Size", false, () -> values.get("size"), v -> values.put("size", String.valueOf(Math.max(0.25, Math.min(4.0, Double.parseDouble(v))))))
                .tip("Size of the quest on the map.").defaults("1.00");
        section("Quest → Visibility");
        triState("Hide Details Until Startable", "hide_details", "If true then quest details (text and tasks) can't be viewed until the quest can be started.");
        triState("Hide Quest Until Dependencies Complete", "hide_deps_complete", "The quest stays hidden until every quest it needs is completed.");
        triState("Hide Quest Until Dependencies Visible", "hide_deps_visible", "The quest stays hidden until the quests it needs are visible.");
        bool("Invisible Until Completed", () -> Boolean.parseBoolean(values.get("invisible")), v -> values.put("invisible", String.valueOf(v)))
                .tip("The quest is not shown until it is completed.").defaults("False");
        number("Invisible until X tasks are completed", true, () -> values.get("invisible_until_tasks"), v -> values.put("invisible_until_tasks", String.valueOf(Math.max(0, Math.min(20, Integer.parseInt(v))))))
                .tip("The quest is not shown until this many of its tasks are completed. 0 turns it off.").defaults("0");
        section("Quest → Dependencies");
        choice("Dependency Requirement", REQUIREMENTS, () -> values.get("dependency_requirement"), v -> values.put("dependency_requirement", v))
                .tip("What has to be true of the quests this one needs.").defaults("All Completed");
        triState("Hide Dependency Lines", "hide_dep_lines", "Hide the lines to the quests this quest needs (they still show while it is hovered).");
        triState("Hide Dependent Lines", "hide_dependent_lines", "Hide the lines to the quests that need this quest (they still show while it is hovered).");
        number("Min Required Dependencies", true, () -> values.get("min_required_dependencies"), v -> values.put("min_required_dependencies", String.valueOf(Math.max(0, Math.min(50, Integer.parseInt(v))))))
                .tip("If above 0, this many of the dependencies are enough.").defaults("0");
        section("Quest → Misc");
        bool("Optional Quest", () -> Boolean.parseBoolean(values.get("optional")), v -> values.put("optional", String.valueOf(v)))
                .tip("Optional quests are not needed to finish the chapter.").defaults("False");
        triState("Repeatable Quest", "repeatable", "The quest can be done again after its rewards are claimed.");
        triState("Sequential Task Completion", "sequential", "Tasks have to be completed in order.");
    }

    private void triState(String label, String key, String tip) {
        choice(label, TRI, () -> values.get(key), v -> values.put(key, v)).tip(tip).defaults("Default");
    }

    private static String tri(Tristate t) {
        return t == Tristate.TRUE ? "True" : t == Tristate.FALSE ? "False" : "Default";
    }

    private void prop(String key, String value) {
        TableEdits.send("set_prop", DataNode.Compound.empty().with("id", quest.id).with("key", key).with("value", value));
    }

    @Override
    protected void accepted() {
        boolean textChanged = !values.get("title").equals(initial.get("title")) || !values.get("subtitle").equals(initial.get("subtitle"));
        if (textChanged) {
            List<DataNode> lines = new ArrayList<>();
            for (String line : quest.description) {
                lines.add(new DataNode.Str(line));
            }
            TableEdits.send("set_text", DataNode.Compound.empty().with("id", quest.id).with("title", values.get("title"))
                    .with("subtitle", values.get("subtitle")).with("desc", new DataNode.Lst(lines)));
        }
        for (Map.Entry<String, String> e : values.entrySet()) {
            String key = e.getKey();
            if (key.equals("title") || key.equals("subtitle") || e.getValue().equals(initial.get(key))) {
                continue;
            }
            String v = e.getValue();
            switch (key) {
                case "shape":
                    prop(key, SHAPE_NAMES.get(SHAPE_LABELS.indexOf(v)));
                    break;
                case "dependency_requirement":
                    prop(key, DependencyMode.values()[REQUIREMENTS.indexOf(v)].name());
                    break;
                case "hide_details":
                case "hide_deps_complete":
                case "hide_deps_visible":
                case "hide_dep_lines":
                case "hide_dependent_lines":
                case "repeatable":
                case "sequential":
                    prop(key, v.toLowerCase(java.util.Locale.ROOT));
                    break;
                default:
                    prop(key, v);
                    break;
            }
        }
    }

    @Override
    protected void cancelled() {
        // nothing was sent, so nothing to undo
    }
}
