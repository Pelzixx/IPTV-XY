package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsLogicTest {
    @Test fun erkenntBundesligaPaarung() {
        val h = SportsLogic.parse("Bundesliga: FC Bayern München - Borussia Dortmund", "", "Sky Sport Bundesliga 1 HD")
        assertNotNull(h); h!!
        assertEquals("Bundesliga", h.league)
        assertEquals("FC Bayern München", h.home)
        assertEquals("Borussia Dortmund", h.away)
    }

    @Test fun erkenntVsUndLive() {
        val h = SportsLogic.parse("LIVE: Arsenal vs Chelsea", "Premier League, 12. Spieltag", "DAZN 1")!!
        assertEquals("Premier League", h.league)
        assertEquals("Arsenal", h.home); assertEquals("Chelsea", h.away)
    }

    @Test fun ignoriertSerienUndHighlights() {
        assertNull(SportsLogic.parse("Tatort - Mord im Dorf", "Krimi", "Das Erste HD"))
        assertNull(SportsLogic.parse("Bundesliga Highlights - Der Spieltag", "", "Sky Sport News"))
        assertNull(SportsLogic.parse("Europamagazin", "", "Das Erste"))
    }

    @Test fun formelEinsRennen() {
        val h = SportsLogic.parse("Formel 1: Großer Preis von Brasilien - Rennen", "", "Sky Sport F1 HD")!!
        assertEquals(SportsLogic.MOTOR, h.sport)
        assertEquals("Formel 1", h.league)
    }

    @Test fun teamsVergleichenUndSenderRang() {
        assertTrue(SportsLogic.sameTeam("FC Bayern München", "Bayern"))
        assertTrue(SportsLogic.sameTeam("Borussia Mönchengladbach", "Mönchengladbach"))
        assertTrue(SportsLogic.channelRank("Sky Sport FHD") > SportsLogic.channelRank("Sky Sport HD"))
        assertEquals("FB", SportsLogic.initials("FC Bayern"))
    }

    @Test fun deutscherSportUndKonferenz() {
        // Sky-Format mit Vorspann bis zum letzten Doppelpunkt
        val h = SportsLogic.parse("Fußball: 2. Bundesliga, 11. Spieltag: 1. FC Heidenheim 1846 - Hannover 96", "", "Sky Sport Bundesliga 3 HD")!!
        assertEquals("2. Bundesliga", h.league)
        assertEquals("1. FC Heidenheim 1846", h.home); assertEquals("Hannover 96", h.away)
        val k = SportsLogic.parse("2. Bundesliga: Die Konferenz", "", "Sky Sport Bundesliga 1")!!
        assertEquals("2. Bundesliga – Konferenz", k.title)
        // andere Sportarten mit „Bundesliga“ / „Länderspiel“ sind kein Fußball
        assertEquals("Andere", SportsLogic.parse("Tischtennis: Bundesliga", "", "ORF Sport+")?.league ?: "Andere")
        assertEquals("Eishockey", SportsLogic.league("LIVE Eishockey Länderspiel")?.second)
        assertTrue(SportsLogic.allowed("Bundesliga")); assertTrue(!SportsLogic.allowed("NBA"))
        assertTrue(SportsLogic.prio("Bundesliga") < SportsLogic.prio("Champions League"))
        assertTrue(SportsLogic.mentions("Live: Heidenheim gegen Hannover", "1. FC Heidenheim 1846"))
        // echte Sky-Titel aus dem Anbieter-Programm
        val s = SportsLogic.parse("Live 2. BL: 1. FC Magdeburg - FC St. Pauli, 11. Spieltag", "", "Sky Bundesliga 2 ᶠʰᵈ")!!
        assertEquals("2. Bundesliga", s.league); assertEquals("1. FC Magdeburg", s.home); assertEquals("FC St. Pauli", s.away)
        assertEquals("Bundesliga", SportsLogic.parse("BL: 1. FC Köln - Bayer Leverkusen, 9. Spieltag", "", "Sky Bundesliga 2")!!.league)
        assertEquals("2. Bundesliga – Konferenz", SportsLogic.parse("Live 2. BL: Sonntags-Konferenz, 11. Spieltag", "", "Sky Konferenz")!!.title)
        assertNull(SportsLogic.parse("Bundesliga der Frauen - 2026/27", "", "DAZN 2"))
        assertNull(SportsLogic.parse("Live 2. BL Alle Spiele, alle Tore: Die Vodafone Highlight-Show, 11. Spieltag", "", "Sky Konferenz"))
        assertTrue(SportsLogic.foreign("UK: Sky Sports Main Event")); assertTrue(!SportsLogic.foreign("Sky Bundesliga 2 ᶠʰᵈ"))
        assertEquals("sky sport bundesliga 1", SportsLogic.baseName("DE: Sky Sport Bundesliga 1 FHD"))
        assertEquals("sky sport bundesliga 1", SportsLogic.baseName("Sky Sport Bundesliga 1 HD (1080p)"))
    }

    @Test fun sendungen_sind_keine_spiele() {
        val h = SportsLogic.parse("Glanzparade - Die Show mit Buschmann", "", "Sky Sport Bundesliga 1 HD")
        assertTrue(h == null || h.home == null)
        val real = SportsLogic.parse("Bundesliga: Hertha BSC - VfL Wolfsburg", "", "Sky Sport Bundesliga 2 HD")!!
        assertEquals("Hertha BSC", real.home)
    }

    @Test fun rallye_auf_f1_sender_ist_kein_f1() {
        val h = SportsLogic.parse("WRC: Rally Italia Sardegna", "SS17 Sassari - Argentiera 2", "Sky Sport F1 HD")
        assertTrue(h == null || (h.league != "Formel 1" && h.home == null))
        val r = SportsLogic.parse("WRC Live: SS17 Sassari - Argentiera 2", "", "Sky Sport F1 HD")
        assertTrue(r == null || r.home == null)
    }

    @Test fun nacht_wiederholung() {
        val z = java.util.TimeZone.getTimeZone("Europe/Berlin")
        // 10.11.2026 00:30 MEZ = 09.11. 23:30 UTC
        assertTrue(SportsLogic.nightReplay("Bundesliga", 1794267000L, z))
        // Samstag 15:30 MEZ
        assertTrue(!SportsLogic.nightReplay("Bundesliga", 1794583800L, z))
        assertTrue(!SportsLogic.nightReplay("Champions League", 1794267000L, z))
    }

    @Test fun alle_spiele_ist_kein_spiel() {
        val h = SportsLogic.parse("Alle Spiele, Brack Super League", "Brack Super League, 13. Spieltag", "Blue Sport D1")
        assertTrue(h == null || h.home == null)
    }
}

class DartsTest {
    @Test fun darts_live() {
        val h = SportsLogic.parse("Darts Live - Interwetten PDC Europe Super League", "", "SPORT1 ʰᵈ")
        org.junit.Assert.assertNotNull(h)
        org.junit.Assert.assertEquals("Darts", h!!.league)
        org.junit.Assert.assertTrue(SportsLogic.allowed("Darts"))
        org.junit.Assert.assertNotNull(SportsLogic.parse("Darts Live - Grand Slam of Darts", "", "DAZN 2"))
        org.junit.Assert.assertNotNull(SportsLogic.parse("Darts - Grand Slam Of Darts", "", "SPORT1 ʰᵈ"))
    }
}
