/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflib.api.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SnbtTest {

    @Test
    void parsesAllTypes() {
        DataNode.Compound c = Snbt.parse("""
                {
                  s: "hi \\"there\\"\\n"
                  single: 'x'
                  i: 5
                  neg: -7
                  big: 3000000000
                  l: 9L
                  d: 1.5
                  d2: 2d
                  e: 1.0E3
                  t: true
                  f: false
                  list: [1, 2 3]
                  nested: { a: 1, "quoted key": 2 }
                }
                """);
        assertEquals("hi \"there\"\n", c.getString("s", null));
        assertEquals("x", c.getString("single", null));
        assertEquals(5, c.getInt("i", 0));
        assertEquals(-7, c.getInt("neg", 0));
        assertEquals(new DataNode.Lng(3_000_000_000L), c.get("big"));
        assertEquals(9L, c.getLong("l", 0));
        assertEquals(1.5, c.getDouble("d", 0));
        assertEquals(new DataNode.Dbl(2.0), c.get("d2"));
        assertEquals(1000.0, c.getDouble("e", 0));
        assertTrue(c.getBool("t", false));
        assertFalse(c.getBool("f", true));
        assertEquals(List.of(new DataNode.Int(1), new DataNode.Int(2), new DataNode.Int(3)), c.getList("list"));
        assertEquals(2, c.getCompound("nested").getInt("quoted key", 0));
    }

    @Test
    void acceptsJson5AndLegacySnbtSyntax() {
        DataNode.Compound c = Snbt.parse("""
                /* block
                   comment */
                {
                  "quoted": 1,
                  hex: 0xFF,
                  neg_hex: -0x10,
                  plus: +5,
                  flag: 1b
                  small: 3s
                  f: 1.5f
                  gone: null
                  list: [1, null, 2,],
                }
                """);
        assertEquals(255, c.getInt("hex", 0));
        assertEquals(-16, c.getInt("neg_hex", 0));
        assertEquals(5, c.getInt("plus", 0));
        assertTrue(c.getBool("flag", false));
        assertEquals(3, c.getInt("small", 0));
        assertEquals(1.5, c.getDouble("f", 0));
        assertFalse(c.has("gone"));
        assertEquals(List.of(new DataNode.Int(1), new DataNode.Int(2)), c.getList("list"));
        assertThrows(SnbtException.class, () -> Snbt.parse("/* open { a: 1 }"));
        assertEquals(2, Snbt.parse("{ a: null, a: 2 }").getInt("a", 0), "null is absent, so a later value is not a duplicate");
        assertThrows(SnbtException.class, () -> Snbt.parse("{ a: 1, a: null }"));
    }

    @Test
    void typedArraysAreReadAsPlainLists() {
        DataNode.Compound c = Snbt.parse("{ a: [I; 1, 2, 3], b: [L; 5L, 6L], c: [B; 0b, 1b], d: [ I ; ], e: [1, 2] }");
        assertEquals(List.of(new DataNode.Int(1), new DataNode.Int(2), new DataNode.Int(3)), c.getList("a"));
        assertEquals(List.of(new DataNode.Lng(5), new DataNode.Lng(6)), c.getList("b"));
        assertEquals(2, c.getList("c").size());
        assertTrue(c.getList("d").isEmpty());
        assertEquals(2, c.getList("e").size());
        assertThrows(SnbtException.class, () -> Snbt.parse("{ a: [X; 1] }"));
    }

    @Test
    void lenientParsingKeepsTheLastDuplicate() {
        assertThrows(SnbtException.class, () -> Snbt.parse("{ a: 1, a: 2 }"));
        assertEquals(2, Snbt.parseLenient("{ a: 1, a: 2 }").getInt("a", 0));
        assertEquals(2, Snbt.parseLenient("{ a: null, a: 2 }").getInt("a", 0));
    }

    @Test
    void commentsAndOptionalCommas() {
        DataNode.Compound c = Snbt.parse("# header\n{ a: 1 // trailing\n b: 2, c: 3, }");
        assertEquals(3, c.entries().size());
    }

    @Test
    void roundTripPrettyAndCompact() {
        DataNode.Compound original = DataNode.Compound.empty()
                .with("title", "A \"quoted\" \\ title\twith tab\u0001")
                .with("n", 1).with("l", -5L).with("d", 0.1).with("t", true)
                .with("list", new DataNode.Lst(List.of(new DataNode.Int(1), new DataNode.Int(2))))
                .with("objs", new DataNode.Lst(List.of(
                        DataNode.Compound.empty().with("k", "v"), DataNode.Compound.empty())))
                .with("weird key!", 1)
                .with("empty", new DataNode.Lst(List.of()));
        assertEquals(original, Snbt.parse(Snbt.print(original)));
        assertEquals(original, Snbt.parse(Snbt.printCompact(original)));
    }

    @Test
    void keyOrderIsPreserved() {
        DataNode.Compound c = Snbt.parse("{ z: 1 a: 2 m: 3 }");
        assertEquals(List.of("z", "a", "m"), List.copyOf(c.entries().keySet()));
    }

    @Test
    void errorsCarryPosition() {
        SnbtException ex = assertThrows(SnbtException.class, () -> Snbt.parse("{\n  a: 1\n  b: nope\n}"));
        assertEquals(3, ex.getLine());
        assertThrows(SnbtException.class, () -> Snbt.parse("{ a: 1"));
        assertThrows(SnbtException.class, () -> Snbt.parse("{ a: 1 } extra"));
        assertThrows(SnbtException.class, () -> Snbt.parse("{ a: 1, a: 2 }"));
        assertThrows(SnbtException.class, () -> Snbt.parse("{ a: \"open }"));
        assertThrows(SnbtException.class, () -> Snbt.parse("{ a: 99999999999999999999 }"));
    }

    @Test
    void refusesNanAndInfinity() {
        assertThrows(IllegalArgumentException.class, () -> Snbt.print(DataNode.Compound.empty().with("x", Double.NaN)));
    }

    @Test
    void compoundIsImmutable() {
        DataNode.Compound a = DataNode.Compound.empty();
        a.with("k", 1);
        assertTrue(a.entries().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> a.entries().put("x", new DataNode.Int(1)));
        assertEquals(Map.of(), a.entries());
    }
}
