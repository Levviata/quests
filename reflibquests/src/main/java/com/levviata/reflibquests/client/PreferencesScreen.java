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

@SideOnly(Side.CLIENT)
final class PreferencesScreen extends SettingsScreen {

    private final DataNode.Compound before = ClientPrefs.snapshot();

    PreferencesScreen(GuiScreen parent) {
        super(parent, "Player Preferences");
        section("User Interface");
        bool("Backspace Goes Back", () -> ClientPrefs.backspaceHistory, v -> ClientPrefs.backspaceHistory = v)
                .tip("Not used yet. Would take you back to the previous quest.").defaults("True");
        bool("Keep Chapter Panel Open", () -> ClientPrefs.chapterPanelPinned, v -> ClientPrefs.chapterPanelPinned = v)
                .tip("Chapter panel stays open instead of hiding when the mouse leaves.").defaults("False");
        bool("Old Scroll Wheel", () -> ClientPrefs.oldScrollWheel, v -> ClientPrefs.oldScrollWheel = v)
                .tip("Off: wheel scrolls up and down, Shift pans, Ctrl zooms\nOn: wheel only zooms, like 1.19 and before").defaults("False");
        bool("Show Lock on Locked Quests", () -> ClientPrefs.showLockIcon, v -> ClientPrefs.showLockIcon = v)
                .tip("Little lock on quests whose dependencies aren't done.").defaults("True");
        section("Notifications");
        choice("Completion Notification Style", List.of("Toast", "Chat", "None"), () -> ClientPrefs.completionNotification, v -> ClientPrefs.completionNotification = v)
                .tip("How you hear about a finished quest or chapter.").defaults("Toast");
        bool("Play Completion Sounds", () -> ClientPrefs.completionSounds, v -> ClientPrefs.completionSounds = v).defaults("True");
        choice("Reward Notification Style", List.of("Toast", "Chat", "None"), () -> ClientPrefs.rewardNotification, v -> ClientPrefs.rewardNotification = v)
                .tip("How you hear about a collected reward.").defaults("Toast");
        section("Pinned Quests");
        choice("Auto-pin Follows", List.of("Chapter", "Quest", "None"), () -> ClientPrefs.autoPinFollows, v -> ClientPrefs.autoPinFollows = v)
                .tip("Not used yet.").defaults("Chapter");
        bool("Exclude Flexible-Mode Quests", () -> ClientPrefs.excludeFlexible, v -> ClientPrefs.excludeFlexible = v).defaults("True");
        choice("Pinned Panel Position", List.of("Right", "Left"), () -> ClientPrefs.pinnedPosition, v -> ClientPrefs.pinnedPosition = v).defaults("Right");
        number("Pinned Panel Scale", false, () -> String.valueOf(ClientPrefs.pinnedScaling), v -> ClientPrefs.pinnedScaling = Math.max(0.25, Math.min(3.0, Double.parseDouble(v)))).defaults("0.75");
        number("Pinned Panel X Offset", true, () -> String.valueOf(ClientPrefs.pinnedXInset), v -> ClientPrefs.pinnedXInset = Integer.parseInt(v)).defaults("2");
        number("Pinned Panel Y Offset", true, () -> String.valueOf(ClientPrefs.pinnedYInset), v -> ClientPrefs.pinnedYInset = Integer.parseInt(v)).defaults("2");
        choice("Pinned Tracker Visibility", List.of("Quests and Tasks", "Quests only", "Hidden"), () -> ClientPrefs.trackerVisibility, v -> ClientPrefs.trackerVisibility = v).defaults("Quests and Tasks");
        section("Translations");
        text("Fallback Locale", () -> ClientPrefs.fallbackLocale, v -> ClientPrefs.fallbackLocale = v).tip("Not used yet.");
        bool("Highlight missing translations", () -> ClientPrefs.highlightMissing, v -> ClientPrefs.highlightMissing = v).tip("Not used yet.").defaults("True");
        text("Locale Override", () -> ClientPrefs.localeOverride, v -> ClientPrefs.localeOverride = v).tip("Not used yet.");
        section("Change History Display");
        bool("Always Show Recent Changes", () -> ClientPrefs.alwaysShowHistory, v -> ClientPrefs.alwaysShowHistory = v).tip("Recent changes stay on the map and don't fade.").defaults("False");
        number("Recent Changes Show Time (seconds)", false, () -> String.valueOf(ClientPrefs.historySeconds), v -> ClientPrefs.historySeconds = Double.parseDouble(v)).defaults("5.00");
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
