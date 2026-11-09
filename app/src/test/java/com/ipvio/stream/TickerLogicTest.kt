package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TickerLogicTest {
    private val body = """{"ok":true,"items":[
        {"id":"a","text":"Neue Sender  im\nPaket","level":"info","ts":10},
        {"id":"b","text":"Server XY TV gestört","level":"error","ts":20},
        {"id":"c","text":"   ","ts":30}]}"""

    @Test fun lesen() {
        val l = TickerLogic.parse(body)
        assertEquals(2, l.size)
        assertEquals("Neue Sender im Paket", l[0].text)
        assertTrue(l[1].error)
    }

    @Test fun zeile_neueste_zuerst() =
        assertEquals("Server XY TV gestört   +++   Neue Sender im Paket", TickerLogic.line(TickerLogic.parse(body)))

    @Test fun kaputt_ist_leer() = assertEquals(0, TickerLogic.parse("nix").size)
}

class LiveBroadcastTest {
    @Test fun live_markiert() {
        assertTrue(SportsLogic.liveBroadcast("NHL: Live - Rangers vs. Bruins", ""))
        assertTrue(SportsLogic.liveBroadcast("Tennis", "ATP Finals, Turin. (L)"))
        assertTrue(!SportsLogic.liveBroadcast("Re-Live: Bayern - Dortmund", ""))
        assertTrue(!SportsLogic.liveBroadcast("Poker Night", "Die besten Hände"))
        assertTrue(!SportsLogic.liveBroadcast("Highlights Live", ""))
    }
}

class TableParseTest {
    @Test fun tabelle() {
        val t = SportsLogic.parseTable("""[{"teamName":"FC Bayern München","points":21,"opponentGoals":9,"goals":29,"matches":9,"won":6,"lost":0,"draw":3,"goalDiff":20},{"teamName":"","points":1}]""")
        assertEquals(1, t.size)
        assertEquals(TableRow(1, "FC Bayern München", 9, 6, 3, 0, 29, 9, 20, 21), t[0])
    }
}

class LogoUrlTest {
    @Test fun svg_wird_png() = assertEquals(
        "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1f/Logo_FC_Bayern.svg/120px-Logo_FC_Bayern.svg.png",
        SportsLogic.logoUrl("https://upload.wikimedia.org/wikipedia/commons/1/1f/Logo_FC_Bayern.svg"))
    @Test fun png_bleibt() = assertEquals("https://x.de/a.png", SportsLogic.logoUrl("https://x.de/a.png"))
    @Test fun fremdes_svg_nicht() = assertEquals(null, SportsLogic.logoUrl("https://x.de/a.svg"))
}
