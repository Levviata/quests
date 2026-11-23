/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.data.DataNode;

/** The "Player Preferences" window: grouped settings with coloured values, Accept and Cancel. */
@SideOnly(Side.CLIENT)
final class PreferencesScreen extends SettingsScreen {

    private final DataNode.Compound before = ClientPrefs.snapshot();

    PreferencesScreen(GuiScreen parent) {
        super(parent, "Player Preferences");
        section("User Interface");
        bool("Backspace Key for View History", () -> ClientPrefs.backspaceHistory, v -> ClientPrefs.backspaceHistory = v)
                .tip("Not used yet: going back to the previous quest.").defaults("True");
        bool("Chapter Panel Pinned Open", () -> ClientPrefs.chapterPanelPinned, v -> ClientPrefs.chapterPanelPinned = v)
                .tip("Keep the chapter panel open instead of hiding it when the mouse leaves.").defaults("False");
        bool("Old-style Scroll Wheel Behavior", () -> ClientPrefs.oldScrollWheel, v -> ClientPrefs.oldScrollWheel = v)
                .tip("When false, scroll wheel scrolls up & down: hold Shift to pan, Ctrl to zoom\nWhen true, scroll wheel only zooms (1.19 and earlier behavior)").defaults("False");
        bool("Show Icon for Locked Quests", () -> ClientPrefs.showLockIcon, v -> ClientPrefs.showLockIcon = v)
                .tip("Draw the small lock on quests whose dependencies are not complete.").defaults("True");
        section("Notifications");
        choice("Completion Notification Style", List.of("Toast", "Chat", "None"), () -> ClientPrefs.completionNotification, v -> ClientPrefs.completionNotification = v)
                .tip("How you are told that a quest or chapter was completed.").defaults("Toast");
        bool("Play Completion Sounds", () -> ClientPrefs.completionSounds, v -> ClientPrefs.completionSounds = v).defaults("True");
        choice("Reward Notification Style", List.of("Toast", "Chat", "None"), () -> ClientPrefs.rewardNotification, v -> ClientPrefs.rewardNotification = v)
                .tip("How you are told that a reward was collected.").defaults("Toast");
        section("Pinned Quests");
        choice("Auto-pin Follows", List.of("Chapter", "Quest", "None"), () -> ClientPrefs.autoPinFollows, v -> ClientPrefs.autoPinFollows = v)
                .tip("Not used yet.").defaults("Chapter");
        bool("Exclude Flexible-Mode Quests", () -> ClientPrefs.excludeFlexible, v -> ClientPrefs.excludeFlexible = v).defaults("True");
        choice("Pinned Quests Panel Position", List.of("Right", "Left"), () -> ClientPrefs.pinnedPosition, v -> ClientPrefs.pinnedPosition = v).defaults("Right");
        number("Pinned Quests Panel Scaling", false, () -> String.valueOf(ClientPrefs.pinnedScaling), v -> ClientPrefs.pinnedScaling = Math.max(0.25, Math.min(3.0, Double.parseDouble(v)))).defaults("0.75");
        number("Pinned Quests Panel X Inset", true, () -> String.valueOf(ClientPrefs.pinnedXInset), v -> ClientPrefs.pinnedXInset = Integer.parseInt(v)).defaults("2");
        number("Pinned Quests Panel Y Inset", true, () -> String.valueOf(ClientPrefs.pinnedYInset), v -> ClientPrefs.pinnedYInset = Integer.parseInt(v)).defaults("2");
        choice("Pinned Tracker Visibility", List.of("Quests and Tasks", "Quests only", "Hidden"), () -> ClientPrefs.trackerVisibility, v -> ClientPrefs.trackerVisibility = v).defaults("Quests and Tasks");
        section("Translations");
        text("Fallback Locale", () -> ClientPrefs.fallbackLocale, v -> ClientPrefs.fallbackLocale = v).tip("Not used yet.");
        bool("Highlight missing translations", () -> ClientPrefs.highlightMissing, v -> ClientPrefs.highlightMissing = v).tip("Not used yet.").defaults("True");
        text("Locale Override", () -> ClientPrefs.localeOverride, v -> ClientPrefs.localeOverride = v).tip("Not used yet.");
        section("Change History Display");
        bool("Always Show Change History", () -> ClientPrefs.alwaysShowHistory, v -> ClientPrefs.alwaysShowHistory = v).tip("Keep the list of recent changes on the map instead of letting it fade.").defaults("False");
        number("Change History Show Time (seconds)", false, () -> String.valueOf(ClientPrefs.historySeconds), v -> ClientPrefs.historySeconds = Double.parseDouble(v)).defaults("5.00");
        number("Changelog Font Scale", false, () -> String.valueOf(ClientPrefs.changelogScale), v -> ClientPrefs.changelogScale = Double.parseDouble(v)).defaults("0.75");
        number("Max Line Count", true, () -> String.valueOf(ClientPrefs.maxLines), v -> ClientPrefs.maxLines = Integer.parseInt(v)).defaults("10");
    }

    @Override
    protected boolean showDefault() {
        return true;
    }

    @Override
    protected void accepted() {
        ClientPrefs.save();
    }

    @Override
    protected void cancelled() {
        ClientPrefs.restore(before);
    }
}
