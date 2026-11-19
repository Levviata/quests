/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.game;

import java.io.File;
import java.util.UUID;

import com.levviata.reflib.api.data.DataFiles;
import com.levviata.reflibquests.model.ProgressCodec;
import com.levviata.reflibquests.model.TeamProgress;

/** Headless self-check used by {@code -Dreflibquests.smoke=true}: load, progress, save, reload from disk. */
public final class Smoke {

    private Smoke() {}

    public static String run(QuestServer qs) {
        try {
            if (qs.book().allQuests().size() < 6) {
                return "expected at least the 6 sample quests, found " + qs.book().allQuests().size();
            }
            if (qs.book().tables().size() != 1) {
                return "expected 1 reward table";
            }
            var dirt = qs.book().quest("q_dirt");
            if (dirt == null || !"Gather dirt".equals(dirt.title) || dirt.description.size() != 2) {
                return "translations were not applied";
            }
            if (qs.inertTasks().size() != 2) {
                return "expected 2 inert tasks (fluid, unknown item), found " + qs.inertTasks();
            }
            // the mapped 1.21 name must resolve through id_map.snbt
            if (ItemResolver.resolve(qs.book().task("t_log").params().get("item"), qs.idMap()) == null) {
                return "oak_log did not resolve through the id map";
            }
            java.util.List<String> table = BuiltinCheck.problems();
            if (!table.isEmpty()) {
                for (String line : table) {
                    com.levviata.reflibquests.ReflibQuestsMod.LOGGER.error("ID TABLE: {}", line);
                }
                return table.size() + " built-in id table entr(ies) do not match the game; first: " + table.get(0);
            }
            com.levviata.reflibquests.ReflibQuestsMod.LOGGER.info("ID TABLE: {} item and {} entity entries match the 1.12.2 registries",
                    com.levviata.reflibquests.io.BuiltinIdMap.items().size(), com.levviata.reflibquests.io.BuiltinIdMap.entities().size());
            UUID team = UUID.randomUUID();
            TeamProgress p = qs.progressOf(team);
            qs.apply(p, qs.engine().setTaskProgress(p, "t_dirt", 3, 1000L));
            if (!p.isCompleted("q_dirt")) {
                return "q_dirt did not complete";
            }
            qs.saveAll();
            File file = new File(new File(net.minecraftforge.common.DimensionManager.getCurrentSaveRootDirectory(), "data/reflibquests"), "team-" + team + ".snbt");
            if (!file.exists()) {
                return "progress file was not written: " + file;
            }
            TeamProgress back = ProgressCodec.read(team, DataFiles.read(file.toPath()));
            if (!back.isCompleted("q_dirt") || back.taskProgress("t_dirt") != 3) {
                return "progress did not survive a save/load";
            }
            return null;
        } catch (Throwable t) {
            return t.toString();
        }
    }
}
