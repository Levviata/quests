/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.client;

import java.util.UUID;

import javax.annotation.Nullable;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.ReflibQuestsMod;
import com.levviata.reflibquests.io.BookCodec;
import com.levviata.reflibquests.io.Gz;
import com.levviata.reflibquests.io.IdMap;
import com.levviata.reflibquests.model.ProgressCodec;
import com.levviata.reflibquests.model.ProgressEngine;
import com.levviata.reflibquests.model.QuestBook;
import com.levviata.reflibquests.model.TeamProgress;

/**
 * The client's copy of the quest book and of its team's progress, as last synced from the server. Holds only
 * model data (no client classes), so it is safe to reference from common code. All access is on the client
 * thread.
 */
public final class ClientQuests {

    private static QuestBook book;
    private static IdMap idMap = IdMap.withBuiltin(DataNode.Compound.empty());
    private static ProgressEngine engine;
    private static TeamProgress progress;
    private static int bookVersion;
    private static java.util.List<String> pins = java.util.List.of();

    private ClientQuests() {}

    public static void onBook(byte[] packed) {
        try {
            BookCodec.Decoded d = BookCodec.read(Gz.unpack(packed));
            book = d.book();
            idMap = IdMap.withBuiltin(d.idMap());
            engine = new ProgressEngine(book);
            bookVersion++;
            ReflibQuestsMod.LOGGER.info("Quest book synced: {} chapters, {} quests ({} bytes packed)",
                    book.chapters().size(), book.allQuests().size(), packed.length);
        } catch (IllegalArgumentException ex) {
            ReflibQuestsMod.LOGGER.error("Could not read the quest book from the server: {}", ex.getMessage());
        }
    }

    public static void onProgress(byte[] packed) {
        try {
            DataNode.Compound c = Gz.unpack(packed);
            progress = ProgressCodec.read(UUID.fromString(c.getString("team", "")), c);
        } catch (IllegalArgumentException ex) {
            ReflibQuestsMod.LOGGER.error("Could not read quest progress from the server: {}", ex.getMessage());
        }
    }

    public static void onPins(java.util.List<String> ids) {
        pins = java.util.List.copyOf(ids);
    }

    /** Ids of the quests this player pinned, in the order pinned. */
    public static java.util.List<String> pins() {
        return pins;
    }

    public static boolean ready() {
        return book != null && progress != null;
    }

    @Nullable
    public static QuestBook book() {
        return book;
    }

    public static IdMap idMap() {
        return idMap;
    }

    @Nullable
    public static ProgressEngine engine() {
        return engine;
    }

    @Nullable
    public static TeamProgress progress() {
        return progress;
    }

    /** Increases whenever a new book arrives, so screens can drop cached layout. */
    public static int bookVersion() {
        return bookVersion;
    }

    public static void clear() {
        book = null;
        progress = null;
        engine = null;
        pins = java.util.List.of();
        idMap = IdMap.withBuiltin(DataNode.Compound.empty());
    }
}
