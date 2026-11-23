/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.data.DataFiles;
import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.ReflibQuestsMod;

@SideOnly(Side.CLIENT)
final class ClientPrefs {

    static boolean backspaceHistory = true;
    static boolean chapterPanelPinned = false;
    static boolean oldScrollWheel = false;
    static boolean showLockIcon = true;

    static String completionNotification = "Toast";
    static boolean completionSounds = true;
    static String rewardNotification = "Toast";

    static String autoPinFollows = "Chapter";
    static boolean excludeFlexible = true;
    static boolean autoPinEnabled = false;
    static String pinnedPosition = "Right";
    static double pinnedScaling = 0.75;
    static int pinnedXInset = 2;
    static int pinnedYInset = 2;
    static String trackerVisibility = "Quests and Tasks";

    static String fallbackLocale = "";
    static boolean highlightMissing = true;
    static String localeOverride = "";

    static boolean alwaysShowHistory = false;
    static double historySeconds = 5.0;
    static double changelogScale = 0.75;
    static int maxLines = 10;

    private static boolean loaded;

    private ClientPrefs() {}

    private static Path file() {
        return new File(Minecraft.getMinecraft().gameDir, "config/reflibquests/client_prefs.snbt").toPath();
    }

    static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (file().toFile().isFile()) {
                restore(DataFiles.readLenient(file()));
            }
        } catch (IOException | RuntimeException ex) {
            ReflibQuestsMod.LOGGER.warn("Cannot read the player preferences: {}", ex.getMessage());
        }
    }

    static DataNode.Compound snapshot() {
        return DataNode.Compound.empty()
                .with("backspaceHistory", backspaceHistory).with("chapterPanelPinned", chapterPanelPinned)
                .with("oldScrollWheel", oldScrollWheel).with("showLockIcon", showLockIcon)
                .with("completionNotification", completionNotification).with("completionSounds", completionSounds)
                .with("rewardNotification", rewardNotification).with("autoPinFollows", autoPinFollows)
                .with("excludeFlexible", excludeFlexible).with("autoPinEnabled", autoPinEnabled).with("pinnedPosition", pinnedPosition)
                .with("pinnedScaling", pinnedScaling).with("pinnedXInset", pinnedXInset).with("pinnedYInset", pinnedYInset)
                .with("trackerVisibility", trackerVisibility).with("fallbackLocale", fallbackLocale)
                .with("highlightMissing", highlightMissing).with("localeOverride", localeOverride)
                .with("alwaysShowHistory", alwaysShowHistory).with("historySeconds", historySeconds)
                .with("changelogScale", changelogScale).with("maxLines", maxLines);
    }

    static void restore(DataNode.Compound c) {
        backspaceHistory = c.getBool("backspaceHistory", true);
        chapterPanelPinned = c.getBool("chapterPanelPinned", false);
        oldScrollWheel = c.getBool("oldScrollWheel", false);
        showLockIcon = c.getBool("showLockIcon", true);
        completionNotification = c.getString("completionNotification", "Toast");
        completionSounds = c.getBool("completionSounds", true);
        rewardNotification = c.getString("rewardNotification", "Toast");
        autoPinFollows = c.getString("autoPinFollows", "Chapter");
        excludeFlexible = c.getBool("excludeFlexible", true);
        autoPinEnabled = c.getBool("autoPinEnabled", false);
        pinnedPosition = c.getString("pinnedPosition", "Right");
        pinnedScaling = Math.max(0.25, Math.min(3.0, c.getDouble("pinnedScaling", 0.75)));
        pinnedXInset = c.getInt("pinnedXInset", 2);
        pinnedYInset = c.getInt("pinnedYInset", 2);
        trackerVisibility = c.getString("trackerVisibility", "Quests and Tasks");
        fallbackLocale = c.getString("fallbackLocale", "");
        highlightMissing = c.getBool("highlightMissing", true);
        localeOverride = c.getString("localeOverride", "");
        alwaysShowHistory = c.getBool("alwaysShowHistory", false);
        historySeconds = c.getDouble("historySeconds", 5.0);
        changelogScale = c.getDouble("changelogScale", 0.75);
        maxLines = c.getInt("maxLines", 10);
    }

    static void save() {
        try {
            DataFiles.write(file(), snapshot());
        } catch (IOException ex) {
            ReflibQuestsMod.LOGGER.warn("Cannot save the player preferences: {}", ex.getMessage());
        }
    }
}
