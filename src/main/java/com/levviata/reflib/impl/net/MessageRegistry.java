/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.impl.net;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;

import com.levviata.reflib.api.net.ReflibChannel;
import com.levviata.reflib.api.net.ReflibMessage;

/**
 * Internal: the transport-independent half of a channel. Maps message classes to one-byte ids, encodes and
 * decodes payloads, splits large payloads into fragments, and enforces direction and size rules. It has no
 * dependency on the network stack so it can be unit-tested.
 */
public final class MessageRegistry {

    /** Largest message (after encoding) a channel will send or accept. Larger data must be split by the caller. */
    public static final int MAX_MESSAGE_BYTES = 8 * 1024 * 1024;

    /** Payload bytes per network frame (vanilla limits custom payloads to 32 KiB; leave room for headers). */
    public static final int MAX_FRAGMENT_BYTES = 28_000;

    public static final int MAX_FRAGMENTS = (MAX_MESSAGE_BYTES + MAX_FRAGMENT_BYTES - 1) / MAX_FRAGMENT_BYTES;

    private record Entry(Class<? extends ReflibMessage> type, Side receiver,
                         ReflibChannel.Handler<ReflibMessage> handler, Constructor<? extends ReflibMessage> ctor) {}

    private final String name;
    private final String protocolVersion;
    private final List<Entry> entries = new ArrayList<>();
    private boolean frozen;
    private int signature;

    public MessageRegistry(String name, String protocolVersion) {
        this.name = name;
        this.protocolVersion = protocolVersion;
    }

    @SuppressWarnings("unchecked")
    public synchronized <M extends ReflibMessage> void register(Class<M> type, Side receiver,
                                                                ReflibChannel.Handler<M> handler) {
        if (frozen) {
            throw new IllegalStateException("Channel '" + name + "' is already in use; register all messages first");
        }
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

    /**
     * A hash of the protocol version and the ordered registered message types and directions. Both ends must
     * agree; frames carrying a different value are dropped with a clear log line. Freezes the registry.
     */
    public synchronized int signature() {
        if (!frozen) {
            frozen = true;
            StringBuilder sb = new StringBuilder(protocolVersion);
            for (Entry e : entries) {
                sb.append('|').append(e.type.getName()).append('>').append(e.receiver);
            }
            signature = sb.toString().hashCode();
        }
        return signature;
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
            if (raw.readableBytes() > MAX_MESSAGE_BYTES) {
                throw new IllegalArgumentException(message.getClass().getName() + " encodes to " + raw.readableBytes()
                        + " bytes, above the limit of " + MAX_MESSAGE_BYTES + "; split it into several messages");
            }
            byte[] out = new byte[raw.readableBytes()];
            raw.readBytes(out);
            return out;
        } finally {
            raw.release();
        }
    }

    /** Split an encoded payload into network-sized fragments (one fragment if it already fits). */
    public static List<byte[]> split(byte[] payload) {
        List<byte[]> out = new ArrayList<>();
        if (payload.length <= MAX_FRAGMENT_BYTES) {
            out.add(payload);
            return out;
        }
        for (int from = 0; from < payload.length; from += MAX_FRAGMENT_BYTES) {
            out.add(Arrays.copyOfRange(payload, from, Math.min(payload.length, from + MAX_FRAGMENT_BYTES)));
        }
        return out;
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
        if (payload.length > MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("Payload of " + payload.length + " bytes exceeds " + MAX_MESSAGE_BYTES);
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
