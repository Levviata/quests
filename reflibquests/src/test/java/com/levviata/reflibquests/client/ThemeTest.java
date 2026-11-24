/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ThemeTest {

    private static Map<String, Map<String, String>> parsed(String text) {
        Map<String, Map<String, String>> out = new HashMap<>();
        Theme.parse(text, out);
        return out;
    }

    @Test
    void readsTheDefaultSectionAndTrimsOptions() {
        Map<String, Map<String, String>> t = parsed("[*]\nbackground: ftblibrary:textures/gui/x.png; color=#DCFFFFFF; tile_size=64\n"
                + "dependency_line_thickness:          0.17\n\ntext_color:          #FFFFFF\n");
        assertEquals("ftblibrary:textures/gui/x.png", t.get("*").get("background"));
        assertEquals("0.17", t.get("*").get("dependency_line_thickness"));
        assertEquals("#FFFFFF", t.get("*").get("text_color"));
    }

    @Test
    void readsQuestIdAndTagSections() {
        Map<String, Map<String, String>> t = parsed("[*]\nquest_not_started_color: #96FFFFFF\n"
                + "[BlueQuests]\nquest_not_started_color: #FF0000FF\n[0123ABCD]\nquest_completed_color: #C856FF56\n");
        assertEquals("#96FFFFFF", t.get("*").get("quest_not_started_color"));
        assertEquals("#FF0000FF", t.get("bluequests").get("quest_not_started_color"));
        assertEquals("#C856FF56", t.get("0123abcd").get("quest_completed_color"));
    }

    @Test
    void readsTheDocumentationExampleWrittenOnOneLine() {
        Map<String, Map<String, String>> t = parsed("[bluequests]quest_not_started_color: #FF0000FF[redquests]quest_not_started_color: #FFFF0000");
        assertEquals("#FF0000FF", t.get("bluequests").get("quest_not_started_color"));
        assertEquals("#FFFF0000", t.get("redquests").get("quest_not_started_color"));
    }

    @Test
    void laterFilesOverrideEarlierOnes() {
        Map<String, Map<String, String>> t = new HashMap<>();
        Theme.parse("[*]\nfull_screen_quest: 0\ntext_color: #FFFFFF\n", t);
        Theme.parse("[*]\nfull_screen_quest: 1\n", t);
        assertEquals("1", t.get("*").get("full_screen_quest"));
        assertEquals("#FFFFFF", t.get("*").get("text_color"));
    }

    @Test
    void ignoresLinesWithoutAKey() {
        Map<String, Map<String, String>> t = parsed("[*]\njust some words\n:no key\n");
        assertNull(t.get("*"));
    }

    @Test
    void colourFormats() {
        assertEquals(0xFF00C8C8, Theme.parseColor("#00C8C8", 0));
        assertEquals(0x64CCA3A3, Theme.parseColor("#64CCA3A3", 0));
        assertEquals(7, Theme.parseColor("blue", 7));
        assertEquals(7, Theme.parseColor("#12345", 7));
        assertEquals(7, Theme.parseColor("#GGGGGG", 7));
        assertEquals(7, Theme.parseColor(null, 7));
    }
}
