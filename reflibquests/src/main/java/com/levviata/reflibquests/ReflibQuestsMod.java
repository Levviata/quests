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
    public static final net.minecraft.item.Item LOOT_CRATE = new LootCrateItem();

    private int serverTicks;

    @SubscribeEvent
    public void registerItems(net.minecraftforge.event.RegistryEvent.Register<net.minecraft.item.Item> event) {
        event.getRegistry().register(QUEST_BOOK);
        event.getRegistry().register(LOOT_CRATE);
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

    private static final java.util.Map<java.util.UUID, java.util.List<net.minecraft.item.ItemStack>> KEPT_BOOKS = new java.util.concurrent.ConcurrentHashMap<>();

    @SubscribeEvent
    public void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        QuestServer qs = QuestServer.get();
        if (qs == null) {
            return;
        }
        com.levviata.reflibquests.model.QuestBook book = qs.book();
        net.minecraft.entity.EntityLivingBase dying = event.getEntityLiving();
        if (dying instanceof EntityPlayerMP) {
            if (!book.settings.getBool("drop_book_on_death", false)) {
                java.util.List<net.minecraft.item.ItemStack> kept = new java.util.ArrayList<>();
                java.util.Iterator<net.minecraft.entity.item.EntityItem> it = event.getDrops().iterator();
                while (it.hasNext()) {
                    net.minecraft.item.ItemStack stack = it.next().getItem();
                    if (stack.getItem() == QUEST_BOOK) {
                        kept.add(stack.copy());
                        it.remove();
                    }
                }
                if (!kept.isEmpty()) {
                    KEPT_BOOKS.put(dying.getUniqueID(), kept);
                }
            }
            return;
        }
        if (!book.settings.getBool("drop_loot_crates", false) || !(event.getSource().getTrueSource() instanceof EntityPlayerMP)) {
            return;
        }
        String category = !dying.isNonBoss() ? "boss" : dying instanceof net.minecraft.entity.monster.IMob ? "monster" : "passive";
        int noDrop = book.settings.getCompound("loot_crate_no_drop").getInt(category, category.equals("passive") ? 4000 : category.equals("monster") ? 600 : 0);
        java.util.List<com.levviata.reflibquests.model.Definitions.RewardTable> tables = new java.util.ArrayList<>();
        int total = 0;
        for (com.levviata.reflibquests.model.Definitions.RewardTable t : book.tables().values()) {
            int weight = t.raw.getCompound("loot_crate").getCompound("drops").getInt(category, 0);
            if (LootCrates.enabled(t) && weight > 0) {
                tables.add(t);
                total += weight;
            }
        }
        if (total <= 0) {
            return;
        }
        int roll = dying.world.rand.nextInt(total + Math.max(0, noDrop));
        if (roll >= total) {
            return;
        }
        for (com.levviata.reflibquests.model.Definitions.RewardTable t : tables) {
            roll -= t.raw.getCompound("loot_crate").getCompound("drops").getInt(category, 0);
            if (roll < 0) {
                event.getDrops().add(new net.minecraft.entity.item.EntityItem(dying.world, dying.posX, dying.posY, dying.posZ, LootCrates.stackFor(t)));
                return;
            }
        }
    }

    @SubscribeEvent
    public void onRespawn(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent event) {
        java.util.List<net.minecraft.item.ItemStack> kept = KEPT_BOOKS.remove(event.player.getUniqueID());
        if (kept != null) {
            for (net.minecraft.item.ItemStack stack : kept) {
                if (!event.player.inventory.addItemStackToInventory(stack)) {
                    event.player.dropItem(stack, false);
                }
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
