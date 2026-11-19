/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.net;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;

import com.levviata.reflib.api.net.ReflibChannel;
import com.levviata.reflib.api.net.ReflibMessage;
import com.levviata.reflibquests.client.ClientQuests;
import com.levviata.reflibquests.game.QuestServer;

/**
 * The mod's network channel. Server to client: the whole quest book once on join and after reloads, then a
 * snapshot of the player's team progress whenever it changes. Client to server: the three player actions
 * (claim, check, hand in); the server validates everything and answers through normal progress sync and chat.
 *
 * <p>Registration order is the wire protocol; client and server must run the same build.
 */
public final class QuestNet {

    private static ReflibChannel channel;

    private QuestNet() {}

    public static synchronized void init() {
        if (channel != null) {
            return;
        }
        channel = ReflibChannel.create("reflibquests", "1")
                .register(SyncBook.class, Side.CLIENT, (m, c) -> ClientQuests.onBook(m.data))
                .register(SyncProgress.class, Side.CLIENT, (m, c) -> ClientQuests.onProgress(m.data))
                .register(ClaimReward.class, Side.SERVER, (m, c) -> {
                    QuestServer qs = QuestServer.get();
                    if (qs != null && c.sender() != null) {
                        qs.handleClaim(c.sender(), m.rewardId, m.choice);
                    }
                })
                .register(CheckTask.class, Side.SERVER, (m, c) -> {
                    QuestServer qs = QuestServer.get();
                    if (qs != null && c.sender() != null) {
                        qs.handleCheck(c.sender(), m.taskId);
                    }
                })
                .register(SubmitTask.class, Side.SERVER, (m, c) -> {
                    QuestServer qs = QuestServer.get();
                    if (qs != null && c.sender() != null) {
                        qs.handleSubmit(c.sender(), m.taskId);
                    }
                });
    }

    public static void sendBook(EntityPlayerMP player, byte[] packed) {
        channel.sendTo(new SyncBook(packed), player);
    }

    public static void sendProgress(EntityPlayerMP player, byte[] packed) {
        channel.sendTo(new SyncProgress(packed), player);
    }

    public static void claim(String rewardId, int choice) {
        channel.sendToServer(new ClaimReward(rewardId, choice));
    }

    public static void check(String taskId) {
        channel.sendToServer(new CheckTask(taskId));
    }

    public static void submit(String taskId) {
        channel.sendToServer(new SubmitTask(taskId));
    }

    private static void writeBlob(PacketBuffer buf, byte[] data) {
        buf.writeVarInt(data.length);
        buf.writeBytes(data);
    }

    private static byte[] readBlob(PacketBuffer buf) {
        int len = buf.readVarInt();
        if (len < 0 || len > buf.readableBytes()) {
            throw new IllegalArgumentException("bad blob length " + len);
        }
        byte[] data = new byte[len];
        buf.readBytes(data);
        return data;
    }

    // ------------------------------------------------------------------ messages

    /** Server to client: the packed quest book. */
    public static final class SyncBook implements ReflibMessage {
        byte[] data = new byte[0];

        public SyncBook() {}

        SyncBook(byte[] data) {
            this.data = data;
        }

        @Override
        public void write(PacketBuffer buf) {
            writeBlob(buf, data);
        }

        @Override
        public void read(PacketBuffer buf) {
            data = readBlob(buf);
        }
    }

    /** Server to client: a snapshot of the receiving player's team progress. */
    public static final class SyncProgress implements ReflibMessage {
        byte[] data = new byte[0];

        public SyncProgress() {}

        SyncProgress(byte[] data) {
            this.data = data;
        }

        @Override
        public void write(PacketBuffer buf) {
            writeBlob(buf, data);
        }

        @Override
        public void read(PacketBuffer buf) {
            data = readBlob(buf);
        }
    }

    /** Client to server: claim a reward; {@code choice} is a zero-based index for choice rewards, else -1. */
    public static final class ClaimReward implements ReflibMessage {
        String rewardId = "";
        int choice = -1;

        public ClaimReward() {}

        ClaimReward(String rewardId, int choice) {
            this.rewardId = rewardId;
            this.choice = choice;
        }

        @Override
        public void write(PacketBuffer buf) {
            buf.writeString(rewardId);
            buf.writeVarInt(choice + 1);
        }

        @Override
        public void read(PacketBuffer buf) {
            rewardId = buf.readString(256);
            choice = buf.readVarInt() - 1;
        }
    }

    /** Client to server: confirm a checkmark task. */
    public static final class CheckTask implements ReflibMessage {
        String taskId = "";

        public CheckTask() {}

        CheckTask(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public void write(PacketBuffer buf) {
            buf.writeString(taskId);
        }

        @Override
        public void read(PacketBuffer buf) {
            taskId = buf.readString(256);
        }
    }

    /** Client to server: hand in items for a consuming item task. */
    public static final class SubmitTask implements ReflibMessage {
        String taskId = "";

        public SubmitTask() {}

        SubmitTask(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public void write(PacketBuffer buf) {
            buf.writeString(taskId);
        }

        @Override
        public void read(PacketBuffer buf) {
            taskId = buf.readString(256);
        }
    }
}
