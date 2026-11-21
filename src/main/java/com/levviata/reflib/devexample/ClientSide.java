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
        checkSuppliedAssets();
        LOGGER.info("REFLIB-NET: client sending ping #42");
        channel.sendToServer(new PingMessage(42));
    }

    /** Verifies that the supplied test jar is visible through the normal resource and language systems. */
    private static void checkSuppliedAssets() {
        Minecraft mc = Minecraft.getMinecraft();
        String hello = net.minecraft.client.resources.I18n.format("reflibtest.hello");
        String multi = net.minecraft.client.resources.I18n.format("reflibtest.multi");
        boolean textureOk;
        try {
            textureOk = mc.getResourceManager()
                    .getResource(new net.minecraft.util.ResourceLocation("reflibtestassets", "textures/gui/test.png"))
                    .getInputStream().read() == 0x89;
        } catch (java.io.IOException ex) {
            textureOk = false;
        }
        boolean leak;
        try {
            mc.getResourceManager().getResource(new net.minecraft.util.ResourceLocation("minecraft", "textures/gui/should_not_leak.png"));
            leak = true;
        } catch (java.io.IOException ex) {
            leak = false;
        }
        boolean ok = "Hello from a supplied jar".equals(hello) && "one two".equals(multi) && textureOk && !leak;
        LOGGER.info("REFLIB-ASSETS: {} (lang='{}', multi='{}', texture={}, minecraftLeak={})",
                ok ? "PASS" : "FAIL", hello, multi, textureOk, leak);
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
