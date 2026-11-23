/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.devexample;

import net.minecraft.network.PacketBuffer;

import com.levviata.reflib.api.net.ReflibMessage;

public class PingMessage implements ReflibMessage {

    int counter;

    public PingMessage() {}

    public PingMessage(int counter) {
        this.counter = counter;
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeVarInt(counter);
    }

    @Override
    public void read(PacketBuffer buf) {
        counter = buf.readVarInt();
    }
}
