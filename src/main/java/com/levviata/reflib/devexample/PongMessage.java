/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.devexample;

import net.minecraft.network.PacketBuffer;

import com.levviata.reflib.api.net.ReflibMessage;

public class PongMessage implements ReflibMessage {

    String text = "";

    public PongMessage() {}

    public PongMessage(String text) {
        this.text = text;
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeString(text);
    }

    @Override
    public void read(PacketBuffer buf) {
        text = buf.readString(256);
    }
}
