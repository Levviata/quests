/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.impl.net;

import io.netty.buffer.ByteBuf;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** Internal wire frame: one id byte followed by the raw payload. Public only for the network layer. */
public final class Envelope implements IMessage {

    public int id;
    public byte[] payload = new byte[0];

    public Envelope() {}

    public Envelope(int id, byte[] payload) {
        this.id = id;
        this.payload = payload;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        id = buf.readUnsignedByte();
        payload = new byte[buf.readableBytes()];
        buf.readBytes(payload);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(id);
        buf.writeBytes(payload);
    }
}
