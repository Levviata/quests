/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.levviata.reflib.api.data.DataNode;
import com.levviata.reflibquests.model.Definitions.Task;

class IdsTableTest {

    @Test
    void numericTableIdGivesTheHexKeyOfTheTableFile() {

        DataNode.Compound p = DataNode.Compound.empty().with("table_id", new DataNode.Lng(8688099075117598374L));
        assertEquals("78925413C9C8C6A6", Ids.tableId(p));
    }

    @Test
    void textTableIdsStillWork() {
        assertEquals("ABC123", Ids.tableId(DataNode.Compound.empty().with("table_id", "abc123")));
        assertEquals("78925413C9C8C6A6", Ids.tableId(DataNode.Compound.empty().with("table_id", "8688099075117598374")));
        assertEquals("", Ids.tableId(DataNode.Compound.empty()));
    }

    @Test
    void itemTaskCountIsReadFromTheNestedItem() {
        DataNode.Compound nested = DataNode.Compound.empty().with("item", DataNode.Compound.empty().with("id", "minecraft:iron_ingot").with("count", 7));
        assertEquals(7, Task.itemCount(nested));
        assertEquals(3, Task.itemCount(nested.with("count", 3)));
        assertEquals(1, Task.itemCount(DataNode.Compound.empty()));
    }
}
