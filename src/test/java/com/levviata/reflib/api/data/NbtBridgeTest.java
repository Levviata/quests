/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.NBTTagCompound;

class NbtBridgeTest {

    @Test
    void roundTrips() {
        DataNode.Compound c = DataNode.Compound.empty()
                .with("s", "str").with("i", 4).with("l", 5L).with("d", 1.25).with("b", true)
                .with("list", new DataNode.Lst(List.of(new DataNode.Int(1), new DataNode.Int(2))))
                .with("objs", new DataNode.Lst(List.of(DataNode.Compound.empty().with("k", 1))))
                .with("nested", DataNode.Compound.empty().with("x", false));
        NBTTagCompound nbt = NbtBridge.toNbt(c);
        assertEquals("str", nbt.getString("s"));
        assertEquals(1, nbt.getByte("b"));
        assertEquals(c, NbtBridge.fromNbt(nbt));
    }

    @Test
    void mixedListsAreRejected() {
        DataNode.Compound c = DataNode.Compound.empty()
                .with("mixed", new DataNode.Lst(List.of(new DataNode.Int(1), new DataNode.Str("x"))));
        assertThrows(IllegalArgumentException.class, () -> NbtBridge.toNbt(c));
    }

    @Test
    void bytesOutsideBooleanRangeReadAsInt() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setByte("b", (byte) 7);
        assertEquals(new DataNode.Int(7), NbtBridge.fromNbt(nbt).get("b"));
    }

    @Test
    void intArraysReadAsLists() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setIntArray("a", new int[] {1, 2});
        assertEquals(List.of(new DataNode.Int(1), new DataNode.Int(2)), NbtBridge.fromNbt(nbt).getList("a"));
    }
}
