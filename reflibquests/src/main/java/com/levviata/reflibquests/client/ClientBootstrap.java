/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;

/** Client-only wiring: the key to open the book, the {@code /rqbook} command, and cleanup on disconnect. */
@SideOnly(Side.CLIENT)
public final class ClientBootstrap {

    private static final KeyBinding OPEN = new KeyBinding("Open quest book", Keyboard.KEY_J, "Reflib Quests");
    private static volatile boolean openNextTick;

    private ClientBootstrap() {}

    public static void init() {
        ClientRegistry.registerKeyBinding(OPEN);
        MinecraftForge.EVENT_BUS.register(new ClientBootstrap());
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override
            public String getName() {
                return "rqbook";
            }

            @Override
            public String getUsage(ICommandSender sender) {
                return "/rqbook";
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
                // opened a tick later: the chat screen closes itself right after sending a command
                openNextTick = true;
            }
        });
    }

    private static void open() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!ClientQuests.ready()) {
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString("[Quests] The quest book has not arrived from the server (is Reflib Quests installed there?)."));
            }
            return;
        }
        mc.displayGuiScreen(new QuestScreen());
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (OPEN.isPressed() && Minecraft.getMinecraft().currentScreen == null) {
            open();
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && openNextTick) {
            openNextTick = false;
            open();
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ClientQuests.clear();
    }
}
