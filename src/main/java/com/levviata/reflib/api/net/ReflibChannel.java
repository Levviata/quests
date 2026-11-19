/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.net;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.levviata.reflib.impl.net.Envelope;
import com.levviata.reflib.impl.net.MessageRegistry;
import com.levviata.reflib.impl.net.Reassembler;

/**
 * A typed message channel for one referencing project.
 *
 * <p>Register message types (in the same order on client and server; the registration index is the wire
 * id), then send them. Handlers always run on the main game thread. Messages that arrive on the wrong side,
 * with an unknown id, oversize, malformed, or from a build with a different message set are dropped and
 * logged; they never throw into the network thread.
 *
 * <p>Messages larger than one network frame are split and reassembled transparently, up to
 * {@value MessageRegistry#MAX_MESSAGE_BYTES} bytes per message. Registration is closed once the channel is
 * first used. Each channel also carries a signature of its protocol version and message list; peers whose
 * signatures differ ignore each other's messages (and log why).
 *
 * <pre>{@code
 * ReflibChannel channel = ReflibChannel.create("myproject");
 * channel.register(PingMessage.class, Side.SERVER, (msg, ctx) -> log(ctx.sender(), msg));
 * channel.sendToServer(new PingMessage(5));
 * }</pre>
 */
public final class ReflibChannel {

    private static final Logger LOGGER = LogManager.getLogger("reflib/net");
    private static final Set<String> USED_NAMES = new HashSet<>();
    private static final String CLIENT_SENDER = "server";

    /** Context passed to handlers. {@code sender} is null when the message arrives on the client. */
    public record Context(Side side, @Nullable EntityPlayerMP sender) {}

    @FunctionalInterface
    public interface Handler<M extends ReflibMessage> {
        void handle(M message, Context context);
    }

    private final String name;
    private final MessageRegistry registry;
    private final SimpleNetworkWrapper wrapper;
    private final AtomicInteger nextTransfer = new AtomicInteger();
    private final Reassembler<Object> reassembler = new Reassembler<>(MessageRegistry.MAX_FRAGMENTS, MessageRegistry.MAX_MESSAGE_BYTES);
    private volatile boolean warnedSignature;

    private ReflibChannel(String name, String protocolVersion) {
        this.name = name;
        this.registry = new MessageRegistry(name, protocolVersion);
        this.wrapper = NetworkRegistry.INSTANCE.newSimpleChannel(name);
        EnvelopeHandler handler = new EnvelopeHandler();
        wrapper.registerMessage(handler, Envelope.class, 0, Side.CLIENT);
        wrapper.registerMessage(handler, Envelope.class, 1, Side.SERVER);
        MinecraftForge.EVENT_BUS.register(this);
    }

    /** Create the channel for a project with protocol version {@code "1"}. See {@link #create(String, String)}. */
    public static ReflibChannel create(String modId) {
        return create(modId, "1");
    }

    /**
     * Create the channel for a project. The name is the project's mod id (at most 20 characters, a Forge limit).
     *
     * @param protocolVersion bump this when the meaning of existing messages changes without the class list changing
     * @throws IllegalStateException if a channel with this name already exists
     */
    public static ReflibChannel create(String modId, String protocolVersion) {
        if (modId == null || modId.isEmpty() || modId.length() > 20) {
            throw new IllegalArgumentException("Channel name must be 1-20 characters, got '" + modId + "'");
        }
        synchronized (USED_NAMES) {
            if (!USED_NAMES.add(modId)) {
                throw new IllegalStateException("A reflib channel named '" + modId + "' already exists");
            }
        }
        return new ReflibChannel(modId, protocolVersion);
    }

    /**
     * Register a message type.
     *
     * @param receiver the side that handles it ({@link Side#SERVER} for client-to-server messages)
     * @throws IllegalStateException if the channel was already used
     */
    public <M extends ReflibMessage> ReflibChannel register(Class<M> type, Side receiver, Handler<M> handler) {
        registry.register(type, receiver, handler);
        return this;
    }

    public void sendToServer(ReflibMessage message) {
        for (Envelope frame : frames(message, Side.SERVER)) {
            wrapper.sendToServer(frame);
        }
    }

    public void sendTo(ReflibMessage message, EntityPlayerMP player) {
        for (Envelope frame : frames(message, Side.CLIENT)) {
            wrapper.sendTo(frame, player);
        }
    }

    public void sendToAll(ReflibMessage message) {
        for (Envelope frame : frames(message, Side.CLIENT)) {
            wrapper.sendToAll(frame);
        }
    }

    private List<Envelope> frames(ReflibMessage message, Side expectedReceiver) {
        int id = registry.idOf(message.getClass());
        if (registry.receiverOf(id) != expectedReceiver) {
            throw new IllegalArgumentException(message.getClass().getName() + " is registered for "
                    + registry.receiverOf(id) + " but was sent to " + expectedReceiver);
        }
        List<byte[]> parts = MessageRegistry.split(registry.encode(message));
        int signature = registry.signature();
        int transfer = nextTransfer.getAndIncrement();
        List<Envelope> out = new java.util.ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            out.add(new Envelope(id, signature, transfer, i, parts.size(), parts.get(i)));
        }
        return out;
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.player.getUniqueID();
        reassembler.clear(id);
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        reassembler.clear(CLIENT_SENDER);
    }

    private final class EnvelopeHandler implements IMessageHandler<Envelope, IMessage> {
        @Override
        public IMessage onMessage(Envelope frame, MessageContext ctx) {
            Side side = ctx.side;
            EntityPlayerMP sender = side == Side.SERVER ? ctx.getServerHandler().player : null;
            if (frame.signature != registry.signature()) {
                if (!warnedSignature) {
                    warnedSignature = true;
                    LOGGER.warn("[{}] ignoring messages from a peer with a different message set or protocol version "
                            + "(different mod versions installed on client and server?)", name);
                }
                return null;
            }
            ReflibMessage message;
            try {
                Object key = sender != null ? sender.getUniqueID() : CLIENT_SENDER;
                byte[] whole = reassembler.accept(key, frame.transfer, frame.index, frame.count, frame.payload,
                        System.currentTimeMillis());
                if (whole == null) {
                    return null;
                }
                message = registry.decode(frame.id, whole, side);
            } catch (IllegalArgumentException ex) {
                LOGGER.warn("[{}] dropped message: {}", name, ex.getMessage());
                return null;
            }
            Handler<ReflibMessage> handler = registry.handlerOf(frame.id);
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                try {
                    handler.handle(message, new Context(side, sender));
                } catch (RuntimeException ex) {
                    LOGGER.error("[{}] handler for {} failed", name, message.getClass().getName(), ex);
                }
            });
            return null;
        }
    }
}
