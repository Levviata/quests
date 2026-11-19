/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibteams;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.levviata.reflib.api.data.DataFiles;
import com.levviata.reflib.api.project.ReflibProjects;
import com.levviata.reflib.api.team.Teams;

/**
 * Party teams for reflib-based mods (such as Reflib Quests). Server side only: clients do not need this mod.
 * Players form parties with {@code /rteam}; mods that ask reflib's {@link Teams} for a player's team then see
 * the party, so progress is shared. Parties are saved in the world under {@code data/reflibteams}.
 */
@Mod(modid = ReflibTeamsMod.ID, name = "Reflib Teams", version = ReflibTeamsMod.VERSION,
        dependencies = "required-after:reflib", acceptableRemoteVersions = "*")
public class ReflibTeamsMod {

    public static final String ID = "reflibteams";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LogManager.getLogger("reflibteams");

    private int ticks;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ReflibProjects.register(ID, VERSION, 3);
        try {
            Teams.install(new PartyTeamProvider());
        } catch (IllegalStateException ex) {
            LOGGER.error("Another mod already provides teams; Reflib Teams will not be used: {}", ex.getMessage());
        }
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Reflib Teams {} loaded", VERSION);
    }

    private static Path file() {
        File root = DimensionManager.getCurrentSaveRootDirectory();
        return new File(root == null ? new File(".") : root, "data/reflibteams/parties.snbt").toPath();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        PartyManager m = new PartyManager();
        Path file = file();
        if (Files.exists(file)) {
            try {
                m = PartyManager.fromData(DataFiles.read(file));
                LOGGER.info("Loaded {} part(ies)", m.parties().size());
            } catch (IOException | IllegalArgumentException ex) {
                LOGGER.error("Cannot read {}; starting with no parties (the file is left untouched): {}", file, ex.getMessage());
            }
        }
        m.setMaxMembers(TeamsConfig.maxMembers);
        PartyTeamProvider.manager = m;
        event.registerServerCommand(new PartyCommand());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        save();
    }

    static void save() {
        PartyManager m = PartyTeamProvider.manager;
        try {
            DataFiles.write(file(), m.toData());
            m.clearDirty();
        } catch (IOException ex) {
            LOGGER.error("Cannot save parties: {}", ex.getMessage());
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            PartyTeamProvider.manager.rememberName(event.player.getUniqueID(), event.player.getName());
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ++ticks % 600 == 0 && PartyTeamProvider.manager.isDirty()) {
            save();
        }
    }
}
