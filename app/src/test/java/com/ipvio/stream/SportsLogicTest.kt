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
        assertEquals("sky sport bundesliga 1", SportsLogic.baseName("DE: Sky Sport Bundesliga 1 FHD"))
        assertEquals("sky sport bundesliga 1", SportsLogic.baseName("Sky Sport Bundesliga 1 HD (1080p)"))
    }
}
