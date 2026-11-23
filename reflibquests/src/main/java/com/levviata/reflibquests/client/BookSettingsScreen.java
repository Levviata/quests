/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.QuestBook;
import com.levviata.reflibquests.text.RichText;

@SideOnly(Side.CLIENT)
final class BookSettingsScreen extends SettingsScreen {

    private static final List<String> SHAPE_LABELS = List.of("Circle", "Square", "Rounded Square", "Diamond", "Pentagon",
            "Hexagon", "Octagon", "Heart", "Gear", "None");
    private static final List<String> SHAPE_NAMES = List.of("circle", "square", "rsquare", "diamond", "pentagon",
            "hexagon", "octagon", "heart", "gear", "none");
    private static final List<String> AUTO_LABELS = List.of("Disabled", "Enabled", "Enabled, No Toast", "Enabled, Invisible");
    private static final List<String> AUTO_VALUES = List.of("disabled", "enabled", "no_toast", "invisible");
    private static final List<String> MODES = List.of("Linear", "Flexible");

    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, String> initial = new LinkedHashMap<>();

    BookSettingsScreen(GuiScreen parent, QuestBook book) {
        super(parent, "Edit File");
        DataNode.Compound s = book.settings;
        values.put("title", s.getString("title", "Quest Book"));
        values.put("icon", ChapterPropertiesScreen.ICON_KEEP);
        values.put("tags", tagsOf(s));
        values.put("disable_gui", String.valueOf(s.getBool("disable_gui", false)));
        values.put("drop_book_on_death", String.valueOf(s.getBool("drop_book_on_death", false)));
        values.put("emergency_items_cooldown", String.valueOf(s.getInt("emergency_items_cooldown", 300)));
        values.put("drop_loot_crates", String.valueOf(s.getBool("drop_loot_crates", false)));
        values.put("fallback_locale", book.fallbackLocale);
        values.put("grid_scale", String.valueOf(s.getDouble("grid_scale", 0.5)));
        values.put("hide_excluded_quests", String.valueOf(s.getBool("hide_excluded_quests", false)));
        values.put("detection_delay", String.valueOf(s.getInt("detection_delay", 20)));
        values.put("lock_message", s.getString("lock_message", ""));
        values.put("progression_mode", MODES.get(book.progressionMode.name().equals("FLEXIBLE") ? 1 : 0));
        values.put("pause_game", String.valueOf(s.getBool("pause_game", false)));
        values.put("show_lock_icons", String.valueOf(s.getBool("show_lock_icons", ClientPrefs.showLockIcon)));
        values.put("suppress_all_autoclaiming", String.valueOf(s.getBool("suppress_all_autoclaiming", false)));
        values.put("disable_toast", String.valueOf(s.getBool("disable_toast", false)));
        values.put("default_quest_shape", SHAPE_LABELS.get(Math.max(0, SHAPE_NAMES.indexOf(s.getString("default_quest_shape", "circle")))));
        values.put("default_consume_items", String.valueOf(s.getBool("default_consume_items", false)));
        values.put("default_quest_disable_jei", String.valueOf(s.getBool("default_quest_disable_jei", false)));
        values.put("default_reward_team", String.valueOf(s.getBool("default_reward_team", false)));
        values.put("default_autoclaim_rewards", AUTO_LABELS.get(Math.max(0, AUTO_VALUES.indexOf(s.getString("default_autoclaim_rewards", "disabled")))));
        DataNode.Compound byType = s.getCompound("default_reward_team_by_type");
        for (String[] t : TEAM_TYPES) {
            String k = "ftbquests:" + t[0];
            values.put("reward_team_" + t[0], !byType.has(k) ? "Default" : byType.getBool(k, false) ? "True" : "False");
        }
        DataNode.Compound loot = s.getCompound("loot_crate_no_drop");
        values.put("loot_boss", String.valueOf(loot.getInt("boss", 0)));
        values.put("loot_monster", String.valueOf(loot.getInt("monster", 600)));
        values.put("loot_passive", String.valueOf(loot.getInt("passive", 4000)));
        initial.putAll(values);

        section("Quest Book");
        text("Title", () -> values.get("title"), v -> values.put("title", v)).tip("The name of the quest book. Formatting codes such as &6 work.");
        IconPicker.decorate(choice("Icon", ChapterPropertiesScreen.ICON_CHOICES, () -> values.get("icon"), v -> values.put("icon", v))
                .tip("The icon of the quest book.").defaults("None"), book.settings.getCompound("icon"));
        text("Tags", () -> values.get("tags"), v -> values.put("tags", v)).tip("Words that mark the book, separated by commas.");
        unusedBool("Disable GUI", "disable_gui", "The quest book cannot be opened by players who cannot edit it.");
        unusedBool("Drop FTB Quests Book on Death", "drop_book_on_death", "Stored in the book; Reflib Quests does not use it yet.");
        number("Emergency Items Cooldown", true, () -> values.get("emergency_items_cooldown"),
                v -> values.put("emergency_items_cooldown", String.valueOf(Math.max(0L, Long.parseLong(v)))))
                .tip("Cooldown timer for emergency items to respawn.Stored in the book; Reflib Quests does not use it yet.");
        unusedBool("Entities Drop Loot Crates", "drop_loot_crates", "Stored in the book; Reflib Quests does not use it yet.");
        text("Fallback Locale", () -> values.get("fallback_locale"), v -> values.put("fallback_locale", v))
                .tip("The locale used for translations not found in the current locale\n"
                        + "This can be overridden on the client via Player Preferences\nDefaults to 'en_us' if empty");
        number("Grid Scale", false, () -> values.get("grid_scale"),
                v -> values.put("grid_scale", String.valueOf(Math.max(0.0625, Math.min(16.0, Double.parseDouble(v))))))
                .tip("Stored in the book; Reflib Quests does not use it yet.");
        unusedBool("Hide Quests in excluded questlines", "hide_excluded_quests",
                "If false, excluded quests will remain visible but grayed out\nStored in the book; Reflib Quests does not use it yet.");
        number("Item Auto-Detection Minimum Interval", true, () -> values.get("detection_delay"),
                v -> values.put("detection_delay", String.valueOf(Math.max(1, Long.parseLong(v)))))
                .tip("Minimum interval (in ticks) between checks of a player's inventory when it changes\n"
                        + "Higher values are kinder on server TPS, especially if players inventories change frequently,\n"
                        + "but mean longer delays for quest auto-completion messages.");
        text("Lock Message", () -> values.get("lock_message"), v -> values.put("lock_message", v)).tip("Stored in the book; Reflib Quests does not use it yet.");
        choice("Progression Mode", MODES, () -> values.get("progression_mode"), v -> values.put("progression_mode", v))
                .tip("Linear: quest must be complete before dependant quests are started\n"
                        + "Flexible: tasks in dependant quests can be progressed at any time,\n"
                        + "  but dependant quests can't be completed until dependency is completed");
        bool("Quest Screen Pauses SSP Game", () -> Boolean.parseBoolean(values.get("pause_game")),
                v -> values.put("pause_game", String.valueOf(v))).tip("Does not apply to multiplayer games");
        bool("Show Icon for Locked Quests", () -> Boolean.parseBoolean(values.get("show_lock_icons")),
                v -> values.put("show_lock_icons", String.valueOf(v)))
                .tip("Quests that wait for their dependencies show a small lock.");
        bool("Suppress all Reward Autoclaiming", () -> Boolean.parseBoolean(values.get("suppress_all_autoclaiming")),
                v -> values.put("suppress_all_autoclaiming", String.valueOf(v)))
                .tip("If true, no rewards can be autoclaimed, including via the \"Claim All\" button on the reward selector screen\n"
                        + "May be useful during pack development to ensure testers read quests...");
        bool("Disable Completion Toast", () -> Boolean.parseBoolean(values.get("disable_toast")),
                v -> values.put("disable_toast", String.valueOf(v))).tip("No pop-up when any quest of the book is completed.");

        section("Quest Book \u2192 Defaults");
        bool("Quest 'Disable JEI Recipe'", () -> Boolean.parseBoolean(values.get("default_quest_disable_jei")),
                v -> values.put("default_quest_disable_jei", String.valueOf(v)));
        choice("Quest Shape", SHAPE_LABELS, () -> values.get("default_quest_shape"), v -> values.put("default_quest_shape", v));
        choice("Reward 'Auto-claim'", AUTO_LABELS, () -> values.get("default_autoclaim_rewards"), v -> values.put("default_autoclaim_rewards", v));
        bool("Reward 'Team Reward'", () -> Boolean.parseBoolean(values.get("default_reward_team")),
                v -> values.put("default_reward_team", String.valueOf(v)));
        bool("Task 'Consume Items'", () -> Boolean.parseBoolean(values.get("default_consume_items")),
                v -> values.put("default_consume_items", String.valueOf(v)));

        section("Quest Book \u2192 Defaults \u2192 Per-Team Reward by Type");
        for (String[] t : TEAM_TYPES) {
            choice(t[1], TEAM_LABELS, () -> values.get("reward_team_" + t[0]), v -> values.put("reward_team_" + t[0], v))
                    .tip("When true, this is one reward for the whole team; only the submitting player receives it. Default follows 'Reward Team Reward'.")
                    .defaults("Default");
        }

        section("Quest Book \u2192 Entity Loot Crate Drop Empty Weight");
        number("Boss", true, () -> values.get("loot_boss"), v -> values.put("loot_boss", String.valueOf(Math.max(0L, Long.parseLong(v))))).tip("Stored in the book; Reflib Quests does not use it yet.");
        number("Monster", true, () -> values.get("loot_monster"), v -> values.put("loot_monster", String.valueOf(Math.max(0L, Long.parseLong(v))))).tip("Stored in the book; Reflib Quests does not use it yet.");
        number("Passive", true, () -> values.get("loot_passive"), v -> values.put("loot_passive", String.valueOf(Math.max(0L, Long.parseLong(v))))).tip("Stored in the book; Reflib Quests does not use it yet.");
    }

    private static final String[][] TEAM_TYPES = {
        {"advancement", "Advancement"}, {"all_table", "All Table Reward"}, {"choice", "Choice Reward"}, {"command", "Command"},
        {"custom", "Custom"}, {"item", "Item"}, {"loot", "Loot Reward"}, {"random", "Random Reward"}, {"gamestage", "Stage"},
        {"toast", "Toast"}, {"xp", "XP"}, {"xp_levels", "XP Levels"}};
    private static final List<String> TEAM_LABELS = List.of("Default", "False", "True");

    private void unusedBool(String label, String key, String tip) {
        bool(label, () -> Boolean.parseBoolean(values.get(key)), v -> values.put(key, String.valueOf(v))).tip(tip).defaults("False");
    }

    private static String tagsOf(DataNode.Compound s) {
        StringBuilder sb = new StringBuilder();
        for (DataNode n : s.getList("tags")) {
            if (n instanceof DataNode.Str str) {
                sb.append(sb.length() > 0 ? ", " : "").append(str.value());
            }
        }
        return sb.toString();
    }

    @Override
    protected void accepted() {
        for (Map.Entry<String, String> e : values.entrySet()) {
            String key = e.getKey();
            String v = e.getValue();
            if (v.equals(initial.get(key))) {
                continue;
            }
            if (key.equals("icon")) {
                DataNode.Compound iconEdit = IconPicker.edit("book", "", v);
                if (iconEdit != null) {
                    TableEdits.send("set_icon", iconEdit);
                }
                continue;
            }
            switch (key) {
                case "default_quest_shape" -> v = SHAPE_NAMES.get(SHAPE_LABELS.indexOf(v));
                case "reward_team_item", "reward_team_choice", "reward_team_all_table", "reward_team_random", "reward_team_loot",
                        "reward_team_command", "reward_team_custom", "reward_team_xp", "reward_team_xp_levels",
                        "reward_team_advancement", "reward_team_toast", "reward_team_gamestage" -> v = v.toLowerCase(Locale.ROOT);
                case "default_autoclaim_rewards" -> v = AUTO_VALUES.get(AUTO_LABELS.indexOf(v));
                case "progression_mode" -> v = v.toUpperCase(Locale.ROOT);
                case "title" -> v = RichText.stripCodes(v).isEmpty() ? initial.get("title") : v.strip();
                default -> { }
            }
            TableEdits.send("set_book_prop", DataNode.Compound.empty().with("key", key).with("value", v));
        }
    }

    @Override
    protected void cancelled() {

    }
}
