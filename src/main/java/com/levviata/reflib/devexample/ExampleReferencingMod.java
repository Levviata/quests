/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.devexample;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.relauncher.Side;

import com.levviata.reflib.api.ReflibApi;
import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflib.api.data.DataUpgrader;
import com.levviata.reflib.api.data.NbtBridge;
import com.levviata.reflib.api.data.Snbt;
import com.levviata.reflib.api.integration.Integrations;
import com.levviata.reflib.api.net.ReflibChannel;
import com.levviata.reflib.api.project.ReflibProjects;
import com.levviata.reflib.api.team.Teams;

/**
 * Development-only example of a referencing project. It is a separate mod (id {@code reflibexample}) that uses
 * only {@code com.levviata.reflib.api}, it is excluded from the release jar, and it doubles as the in-game
 * smoke test: start a server with {@code -Dreflib.smoke=true} and it checks the library, prints
 * {@code REFLIB-SMOKE: PASS} or {@code FAIL}, and stops the server.
 */
@Mod(modid = ExampleReferencingMod.ID, name = "Reflib Example", version = "0.1.0", dependencies = "required-after:reflib")
public class ExampleReferencingMod {

    public static final String ID = "reflibexample";
    private static final Logger LOGGER = LogManager.getLogger("reflibexample");

    private ReflibChannel channel;
    private final AtomicBoolean optionalIntegrationRan = new AtomicBoolean();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // 1. identify ourselves and declare the API level we need
        ReflibProjects.register(ID, "0.1.0", 1);

        // 2. typed messages: one channel per project
        channel = ReflibChannel.create(ID)
                .register(PingMessage.class, Side.SERVER, (msg, ctx) ->
                        LOGGER.info("Server got ping #{} from {}", msg.counter, ctx.sender()))
                .register(PongMessage.class, Side.CLIENT, (msg, ctx) ->
                        LOGGER.info("Client got pong '{}'", msg.text));

        // 3. an optional integration that must NOT run (the target mod does not exist)
        Integrations.register("definitely_not_installed", "*", () -> () -> optionalIntegrationRan.set(true));
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        Integrations.activateAll();
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (!Boolean.getBoolean("reflib.smoke")) {
            return;
        }
        String failure = runSmokeTest();
        if (failure == null) {
            LOGGER.info("REFLIB-SMOKE: PASS");
        } else {
            LOGGER.error("REFLIB-SMOKE: FAIL - {}", failure);
        }
        FMLCommonHandler.instance().getMinecraftServerInstance().initiateShutdown();
    }

    /** @return null on success, otherwise a description of the first failed check */
    private String runSmokeTest() {
        try {
            if (ReflibProjects.all().stream().noneMatch(p -> p.modId().equals(ID))) {
                return "project not registered";
            }
            if (ReflibApi.API_LEVEL < 1) {
                return "bad API level";
            }
            if (optionalIntegrationRan.get()) {
                return "integration for an absent mod ran";
            }

            // text codec round trip, upgrade, and NBT bridge, all inside the real game classpath
            DataNode.Compound quest = Snbt.parse("""
                    {
                      title: "First steps"   // comment
                      xp: 50L
                      optional: true
                      deps: [1, 2, 3]
                      rewards: [ { item: "minecraft:apple", count: 3 } ]
                    }
                    """);
            if (!Snbt.parse(Snbt.print(quest)).equals(quest)) {
                return "text round trip mismatch";
            }
            if (!NbtBridge.fromNbt(NbtBridge.toNbt(quest)).equals(quest)) {
                return "NBT round trip mismatch";
            }
            DataUpgrader upgrader = new DataUpgrader(1).step(0, c -> c.with("renamed", c.getString("title", "")));
            if (!"First steps".equals(upgrader.upgrade(quest).getString("renamed", null))) {
                return "upgrade step did not run";
            }

            UUID player = UUID.randomUUID();
            if (!Teams.get().teamOf(player).equals(player) || !Teams.get().membersOf(player).contains(player)) {
                return "default team provider wrong";
            }
            if (channel == null) {
                return "channel missing";
            }
            return null;
        } catch (Throwable t) {
            LOGGER.error("smoke test threw", t);
            return t.toString();
        }
    }
}
