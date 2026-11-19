/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class IntegrationsTest {

    @Test
    void versionRanges() {
        assertTrue(Integrations.versionMatches("[4.16,)", "4.16.1.302"));
        assertFalse(Integrations.versionMatches("[4.16,)", "4.15.0"));
        assertTrue(Integrations.versionMatches("*", "anything"));
        assertFalse(Integrations.versionMatches("[1.0,2.0)", "2.0"));
        assertThrows(IllegalArgumentException.class, () -> Integrations.versionMatches("[broken", "1"));
    }

    @Test
    void runsOnlyForPresentMatchingMods() {
        AtomicInteger present = new AtomicInteger();
        AtomicInteger absent = new AtomicInteger();
        AtomicInteger wrongVersion = new AtomicInteger();
        AtomicInteger failing = new AtomicInteger();
        Integrations.register("present", "*", () -> present::incrementAndGet);
        Integrations.register("absent", "*", () -> absent::incrementAndGet);
        Integrations.register("old", "[2.0,)", () -> wrongVersion::incrementAndGet);
        Integrations.register("bad", "*", () -> () -> {
            failing.incrementAndGet();
            throw new NoClassDefFoundError("simulated missing class");
        });
        Map<String, String> installed = Map.of("present", "1.0", "old", "1.0", "bad", "1.0");
        int activated = Integrations.activateAll(installed::get);
        assertEquals(1, activated);
        assertEquals(1, present.get());
        assertEquals(0, absent.get());
        assertEquals(0, wrongVersion.get());
        assertEquals(1, failing.get(), "a failing integration runs once and does not crash the caller");
    }

    @Test
    void factoryIsNotCalledWhenModIsAbsent() {
        AtomicInteger created = new AtomicInteger();
        Integrations.register("nobody", "*", () -> {
            created.incrementAndGet();
            return () -> {};
        });
        Integrations.activateAll(id -> null);
        assertEquals(0, created.get());
    }
}
