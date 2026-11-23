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

@SideOnly(Side.CLIENT)
public final class ClientBootstrap {

    private static final KeyBinding OPEN = new KeyBinding("Open quest book", Keyboard.KEY_J, "Reflib Quests");

    static String openKeyName() {
        return Keyboard.getKeyName(OPEN.getKeyCode());
    }
    private static volatile boolean openNextTick;

    private static final long CELEBRATE_MS = 2000;
    private static final int DONE_GREEN = 0x55FF55;
    private static final Map<String, Boolean> WAS_DONE = new HashMap<>();
    private static final Map<String, Long> CELEBRATE_UNTIL = new HashMap<>();

    private ClientBootstrap() {}

    public static void init() {
        ClientRegistry.registerKeyBinding(OPEN);
        ((net.minecraft.client.resources.IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager())
                .registerReloadListener(manager -> Theme.invalidate());
        com.levviata.reflibquests.LootCrates.clientBook = () -> ClientQuests.ready() ? ClientQuests.book() : null;
        MinecraftForge.EVENT_BUS.register(new ClientBootstrap());
        MinecraftForge.EVENT_BUS.register(new InventoryBookButton());
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

                openNextTick = true;
            }
        });
    }

    public static void showToast(String title, String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        ClientPrefs.ensureLoaded();
        notifyPlayer(mc, ClientPrefs.rewardNotification, new QuestToast(title, text, null), text);
    }

    public static void requestOpen() {
        openNextTick = true;
    }

    @SubscribeEvent
    public void onModels(net.minecraftforge.client.event.ModelRegistryEvent event) {
        net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(com.levviata.reflibquests.ReflibQuestsMod.LOOT_CRATE, 0,
                new net.minecraft.client.renderer.block.model.ModelResourceLocation(com.levviata.reflibquests.ReflibQuestsMod.LOOT_CRATE.getRegistryName(), "inventory"));
        net.minecraft.client.renderer.color.ItemColors colors = Minecraft.getMinecraft().getItemColors();
        if (colors != null) {
            colors.registerItemColorHandler((stack, layer) -> com.levviata.reflibquests.LootCrates.color(stack), com.levviata.reflibquests.ReflibQuestsMod.LOOT_CRATE);
        }
        net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(com.levviata.reflibquests.ReflibQuestsMod.QUEST_BOOK, 0,
                new net.minecraft.client.renderer.block.model.ModelResourceLocation(com.levviata.reflibquests.ReflibQuestsMod.QUEST_BOOK.getRegistryName(), "inventory"));
    }

    static void open() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!ClientQuests.ready()) {
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString("[Quests] No quest book from the server. Is Reflib Quests installed there?"));
            }
            return;
        }
        if (ClientQuests.book() != null && ClientQuests.book().settings.getBool("disable_gui", false) && !ClientQuests.canEdit()) {
            mc.getToastGui().add(QuestToast.blocked("The quest book is disabled"));
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
        if (event.phase == TickEvent.Phase.END) {
            checkToasts();
            TableEdits.tick();
        }
    }

    private static final java.util.Set<String> DONE_SEEN = new java.util.HashSet<>();
    private static final java.util.Set<String> CHAPTERS_SEEN = new java.util.HashSet<>();
    private static final java.util.Set<String> CLAIMED_SEEN = new java.util.HashSet<>();
    private static boolean toastsPrimed;
    private static int toastBookVersion = -1;
    private static int toastTicks;

    private static void notifyPlayer(Minecraft mc, String style, QuestToast toast, String chat) {
        if (style.equals("Toast")) {
            QuestToast.live++;
            mc.getToastGui().add(toast);
        } else if (style.equals("Chat")) {
            mc.player.sendMessage(new net.minecraft.util.text.TextComponentString("\u00a7a[Quests] \u00a7f" + chat));
        }
    }

    private static void checkToasts() {
        Minecraft mc = Minecraft.getMinecraft();
        ClientPrefs.ensureLoaded();
        if (!ClientQuests.ready() || mc.player == null) {
            toastsPrimed = false;
            return;
        }
        if (++toastTicks % 5 != 0) {
            return;
        }
        QuestBook book = ClientQuests.book();
        TeamProgress p = ClientQuests.progress();
        if (book == null || p == null) {
            return;
        }
        java.util.UUID me = mc.player.getUniqueID();
        boolean quiet = !toastsPrimed || toastBookVersion != ClientQuests.bookVersion();
        toastsPrimed = true;
        toastBookVersion = ClientQuests.bookVersion();
        if (quiet) {
            CHAPTERS_SEEN.clear();
            DONE_SEEN.clear();
            CLAIMED_SEEN.clear();
        }
        for (com.levviata.reflibquests.model.Definitions.Chapter ch : book.chapters()) {
            if (ch.quests.isEmpty() || !com.levviata.reflibquests.model.QuestRules.chapterCompleted(p, ch)) {
                CHAPTERS_SEEN.remove(ch.id);
            } else if (CHAPTERS_SEEN.add(ch.id) && !quiet && !ch.disableToast && !book.settings.getBool("disable_toast", false)) {
                net.minecraft.item.ItemStack chIcon = ch.icon.entries().isEmpty() ? null : QuestText.stackOf(ch.icon);
                net.minecraftforge.fluids.Fluid chFluid = null;
                if (chIcon == null && !ch.quests.isEmpty()) {
                    chIcon = QuestText.iconOf(ch.quests.get(0));
                    chFluid = QuestText.iconFluid(ch.quests.get(0));
                }
                QuestToast chapterToast = new QuestToast("Chapter completed!", com.levviata.reflibquests.text.RichText.stripCodes(ch.title.isEmpty() ? ch.id : ch.title), chIcon);
                chapterToast.fluid = chFluid;
                chapterToast.titleColor = 0xFF55FF;
                notifyPlayer(mc, ClientPrefs.completionNotification, chapterToast, "Chapter completed: " + com.levviata.reflibquests.text.RichText.stripCodes(ch.title));
                if (ClientPrefs.completionSounds && !ClientPrefs.completionNotification.equals("None")) {
                    mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f));
                }
            }
        }
        for (Quest q : book.allQuests()) {
            com.levviata.reflibquests.model.Definitions.Chapter toastChapter = book.chapterOf(q.id);
            if (!p.isCompleted(q.id)) {
                DONE_SEEN.remove(q.id);
            }
            if (p.isCompleted(q.id) && !DONE_SEEN.contains(q.id)) {
                com.levviata.reflibquests.ReflibQuestsMod.LOGGER.info("toast check {}: quiet={} questOff={} chapterOff={} bookOff={} style={}", q.id, quiet, q.disableToast,
                        toastChapter != null && toastChapter.disableToast, book.settings.getBool("disable_toast", false), ClientPrefs.completionNotification);
            }
            if (p.isCompleted(q.id) && DONE_SEEN.add(q.id) && !quiet && !q.disableToast && (toastChapter == null || !toastChapter.disableToast) && !book.settings.getBool("disable_toast", false)) {
                QuestToast questToast = new QuestToast("Quest completed!", QuestText.title(q), QuestText.iconOf(q));
                questToast.fluid = QuestText.iconFluid(q);
                notifyPlayer(mc, ClientPrefs.completionNotification, questToast,
                        "Quest completed: " + QuestText.titlePlain(q));
                if (ClientPrefs.completionSounds && !ClientPrefs.completionNotification.equals("None") && QuestToast.live <= 1) {
                    mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f));
                }
            }
            for (com.levviata.reflibquests.model.Definitions.Reward rw : q.rewards) {
                if (!p.isClaimed(rw.id(), me)) {
                    CLAIMED_SEEN.remove(rw.id());
                }
                if (p.isClaimed(rw.id(), me) && CLAIMED_SEEN.add(rw.id()) && !quiet && !QuestText.silentReward(book, rw)) {
                    String text = rw.type().equals("xp") ? "XP: \u00a7a+" + Math.max(1, rw.params().getInt("xp", 1)) : QuestText.reward(rw);
                    net.minecraft.item.ItemStack icon = rw.type().startsWith("xp")
                            ? new net.minecraft.item.ItemStack(net.minecraft.init.Items.EXPERIENCE_BOTTLE)
                            : rw.type().equals("item") ? QuestText.stackOf(rw.params().get("item")) : null;
                    notifyPlayer(mc, ClientPrefs.rewardNotification, new QuestToast("Reward Collected!", text, icon), "Reward collected: " + text);
                }
            }
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ClientQuests.clear();
        WAS_DONE.clear();
        CELEBRATE_UNTIL.clear();
    }

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
        ClientPrefs.ensureLoaded();
        if (ClientPrefs.trackerVisibility.equals("Hidden")) {
            return;
        }
        float sc = (float) ClientPrefs.pinnedScaling;
        boolean leftSide = ClientPrefs.pinnedPosition.equals("Left");
        int right = (int) (event.getResolution().getScaledWidth() / sc) - ClientPrefs.pinnedXInset;
        int y = ClientPrefs.pinnedYInset;
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.scale(sc, sc, 1f);
        long now = System.currentTimeMillis();
        WAS_DONE.keySet().retainAll(pins);
        CELEBRATE_UNTIL.keySet().retainAll(pins);
        for (String id : pins) {
            Quest q = book.quest(id);
            if (q == null) {
                continue;
            }
            boolean done = p.isCompleted(id);

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
            int titleW = Ui.width(fr, title);
            Ui.drawText(fr, title, (leftSide ? ClientPrefs.pinnedXInset : right - titleW), y, titleColor | 0xFF000000, Integer.MAX_VALUE);
            if (celebrating) {
                drawCheck(fr, (leftSide ? ClientPrefs.pinnedXInset : right - titleW) - 12, y - 1);
            }
            y += 10;
            if (!details || ClientPrefs.trackerVisibility.equals("Quests only")) {
                y += 4;
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
                fr.drawStringWithShadow(text, (leftSide ? ClientPrefs.pinnedXInset : right - fr.getStringWidth(text)), y, celebrating ? DONE_GREEN : 0xDDDDDD);
                y += 10;
            }
            y += 4;
        }
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }

    private static void drawCheck(FontRenderer fr, int x, int y) {
        if (QuestArt.available() && QuestArt.has("textures/gui/check_gray.png")) {
            QuestArt.quad(QuestArt.guiTexture("check_gray"), x, y, 10, 10, 0xFF55FF55);
        } else {
            fr.drawStringWithShadow(fr.getStringWidth("✔") > 0 ? "✔" : "v", x + 1, y + 1, DONE_GREEN);
        }
    }
}
