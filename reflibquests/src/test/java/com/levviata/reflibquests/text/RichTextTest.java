/*
 * Copyright (c) 2026 Levviata
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.levviata.reflibquests.text;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.levviata.reflibquests.text.RichText.Align;
import com.levviata.reflibquests.text.RichText.Image;
import com.levviata.reflibquests.text.RichText.Line;
import com.levviata.reflibquests.text.RichText.Page;
import com.levviata.reflibquests.text.RichText.Span;

class RichTextTest {

    private static Span span(Line l, int i) {
        return (Span) l.elements().get(i);
    }

    @Test
    void colourAndStyleCodesApplyUntilReset() {
        List<Line> lines = RichText.parseEntry("plain &6gold&r back &l&bbold aqua&r");
        assertEquals(1, lines.size());
        Line l = lines.get(0);
        assertEquals("plain ", span(l, 0).text());
        assertEquals(RichText.NO_COLOR, span(l, 0).rgb());
        assertEquals("gold", span(l, 1).text());
        assertEquals(0xFFAA00, span(l, 1).rgb());
        assertEquals(" back ", span(l, 2).text());
        assertFalse(span(l, 2).bold());
        assertEquals("bold aqua", span(l, 3).text());
        assertEquals(0x55FFFF, span(l, 3).rgb());
        assertFalse(span(l, 3).bold(), "a colour code after &l resets bold, as in the game");
    }

    @Test
    void styleCodesStackWithoutColourChange() {
        Line l = RichText.parseEntry("&lbold&oitalic&nunder&mstrike&kobf").get(0);
        assertTrue(span(l, 0).bold());
        assertTrue(span(l, 1).bold() && span(l, 1).italic());
        assertTrue(span(l, 4).bold() && span(l, 4).italic() && span(l, 4).underline() && span(l, 4).strike() && span(l, 4).obfuscated());
    }

    @Test
    void codesDoNotLeakAcrossEntries() {
        List<Line> a = RichText.parseEntry("&6gold with no reset");
        List<Line> b = RichText.parseEntry("next entry");
        assertEquals(0xFFAA00, span(a.get(0), 0).rgb());
        assertEquals(RichText.NO_COLOR, span(b.get(0), 0).rgb());
    }

    @Test
    void ampersandEscapesAndUnknownCodes() {
        Line l = RichText.parseEntry("Tom && Jerry &q end &").get(0);
        assertEquals(1, l.elements().size());
        assertEquals("Tom & Jerry &q end &", span(l, 0).text());
    }

    @Test
    void convertLineToJsonKeepsTheLook() {
        String json = RichText.toJsonLine("&6gold&r plain &lbold");
        assertEquals("[{\"text\":\"gold\",\"color\":\"#FFAA00\"},{\"text\":\" plain \"},{\"text\":\"bold\",\"bold\":true}]", json);
        Line l = RichText.parseEntry(json).get(0);
        assertEquals("gold", span(l, 0).text());
        assertEquals(0xFFAA00, span(l, 0).rgb());
        assertTrue(span(l, 2).bold());
        assertNull(RichText.toJsonLine("{@pagebreak}"));
        assertNull(RichText.toJsonLine("two" + (char) 92 + "nrows"), "an entry with a paragraph break is left alone");
    }

    @Test
    void linkJsonIsAClickableSpan() {
        Span s = span(RichText.parseEntry(RichText.linkJson("say \"hi\"", "open_url", "https://example.org")).get(0), 0);
        assertEquals("say \"hi\"", s.text());
        assertEquals("open_url", s.click().action());
        assertEquals("https://example.org", s.click().value());
    }

    @Test
    void ampersandZIsTheRainbowCode() {
        Line l = RichText.parseEntry("plain &zrainbow&r plain").get(0);
        assertFalse(span(l, 0).rainbow());
        assertTrue(span(l, 1).rainbow());
        assertEquals("rainbow", span(l, 1).text());
        assertFalse(span(l, 2).rainbow(), "&r ends the rainbow");
        assertEquals("a rainbow b", RichText.stripCodes("a &zrainbow b"));
        assertEquals("a rainbow b", RichText.toSectionCodes("a &zrainbow b"));
        assertEquals("a " + RichText.RAINBOW_MARK + "rainbow b", RichText.toRainbowAware("a &zrainbow b"));
    }

    @Test
    void literalBackslashNIsALineBreak() {
        List<Line> lines = RichText.parseEntry("one\\ntwo\\n\\nfour");
        assertEquals(4, lines.size());
        assertEquals("one", span(lines.get(0), 0).text());
        assertTrue(lines.get(2).blank(), "\\n\\n makes a blank line");
        assertEquals("four", span(lines.get(3), 0).text());
    }

    @Test
    void styleCarriesAcrossLineBreaksInsideOneEntry() {
        List<Line> lines = RichText.parseEntry("&6gold\\nstill gold");
        assertEquals(0xFFAA00, span(lines.get(1), 0).rgb());
    }

    @Test
    void imageTagWithAllAttributes() {
        Line l = RichText.parseEntry("{image:atm:textures/questpics/a.png width:125 height:100 align:right fit:true}").get(0);
        Image img = (Image) l.elements().get(0);
        assertEquals("atm:textures/questpics/a.png", img.texture());
        assertEquals(125, img.width());
        assertEquals(100, img.height());
        assertEquals(Align.RIGHT, img.align());
        assertTrue(img.fit());
    }

    @Test
    void imageTagDefaultsAndClamping() {
        Image img = (Image) RichText.parseEntry("{image:a:b.png}").get(0).elements().get(0);
        assertEquals(100, img.width());
        assertEquals(Align.CENTER, img.align());
        assertFalse(img.fit());
        Image big = (Image) RichText.parseEntry("{image:a:b.png width:99999 height:0}").get(0).elements().get(0);
        assertEquals(4096, big.width());
        assertEquals(1, big.height());
    }

    @Test
    void malformedImageTagIsShownLiterally() {
        for (String bad : List.of("{image:}", "{image:a:b.png width:abc}", "{image:a:b.png align:middle}", "{image:a:b.png fit:maybe}", "{image:a:b.png junk}")) {
            Line l = RichText.parseEntry(bad).get(0);
            assertEquals(bad, span(l, 0).text(), bad);
        }
    }

    @Test
    void imageTagBuilderRoundTrips() {
        String tag = RichText.imageTag("relics:textures/x.png", 50, 200, Align.LEFT, true);
        assertEquals("{image:relics:textures/x.png width:50 height:200 align:left fit:true}", tag);
        Image img = (Image) RichText.parseEntry(tag).get(0).elements().get(0);
        assertEquals(50, img.width());
        assertEquals(200, img.height());
        assertEquals(Align.LEFT, img.align());
    }

    @Test
    void jsonComponentWithLink() {
        String json = "[ { \"text\": \"hi in a link\", \"underlined\": true, \"clickEvent\": { \"action\": \"open_url\", \"value\": \"hi.com\" } } ]";
        Line l = RichText.parseEntry(json).get(0);
        Span s = span(l, 0);
        assertEquals("hi in a link", s.text());
        assertTrue(s.underline());
        assertEquals("open_url", s.click().action());
        assertEquals("hi.com", s.click().value());
    }

    @Test
    void jsonQuestLinkUsesChangePage() {
        String json = "[ { \"text\": \"go\", \"clickEvent\": { \"action\": \"change_page\", \"value\": \"64C105E1E2F97900\" } } ]";
        Span s = span(RichText.parseEntry(json).get(0), 0);
        assertEquals("change_page", s.click().action());
        assertEquals("64C105E1E2F97900", s.click().value());
    }

    @Test
    void jsonExtraInheritsStyleAndColour() {
        Line l = RichText.parseEntry("{\"text\":\"a\",\"color\":\"gold\",\"bold\":true,\"extra\":[\"b\",{\"text\":\"c\",\"color\":\"#112233\"}]}").get(0);
        assertEquals("a", span(l, 0).text());
        assertEquals(0xFFAA00, span(l, 0).rgb());
        assertEquals("b", span(l, 1).text());
        assertEquals(0xFFAA00, span(l, 1).rgb());
        assertTrue(span(l, 1).bold());
        assertEquals(0x112233, span(l, 2).rgb());
    }

    @Test
    void invalidJsonFallsBackToRawText() {
        Line l = RichText.parseEntry("{ not json at all").get(0);
        assertEquals("{ not json at all", span(l, 0).text());
        Line m = RichText.parseEntry("[1, 2").get(0);
        assertEquals("[1, 2", span(m, 0).text());
    }

    @Test
    void pageBreakSplitsPages() {
        List<Page> pages = RichText.parse(List.of("first", "{@pagebreak}", "second", "third"));
        assertEquals(2, pages.size());
        assertEquals(1, pages.get(0).lines().size());
        assertEquals(2, pages.get(1).lines().size());
        assertTrue(RichText.isPageBreak(" {@pagebreak} "));
        assertFalse(RichText.isPageBreak("{@pagebreak} extra"));
    }

    @Test
    void emptyAndNullDescriptionsGiveOneEmptyPage() {
        assertEquals(1, RichText.parse(List.of()).size());
        assertEquals(1, RichText.parse(null).size());
        assertTrue(RichText.parse(List.of()).get(0).lines().isEmpty());
    }

    @Test
    void trailingPageBreakGivesAnEmptyLastPage() {
        List<Page> pages = RichText.parse(List.of("a", "{@pagebreak}"));
        assertEquals(2, pages.size());
        assertTrue(pages.get(1).lines().isEmpty());
    }

    @Test
    void backslashAmpersandIsALiteralAmpersand() {
        String raw = "Extended " + (char) 92 + "& Advanced AE"; // the text as the pack stores it: backslash, ampersand
        assertEquals("Extended & Advanced AE", RichText.stripCodes(raw));
        assertEquals("Extended & Advanced AE", RichText.toSectionCodes(raw));
        assertEquals("Extended & Advanced AE", span(RichText.parseEntry(raw).get(0), 0).text());
        assertEquals("&6 not a code", RichText.stripCodes((char) 92 + "&6 not a code"));
    }

    @Test
    void stripAndSectionConversion() {
        assertEquals("Hello world & co", RichText.stripCodes("&6Hello&r &lworld&r && co"));
        assertEquals("§6Hello§r &x", RichText.toSectionCodes("&6Hello&r &&x"));
        assertEquals("plain", RichText.clearFormatting("&l&bplain&r"));
        assertEquals("", RichText.stripCodes(null));
    }
}
