/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.impl.net;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;

import com.levviata.reflib.api.net.ReflibChannel;
import com.levviata.reflib.api.net.ReflibMessage;

/**
 * Internal: the transport-independent half of a channel. Maps message classes to one-byte ids, encodes and
 * decodes payloads and enforces direction and size rules. It has no dependency on the network stack so it
 * can be unit-tested.
 */
public final class MessageRegistry {

    /** Conservative cap on a single payload (vanilla limits serverbound custom payloads to 32 KiB). */
    public static final int MAX_PAYLOAD_BYTES = 30_000;

    private record Entry(Class<? extends ReflibMessage> type, Side receiver,
                         ReflibChannel.Handler<ReflibMessage> handler, Constructor<? extends ReflibMessage> ctor) {}

    private final String name;
    private final List<Entry> entries = new ArrayList<>();

    public MessageRegistry(String name) {
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    public synchronized <M extends ReflibMessage> void register(Class<M> type, Side receiver,
                                                                ReflibChannel.Handler<M> handler) {
        if (entries.size() >= 255) {
            throw new IllegalStateException("Channel '" + name + "' supports at most 255 message types");
        }
        for (Entry e : entries) {
            if (e.type == type) {
                throw new IllegalStateException(type.getName() + " is already registered on channel '" + name + "'");
            }
        }
        Constructor<M> ctor;
        try {
            ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException(type.getName() + " needs a no-argument constructor", ex);
        }
        entries.add(new Entry(type, receiver, (ReflibChannel.Handler<ReflibMessage>) handler, ctor));
    }

    public synchronized int idOf(Class<? extends ReflibMessage> type) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).type == type) {
                return i;
            }
        }
        throw new IllegalArgumentException(type.getName() + " is not registered on channel '" + name + "'");
    }

    public synchronized Side receiverOf(int id) {
        return entries.get(id).receiver;
    }

    /** @return the encoded payload of a message */
    public byte[] encode(ReflibMessage message) {
        ByteBuf raw = Unpooled.buffer();
        try {
            message.write(new PacketBuffer(raw));
            if (raw.readableBytes() > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException(message.getClass().getName() + " encodes to " + raw.readableBytes()
                        + " bytes, above the limit of " + MAX_PAYLOAD_BYTES + "; split it into several messages");
            }
            byte[] out = new byte[raw.readableBytes()];
            raw.readBytes(out);
            return out;
        } finally {
            raw.release();
        }
    }

    /**
     * Decode a payload.
     *
     * @throws IllegalArgumentException on unknown id, wrong direction, oversize payload, or malformed data
     */
    public ReflibMessage decode(int id, byte[] payload, Side arrivedOn) {
        Entry entry;
        synchronized (this) {
            if (id < 0 || id >= entries.size()) {
                throw new IllegalArgumentException("Unknown message id " + id + " on channel '" + name + "'");
            }
            entry = entries.get(id);
        }
        if (entry.receiver != arrivedOn) {
            throw new IllegalArgumentException(entry.type.getName() + " is addressed to " + entry.receiver
                    + " but arrived on " + arrivedOn);
        }
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Payload of " + payload.length + " bytes exceeds " + MAX_PAYLOAD_BYTES);
        }
        ByteBuf raw = Unpooled.wrappedBuffer(payload);
        try {
            ReflibMessage message = entry.ctor.newInstance();
            message.read(new PacketBuffer(raw));
            if (raw.isReadable()) {
                throw new IllegalArgumentException(entry.type.getName() + " left " + raw.readableBytes()
                        + " unread bytes; read() does not match write()");
            }
            return message;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (ex instanceof IllegalArgumentException iae) {
                throw iae;
            }
            throw new IllegalArgumentException("Malformed " + entry.type.getName() + ": " + ex, ex);
        } finally {
            raw.release();
        }
    }

    public synchronized ReflibChannel.Handler<ReflibMessage> handlerOf(int id) {
        return entries.get(id).handler;
    }
}
