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

    private static final java.util.Map<Integer, double[]> BOOK_BUTTON = new java.util.TreeMap<>();

    static double[] bookButtonAt(int guiScale) {
        double[] at = BOOK_BUTTON.get(guiScale);
        if (at != null) {
            return new double[] {at[0], at[1]};
        }
        int nearest = -1;
        for (int s : BOOK_BUTTON.keySet()) {
            if (nearest < 0 || Math.abs(s - guiScale) < Math.abs(nearest - guiScale)) {
                nearest = s;
            }
        }
        if (nearest < 0) {
            return new double[] {0, 0};
        }
        double[] from = BOOK_BUTTON.get(nearest);
        return new double[] {from[0], from[1]};
    }

    static void setBookButton(int guiScale, double fx, double fy) {
        BOOK_BUTTON.put(guiScale, new double[] {Math.max(0, Math.min(1, fx)), Math.max(0, Math.min(1, fy))});
    }

    static void resetBookButton(int guiScale) {
        BOOK_BUTTON.put(guiScale, new double[] {0, 0});
    }

    private static String bookButtonText() {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<Integer, double[]> e : BOOK_BUTTON.entrySet()) {
            sb.append(sb.length() == 0 ? "" : ",").append(e.getKey()).append(':').append(e.getValue()[0]).append(':').append(e.getValue()[1]);
        }
        return sb.toString();
    }

    private static void restoreBookButton(String text) {
        BOOK_BUTTON.clear();
        for (String part : text.split(",")) {
            String[] f = part.split(":");
            if (f.length == 3) {
                try {
                    BOOK_BUTTON.put(Integer.parseInt(f[0].trim()), new double[] {
                        Math.max(0, Math.min(1, Double.parseDouble(f[1].trim()))), Math.max(0, Math.min(1, Double.parseDouble(f[2].trim())))});
                } catch (NumberFormatException ignored) {

                }
            }
        }
    }

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
                .with("changelogScale", changelogScale).with("maxLines", maxLines)
                .with("bookButtonFraction", bookButtonText());
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
        restoreBookButton(c.getString("bookButtonFraction", ""));
    }

    static void save() {
        try {
            DataFiles.write(file(), snapshot());
        } catch (IOException ex) {
            ReflibQuestsMod.LOGGER.warn("Cannot save the player preferences: {}", ex.getMessage());
        }
    }
}
