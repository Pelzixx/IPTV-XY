package app.opentv

import app.opentv.xy.SportGuideLogic
import app.opentv.xy.SportKind
import app.opentv.xy.SportLogic
import app.opentv.xy.SportScores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SportLogicTest {
    @Test fun classifiesLeagues() {
        assertEquals(SportKind.FOOTBALL to "Bundesliga", SportLogic.classify("Bundesliga: FC Bayern - Borussia Dortmund", null))
        assertEquals(SportKind.MOTORSPORT to "Formel 1", SportLogic.classify("Formel 1: Großer Preis von Brasilien", "Sport"))
        assertNull(SportLogic.classify("Sportschau - Bundesliga Highlights", "Sport"))
        assertNull(SportLogic.classify("Tagesschau", "Nachrichten"))
    }

    @Test fun teamsFromTitle() {
        assertEquals("FC Bayern" to "Borussia Dortmund", SportLogic.teams("Bundesliga: FC Bayern - Borussia Dortmund"))
        assertEquals("Lakers" to "Celtics", SportLogic.teams("Lakers vs. Celtics"))
    }

    @Test fun groupsSameShowOnSeveralChannels() {
        val t = 1_800_000_000_000L
        val shows = listOf(
            SportGuideLogic.Show("sky1.de", "Bundesliga: FC Bayern - BVB", "Sport", null, t, t + 7_200_000),
            SportGuideLogic.Show("skytop.de", "Bundesliga: FC Bayern - BVB", "Sport", null, t + 60_000, t + 7_200_000),
            SportGuideLogic.Show("ard.de", "Tagesschau", "News", null, t, t + 900_000),
        )
        val ev = SportGuideLogic.fromShows(shows, t - 1)
        assertEquals(1, ev.size)
        assertEquals(listOf("sky1.de", "skytop.de"), ev[0].epgIds)
        assertEquals("FC Bayern", ev[0].home)
    }

    @Test fun channelNameMatching() {
        assertTrue(SportLogic.nameMatches("Sky Sport Bundesliga 1", "DE| Sky Sport Bundesliga 1 HD"))
        assertTrue(SportLogic.nameMatches("DAZN 1", "DE: DAZN 1 FHD"))
        assertFalse(SportLogic.nameMatches("DAZN 1", "DE: DAZN 2"))
    }

    @Test fun teamsMatchAcrossSpellings() {
        assertTrue(SportScores.teamMatches("FC Bayern", "Bayern Munich"))
        assertTrue(SportScores.teamMatches("BVB", "Borussia Dortmund"))
        assertTrue(SportScores.teamMatches("Lakers", "Los Angeles Lakers"))
        assertFalse(SportScores.teamMatches("FC Bayern", "Borussia Dortmund"))
    }

    @Test fun parsesEspnScoreboard() {
        val body = """{"events":[{"date":"2026-11-08T17:30Z","name":"x","competitions":[{
            "competitors":[{"homeAway":"home","score":"2","team":{"displayName":"Bayern Munich"}},
                           {"homeAway":"away","score":"1","team":{"displayName":"Borussia Dortmund"}}],
            "status":{"type":{"state":"in","shortDetail":"67'"}}}]}]}"""
        val s = SportScores.parseEspn(body)
        assertEquals(1, s.size)
        assertEquals("2", s[0].homeScore); assertEquals("in", s[0].state); assertEquals("67'", s[0].detail)
        assertNotNull(s[0].startMillis)
        val ev = app.opentv.xy.SportEvent(SportKind.FOOTBALL, "Bundesliga", "FC Bayern - BVB", s[0].startMillis!!, s[0].startMillis!! + 7_200_000, home = "FC Bayern", away = "BVB")
        assertNotNull(SportScores.find(ev, s))
    }

    @Test fun parsesCustomList() {
        val body = """{"events":[{"sport":"football","league":"Bundesliga","title":"FC Bayern – BVB","home":"FC Bayern","away":"BVB",
            "start":"2026-11-08T18:30:00+01:00","channels":["Sky Sport Bundesliga 1","DAZN 1"]}]}"""
        val e = SportLogic.parseCustom(body).single()
        assertTrue(e.featured); assertEquals(2, e.channelNames.size); assertEquals(e.startMillis + 7_200_000, e.endMillis)
    }
}
