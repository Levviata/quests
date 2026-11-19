/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
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

import com.levviata.reflibquests.model.Definitions.Quest;
import com.levviata.reflibquests.model.Definitions.Task;
import com.levviata.reflibquests.model.QuestBook;
import com.levviata.reflibquests.model.QuestRules;
import com.levviata.reflibquests.model.TeamProgress;

/** Client-only wiring: the key to open the book, the {@code /rqbook} command, and cleanup on disconnect. */
@SideOnly(Side.CLIENT)
public final class ClientBootstrap {

    private static final KeyBinding OPEN = new KeyBinding("Open quest book", Keyboard.KEY_J, "Reflib Quests");
    private static volatile boolean openNextTick;

    /** How long a just-completed pinned quest stays on the tracker, and the colour it is shown in. */
    private static final long CELEBRATE_MS = 2000;
    private static final int DONE_GREEN = 0x55FF55;
    private static final Map<String, Boolean> WAS_DONE = new HashMap<>();
    private static final Map<String, Long> CELEBRATE_UNTIL = new HashMap<>();

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
        WAS_DONE.clear();
        CELEBRATE_UNTIL.clear();
    }

    /** The tracker: pinned, unfinished quests and their open tasks, in the top right corner while playing. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.TEXT || !ClientQuests.ready()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        List<String> pins = ClientQuests.pins();
        if (pins.isEmpty() || mc.currentScreen != null || mc.gameSettings.showDebugInfo) {
            return;
        }
        QuestBook book = ClientQuests.book();
        TeamProgress p = ClientQuests.progress();
        FontRenderer fr = mc.fontRenderer;
        int right = event.getResolution().getScaledWidth() - 6;
        int y = 6;
        long now = System.currentTimeMillis();
        WAS_DONE.keySet().retainAll(pins); // forget quests that are no longer pinned
        CELEBRATE_UNTIL.keySet().retainAll(pins);
        for (String id : pins) {
            Quest q = book.quest(id);
            if (q == null) {
                continue;
            }
            boolean done = p.isCompleted(id);
            // a quest that is seen to complete while pinned is shown for a moment as done, then dropped
            Boolean wasDone = WAS_DONE.put(id, done);
            if (done && wasDone != null && !wasDone) {
                CELEBRATE_UNTIL.put(id, now + CELEBRATE_MS);
            }
            Long until = CELEBRATE_UNTIL.get(id);
            boolean celebrating = done && until != null && now < until;
            if (done && !celebrating || !done && !QuestRules.isVisible(book, p, q)) {
                continue;
            }
            boolean details = QuestRules.detailsVisible(book, p, q);
            String title = details ? QuestText.title(q) : "???";
            int titleColor = celebrating ? DONE_GREEN : 0xFFD060;
            fr.drawStringWithShadow(title, right - fr.getStringWidth(title), y, titleColor);
            if (celebrating) {
                drawCheck(fr, right - fr.getStringWidth(title) - 12, y - 1);
            }
            y += 10;
            if (!details) {
                continue;
            }
            int shown = 0;
            for (Task t : q.tasks) {
                if (!celebrating && QuestRules.taskDone(p, t)) {
                    continue;
                }
                if (shown++ >= 3) {
                    break;
                }
                long have = celebrating ? t.maxProgress() : Math.min(p.taskProgress(t.id()), t.maxProgress());
                String text = QuestText.task(t) + (t.maxProgress() > 1 ? "  " + have + "/" + t.maxProgress() : "");
                fr.drawStringWithShadow(text, right - fr.getStringWidth(text), y, celebrating ? DONE_GREEN : 0xDDDDDD);
                y += 10;
            }
            y += 4;
        }
    }

    /** A check mark in light green: the supplied art if there is any, otherwise a character. */
    private static void drawCheck(FontRenderer fr, int x, int y) {
        if (QuestArt.available() && QuestArt.has("textures/gui/check_gray.png")) {
            QuestArt.quad(QuestArt.guiTexture("check_gray"), x, y, 10, 10, 0xFF55FF55);
        } else {
            fr.drawStringWithShadow(fr.getStringWidth("✔") > 0 ? "✔" : "v", x + 1, y + 1, DONE_GREEN);
        }
    }
}
