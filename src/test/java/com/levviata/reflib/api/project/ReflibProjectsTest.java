/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.project;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.levviata.reflib.api.ReflibApi;

class ReflibProjectsTest {

    @BeforeEach
    void reset() {
        ReflibProjects.clearForTests();
    }

    @Test
    void registersAndListsInOrder() {
        ReflibProjects.register("a", "1.0", 1);
        ReflibProjects.register("b", "2.0", 1);
        assertEquals("a", ReflibProjects.all().get(0).modId());
        assertEquals(2, ReflibProjects.all().size());
    }

    @Test
    void rejectsDuplicates() {
        ReflibProjects.register("a", "1.0", 1);
        assertThrows(IllegalStateException.class, () -> ReflibProjects.register("a", "1.1", 1));
    }

    @Test
    void rejectsTooNewApiLevelWithReadableMessage() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ReflibProjects.register("x", "1", ReflibApi.API_LEVEL + 1));
        assertTrue(ex.getMessage().contains("Update reflib"));
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> ReflibProjects.register(" ", "1", 1));
        assertThrows(IllegalArgumentException.class, () -> ReflibProjects.register("x", "1", 0));
    }
}
