/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.impl.net;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;

import com.levviata.reflib.api.net.ReflibMessage;

class MessageRegistryTest {

    public static class Num implements ReflibMessage {
        int v;
        String s = "";

        public Num() {}

        Num(int v, String s) {
            this.v = v;
            this.s = s;
        }

        @Override
        public void write(PacketBuffer buf) {
            buf.writeVarInt(v);
            buf.writeString(s);
        }

        @Override
        public void read(PacketBuffer buf) {
            v = buf.readVarInt();
            s = buf.readString(100);
        }
    }

    public static class Lazy implements ReflibMessage {
        @Override
        public void write(PacketBuffer buf) {
            buf.writeInt(1);
        }

        @Override
        public void read(PacketBuffer buf) {} // does not consume what write() produced
    }

    public static class Huge implements ReflibMessage {
        @Override
        public void write(PacketBuffer buf) {
            buf.writeBytes(new byte[MessageRegistry.MAX_MESSAGE_BYTES + 1]);
        }

        @Override
        public void read(PacketBuffer buf) {}
    }

    public static class NoDefaultCtor implements ReflibMessage {
        NoDefaultCtor(int x) {}

        @Override
        public void write(PacketBuffer buf) {}

        @Override
        public void read(PacketBuffer buf) {}
    }

    private MessageRegistry registry() {
        MessageRegistry r = new MessageRegistry("test", "1");
        r.register(Num.class, Side.SERVER, (m, c) -> {});
        r.register(Lazy.class, Side.CLIENT, (m, c) -> {});
        r.register(Huge.class, Side.SERVER, (m, c) -> {});
        return r;
    }

    @Test
    void encodeDecodeRoundTrip() {
        MessageRegistry r = registry();
        byte[] payload = r.encode(new Num(300, "héllo"));
        Num back = (Num) r.decode(r.idOf(Num.class), payload, Side.SERVER);
        assertEquals(300, back.v);
        assertEquals("héllo", back.s);
    }

    @Test
    void idsFollowRegistrationOrder() {
        MessageRegistry r = registry();
        assertEquals(0, r.idOf(Num.class));
        assertEquals(1, r.idOf(Lazy.class));
    }

    @Test
    void wrongDirectionIsRejected() {
        MessageRegistry r = registry();
        byte[] payload = r.encode(new Num(1, ""));
        assertThrows(IllegalArgumentException.class, () -> r.decode(0, payload, Side.CLIENT));
    }

    @Test
    void unknownIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> registry().decode(9, new byte[0], Side.SERVER));
    }

    @Test
    void truncatedAndTrailingDataIsRejected() {
        MessageRegistry r = registry();
        assertThrows(IllegalArgumentException.class, () -> r.decode(0, new byte[] {(byte) 0x80}, Side.SERVER));
        assertThrows(IllegalArgumentException.class, () -> r.decode(1, new byte[] {0, 0, 0, 1}, Side.CLIENT),
                "read() that leaves bytes behind must be reported");
    }

    @Test
    void oversizeIsRejectedBothWays() {
        MessageRegistry r = registry();
        assertThrows(IllegalArgumentException.class, () -> r.encode(new Huge()));
        assertThrows(IllegalArgumentException.class,
                () -> r.decode(2, new byte[MessageRegistry.MAX_MESSAGE_BYTES + 1], Side.SERVER));
    }

    @Test
    void splitKeepsContentAndOrder() {
        byte[] data = new byte[MessageRegistry.MAX_FRAGMENT_BYTES * 2 + 17];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 31);
        }
        java.util.List<byte[]> parts = MessageRegistry.split(data);
        assertEquals(3, parts.size());
        assertEquals(17, parts.get(2).length);
        assertEquals(1, MessageRegistry.split(new byte[5]).size());
        assertEquals(1, MessageRegistry.split(new byte[0]).size());
        Reassembler<String> r = new Reassembler<>(MessageRegistry.MAX_FRAGMENTS, MessageRegistry.MAX_MESSAGE_BYTES);
        byte[] whole = null;
        for (int i = 0; i < parts.size(); i++) {
            whole = r.accept("a", 1, i, parts.size(), parts.get(i), 0);
        }
        assertArrayEquals(data, whole);
    }

    @Test
    void signatureDependsOnProtocolAndMessageSetAndFreezes() {
        MessageRegistry a = registry();
        MessageRegistry b = registry();
        assertEquals(a.signature(), b.signature());
        MessageRegistry otherProtocol = new MessageRegistry("test", "2");
        otherProtocol.register(Num.class, Side.SERVER, (m, c) -> {});
        otherProtocol.register(Lazy.class, Side.CLIENT, (m, c) -> {});
        otherProtocol.register(Huge.class, Side.SERVER, (m, c) -> {});
        assertNotEquals(a.signature(), otherProtocol.signature());
        MessageRegistry fewer = new MessageRegistry("test", "1");
        fewer.register(Num.class, Side.SERVER, (m, c) -> {});
        assertNotEquals(a.signature(), fewer.signature());
        assertThrows(IllegalStateException.class, () -> a.register(NoDefaultCtor.class, Side.SERVER, (m, c) -> {}));
    }

    @Test
    void registrationRules() {
        MessageRegistry r = registry();
        assertThrows(IllegalStateException.class, () -> r.register(Num.class, Side.SERVER, (m, c) -> {}));
        assertThrows(IllegalArgumentException.class, () -> r.register(NoDefaultCtor.class, Side.SERVER, (m, c) -> {}));
        assertThrows(IllegalArgumentException.class, () -> r.idOf(NoDefaultCtor.class));
    }
}
