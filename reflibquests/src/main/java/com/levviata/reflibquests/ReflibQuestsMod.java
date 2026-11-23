/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import com.levviata.reflib.api.project.ReflibProjects;
import com.levviata.reflibquests.game.QuestCommand;
import com.levviata.reflibquests.game.QuestServer;

@Mod(modid = ReflibQuestsMod.ID, name = "Reflib Quests", version = ReflibQuestsMod.VERSION,
        dependencies = "required-after:reflib")
public class ReflibQuestsMod {

    public static final String ID = "reflibquests";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LogManager.getLogger("reflibquests");

    public static final net.minecraft.item.Item QUEST_BOOK = new QuestBookItem();

    private int serverTicks;

    @SubscribeEvent
    public void registerItems(net.minecraftforge.event.RegistryEvent.Register<net.minecraft.item.Item> event) {
        event.getRegistry().register(QUEST_BOOK);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ReflibProjects.register(ID, VERSION, 4);
        net.minecraftforge.oredict.OreDictionary.registerOre("book", net.minecraft.init.Items.BOOK);
        com.levviata.reflib.api.assets.SuppliedAssets.declareOptional(ID, "FTB Quests 1.20 or newer (art for the quest map)",
                "https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge", "ftbquests");
        com.levviata.reflibquests.net.QuestNet.init();
        MinecraftForge.EVENT_BUS.register(this);
        if (event.getSide() == Side.CLIENT) {
            com.levviata.reflibquests.client.ClientBootstrap.init();
        }
        LOGGER.info("Reflib Quests {} loaded", VERSION);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new QuestCommand());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        QuestServer.start(server);
        if (Boolean.getBoolean("reflibquests.smoke")) {
            String failure = com.levviata.reflibquests.game.Smoke.run(QuestServer.get());
            if (failure == null) {
                LOGGER.info("REFQUESTS-SMOKE: PASS");
            } else {
                LOGGER.error("REFQUESTS-SMOKE: FAIL - {}", failure);
            }
            server.initiateShutdown();
        }
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        QuestServer.stop();
    }

    @SubscribeEvent
    public void onLogin(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
        QuestServer qs = QuestServer.get();
        if (qs != null && event.player instanceof EntityPlayerMP) {
            qs.syncPlayer((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        QuestServer qs = QuestServer.get();
        if (qs != null && event.phase == TickEvent.Phase.END) {
            qs.tick(++serverTicks);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side == Side.SERVER && event.player instanceof EntityPlayerMP) {
            QuestServer qs = QuestServer.get();
            if (qs != null) {
                qs.tracker().tick((EntityPlayerMP) event.player);
            }
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        QuestServer qs = QuestServer.get();
        if (qs != null && event.getSource().getTrueSource() instanceof EntityPlayerMP) {
            qs.tracker().onKill((EntityPlayerMP) event.getSource().getTrueSource(), event.getEntityLiving());
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        QuestServer qs = QuestServer.get();
        if (qs != null && event.getHand() == net.minecraft.util.EnumHand.MAIN_HAND && event.getEntityPlayer() instanceof EntityPlayerMP) {
            net.minecraft.util.ResourceLocation key = event.getWorld().getBlockState(event.getPos()).getBlock().getRegistryName();
            if (key != null) {
                qs.tracker().onInteract((EntityPlayerMP) event.getEntityPlayer(), false, key.toString());
            }
        }
    }

    @SubscribeEvent
    public void onEntityInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        QuestServer qs = QuestServer.get();
        if (qs != null && event.getHand() == net.minecraft.util.EnumHand.MAIN_HAND && event.getEntityPlayer() instanceof EntityPlayerMP) {
            net.minecraft.util.ResourceLocation key = net.minecraft.entity.EntityList.getKey(event.getTarget());
            if (key != null) {
                qs.tracker().onInteract((EntityPlayerMP) event.getEntityPlayer(), true, key.toString());
            }
        }
    }

    @SubscribeEvent
    public void onCrafted(net.minecraftforge.fml.common.gameevent.PlayerEvent.ItemCraftedEvent event) {
        QuestServer qs = QuestServer.get();
        if (qs != null && event.player instanceof EntityPlayerMP) {
            qs.tracker().onCraft((EntityPlayerMP) event.player, event.crafting);
        }
    }

    @SubscribeEvent
    public void onAdvancement(AdvancementEvent event) {
        QuestServer qs = QuestServer.get();
        if (qs != null && event.getEntityPlayer() instanceof EntityPlayerMP) {
            qs.tracker().onAdvancement((EntityPlayerMP) event.getEntityPlayer(), event.getAdvancement().getId());
        }
    }
}
