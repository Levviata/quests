/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.devexample;

import com.levviata.reflib.api.net.ReflibChannel;

final class ClientHook {

    private ClientHook() {}

    static void init(ReflibChannel channel) {
        ClientSide.init(channel);
    }

    static void pong(String text) {
        ClientSide.onPong(text);
    }
}
