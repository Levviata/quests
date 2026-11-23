/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.impl.assets;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class LangJson {

    private LangJson() {}

    public static byte[] toLang(byte[] json) {
        JsonElement root = new JsonParser().parse(new String(json, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        if (root.isJsonObject()) {
            JsonObject o = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                JsonElement v = e.getValue();
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) {
                    continue;
                }
                String text = v.getAsString().replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
                sb.append(e.getKey()).append('=').append(text).append('\n');
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
