/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.assets.SuppliedAssets;
import com.levviata.reflibquests.model.Definitions.Quest;

@SideOnly(Side.CLIENT)
final class Theme {

    private static final String ALL = "*";

    private static Map<String, Map<String, String>> sections;

    private Theme() {}

    private static synchronized Map<String, Map<String, String>> sections() {
        if (sections == null) {
            Map<String, Map<String, String>> out = new HashMap<>();
            try {
                for (net.minecraft.client.resources.IResource res : Minecraft.getMinecraft().getResourceManager()
                        .getAllResources(new net.minecraft.util.ResourceLocation(QuestArt.DOMAIN, "ftb_quests_theme.txt"))) {
                    try (java.io.InputStream in = res.getInputStream()) {
                        parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), out);
                    }
                }
            } catch (java.io.IOException | RuntimeException ignored) {

            }
            if (out.isEmpty()) {
                byte[] data = SuppliedAssets.read(QuestArt.DOMAIN, "ftb_quests_theme.txt");
                if (data != null) {
                    parse(new String(data, StandardCharsets.UTF_8), out);
                }
            }
            sections = out;
        }
        return sections;
    }

    static synchronized void invalidate() {
        sections = null;
    }

    private static void parse(String text, Map<String, Map<String, String>> out) {
        String current = ALL;
        for (String raw : text.split("\r?\n")) {
            String rest = raw.strip();
            while (!rest.isEmpty()) {
                if (rest.startsWith("[")) {
                    int close = rest.indexOf(']');
                    if (close < 0) {
                        break;
                    }
                    String header = rest.substring(1, close).strip();
                    current = header.equals(ALL) ? ALL : header.toLowerCase(Locale.ROOT);
                    rest = rest.substring(close + 1).strip();
                    continue;
                }
                int next = rest.indexOf('[');
                String property = (next < 0 ? rest : rest.substring(0, next)).strip();
                rest = next < 0 ? "" : rest.substring(next);
                int colon = property.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String value = property.substring(colon + 1);
                int semi = value.indexOf(';');
                out.computeIfAbsent(current, k -> new HashMap<>()).put(property.substring(0, colon).strip(), (semi < 0 ? value : value.substring(0, semi)).strip());
            }
        }
    }

    private static String lookup(String key, Quest q) {
        Map<String, Map<String, String>> all = sections();
        if (q != null) {
            Map<String, String> byId = all.get(q.id.toLowerCase(Locale.ROOT));
            if (byId != null && byId.containsKey(key)) {
                return byId.get(key);
            }
            for (String tag : q.tags) {
                Map<String, String> byTag = all.get(tag.strip().toLowerCase(Locale.ROOT));
                if (byTag != null && byTag.containsKey(key)) {
                    return byTag.get(key);
                }
            }
        }
        Map<String, String> general = all.get(ALL);
        return general == null ? null : general.get(key);
    }

    static boolean has(String key) {
        return lookup(key, null) != null;
    }

    static int color(String key, int fallback) {
        return color(key, fallback, null);
    }

    static int color(String key, int fallback, Quest q) {
        String v = lookup(key, q);
        if (v == null || !v.startsWith("#")) {
            return fallback;
        }
        try {
            String hex = v.substring(1);
            if (hex.length() == 6) {
                return 0xFF000000 | Integer.parseInt(hex, 16);
            }
            if (hex.length() == 8) {
                return (int) Long.parseLong(hex, 16);
            }
        } catch (NumberFormatException ignored) {

        }
        return fallback;
    }

    static double number(String key, double fallback) {
        String v = lookup(key, null);
        if (v == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
