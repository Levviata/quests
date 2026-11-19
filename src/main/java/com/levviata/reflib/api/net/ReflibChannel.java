/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.net;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.levviata.reflib.impl.net.Envelope;
import com.levviata.reflib.impl.net.MessageRegistry;

/**
 * A typed message channel for one referencing project.
 *
 * <p>Register message types (in the same order on client and server; the registration index is the wire
 * id), then send them. Handlers always run on the main game thread. Messages that arrive on the wrong side,
 * with an unknown id, oversize or malformed are dropped and logged; they never throw into the network thread.
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

    /** Context passed to handlers. {@code sender} is null when the message arrives on the client. */
    public record Context(Side side, @Nullable EntityPlayerMP sender) {}

    @FunctionalInterface
    public interface Handler<M extends ReflibMessage> {
        void handle(M message, Context context);
    }

    private final String name;
    private final MessageRegistry registry;
    private final SimpleNetworkWrapper wrapper;

    private ReflibChannel(String name) {
        this.name = name;
        this.registry = new MessageRegistry(name);
        this.wrapper = NetworkRegistry.INSTANCE.newSimpleChannel(name);
        EnvelopeHandler handler = new EnvelopeHandler();
        wrapper.registerMessage(handler, Envelope.class, 0, Side.CLIENT);
        wrapper.registerMessage(handler, Envelope.class, 1, Side.SERVER);
    }

    /**
     * Create the channel for a project. The name is the project's mod id (at most 20 characters, a Forge limit).
     *
     * @throws IllegalStateException if a channel with this name already exists
     */
    public static ReflibChannel create(String modId) {
        if (modId == null || modId.isEmpty() || modId.length() > 20) {
            throw new IllegalArgumentException("Channel name must be 1-20 characters, got '" + modId + "'");
        }
        synchronized (USED_NAMES) {
            if (!USED_NAMES.add(modId)) {
                throw new IllegalStateException("A reflib channel named '" + modId + "' already exists");
            }
        }
        return new ReflibChannel(modId);
    }

    /**
     * Register a message type.
     *
     * @param receiver the side that handles it ({@link Side#SERVER} for client-to-server messages)
     */
    public <M extends ReflibMessage> ReflibChannel register(Class<M> type, Side receiver, Handler<M> handler) {
        registry.register(type, receiver, handler);
        return this;
    }

    public void sendToServer(ReflibMessage message) {
        wrapper.sendToServer(frame(message, Side.SERVER));
    }

    public void sendTo(ReflibMessage message, EntityPlayerMP player) {
        wrapper.sendTo(frame(message, Side.CLIENT), player);
    }

    public void sendToAll(ReflibMessage message) {
        wrapper.sendToAll(frame(message, Side.CLIENT));
    }

    private Envelope frame(ReflibMessage message, Side expectedReceiver) {
        int id = registry.idOf(message.getClass());
        if (registry.receiverOf(id) != expectedReceiver) {
            throw new IllegalArgumentException(message.getClass().getName() + " is registered for "
                    + registry.receiverOf(id) + " but was sent to " + expectedReceiver);
        }
        return new Envelope(id, registry.encode(message));
    }

    private final class EnvelopeHandler implements IMessageHandler<Envelope, IMessage> {
        @Override
        public IMessage onMessage(Envelope frame, MessageContext ctx) {
            Side side = ctx.side;
            EntityPlayerMP sender = side == Side.SERVER ? ctx.getServerHandler().player : null;
            ReflibMessage message;
            try {
                message = registry.decode(frame.id, frame.payload, side);
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
