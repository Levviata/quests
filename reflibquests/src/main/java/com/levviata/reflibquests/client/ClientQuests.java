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

public final class ClientQuests {

    private static QuestBook book;
    private static IdMap idMap = IdMap.withBuiltin(DataNode.Compound.empty());
    private static ProgressEngine engine;
    private static TeamProgress progress;
    private static int bookVersion;
    private static java.util.List<String> pins = java.util.List.of();
    private static boolean canEdit;

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

    public static void onDelta(byte[] packed) {
        if (book == null) {
            return;
        }
        try {
            DataNode.Compound c = Gz.unpack(packed);
            for (DataNode n : c.getList("chapters")) {
                if (!(n instanceof DataNode.Compound e)) {
                    continue;
                }
                String id = e.getString("id", "");
                int at = -1;
                for (int i = 0; i < book.chapters().size(); i++) {
                    if (book.chapters().get(i).id.equals(id)) {
                        at = i;
                    }
                }
                if (at >= 0) {
                    book.chapters().remove(at);
                }
                if (e.getInt("index", -1) >= 0) {
                    book.chapters().add(Math.min(e.getInt("index", 0), book.chapters().size()), BookCodec.chapterFrom(e.getCompound("data")));
                }
            }
            for (DataNode n : c.getList("tables")) {
                if (!(n instanceof DataNode.Compound e)) {
                    continue;
                }
                String id = e.getString("id", "");
                if (e.getCompound("data").entries().isEmpty()) {
                    book.tables().remove(id);
                } else {
                    book.tables().put(id, BookCodec.tableFrom(e.getCompound("data")));
                }
            }
            book.reindex();
            bookVersion++;
        } catch (IllegalArgumentException ex) {
            ReflibQuestsMod.LOGGER.error("Could not read the changed parts from the server: {}", ex.getMessage());
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

    public static void onPerms(boolean allowed) {
        canEdit = allowed;
    }

    private static int undoCount;
    private static int redoCount;
    private static String undoLabel = "";
    private static String redoLabel = "";

    public static void onHistory(int undo, int redo, String undoText, String redoText) {
        undoCount = undo;
        redoCount = redo;
        undoLabel = undoText;
        redoLabel = redoText;
        if (undo == 0 && redo == 0 && !TableEdits.hasPending()) {
            ChangeLog.clearHistoryNotes();
        }
    }

    public static int undoCount() {
        return undoCount;
    }

    public static int redoCount() {
        return redoCount;
    }

    public static String undoLabel() {
        return undoLabel;
    }

    public static String redoLabel() {
        return redoLabel;
    }

    public static boolean canEdit() {
        return canEdit;
    }

    public static void onPins(java.util.List<String> ids) {
        pins = java.util.List.copyOf(ids);
    }

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

    public static int bookVersion() {
        return bookVersion;
    }

    public static void clear() {
        book = null;
        progress = null;
        engine = null;
        pins = java.util.List.of();
        canEdit = false;
        idMap = IdMap.withBuiltin(DataNode.Compound.empty());
    }
}
