/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.impl.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class Envelope implements IMessage {

    public int id;
    public int signature;
    public int transfer;
    public int index;
    public int count = 1;
    public byte[] payload = new byte[0];

    public Envelope() {}

    public Envelope(int id, int signature, int transfer, int index, int count, byte[] payload) {
        this.id = id;
        this.signature = signature;
        this.transfer = transfer;
        this.index = index;
        this.count = count;
        this.payload = payload;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer pb = new PacketBuffer(buf);
        id = pb.readUnsignedByte();
        signature = pb.readInt();
        transfer = pb.readVarInt();
        index = pb.readVarInt();
        count = pb.readVarInt();
        payload = new byte[pb.readableBytes()];
        pb.readBytes(payload);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer pb = new PacketBuffer(buf);
        pb.writeByte(id);
        pb.writeInt(signature);
        pb.writeVarInt(transfer);
        pb.writeVarInt(index);
        pb.writeVarInt(count);
        pb.writeBytes(payload);
    }
}
