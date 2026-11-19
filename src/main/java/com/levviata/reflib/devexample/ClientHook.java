/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.devexample;

import com.levviata.reflib.api.net.ReflibChannel;

/**
 * Side-safe bridge: common code calls these methods, which are only ever reached on the physical client, so
 * {@link ClientSide} (which imports client classes) is never loaded on a dedicated server.
 */
final class ClientHook {

    private ClientHook() {}

    static void init(ReflibChannel channel) {
        ClientSide.init(channel);
    }

    static void pong(String text) {
        ClientSide.onPong(text);
    }
}
