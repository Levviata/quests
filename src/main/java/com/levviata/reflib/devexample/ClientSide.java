/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.devexample;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.levviata.reflib.api.net.ReflibChannel;

/** Client-only half of the example: network round trip test, and a command that opens the GUI demo. */
@SideOnly(Side.CLIENT)
final class ClientSide {

    private static final Logger LOGGER = LogManager.getLogger("reflibexample/client");
    private static ReflibChannel channel;

    private ClientSide() {}

    static void init(ReflibChannel ch) {
        channel = ch;
        MinecraftForge.EVENT_BUS.register(new ClientSide());
        ICommand command = new CommandBase() {
            @Override
            public String getName() {
                return "reflibgui";
            }

            @Override
            public String getUsage(ICommandSender sender) {
                return "/reflibgui";
            }

            @Override
            public int getRequiredPermissionLevel() {
                return 0;
            }

            @Override
            public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
                return true;
            }

            @Override
            public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
                // The chat screen closes itself right after sending a command, which would close a screen
                // opened here (addScheduledTask runs immediately on the client thread). Open it next tick.
                openNextTick = true;
            }
        };
        ClientCommandHandler.instance.registerCommand(command);
    }

    private static volatile boolean openNextTick;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && openNextTick) {
            openNextTick = false;
            Minecraft.getMinecraft().displayGuiScreen(new ExampleScreen());
        }
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        if (!Boolean.getBoolean("reflib.clientsmoke") || !event.getWorld().isRemote
                || !(event.getEntity() instanceof EntityPlayerSP)) {
            return;
        }
        LOGGER.info("REFLIB-NET: client sending ping #42");
        channel.sendToServer(new PingMessage(42));
    }

    /** Called when the server's reply arrives on the client thread. */
    static void onPong(String text) {
        boolean ok = "pong 42".equals(text);
        LOGGER.info(ok ? "REFLIB-NET: PASS (round trip, got '{}')" : "REFLIB-NET: FAIL (got '{}')", text);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString("[reflib] network round trip "
                    + (ok ? "PASS" : "FAIL") + " - type /reflibgui to open the GUI demo"));
        }
        if (Boolean.getBoolean("reflib.clientsmoke") && ok) {
            mc.displayGuiScreen(new ExampleScreen());
            LOGGER.info("REFLIB-GUI: demo screen opened");
        }
    }
}
