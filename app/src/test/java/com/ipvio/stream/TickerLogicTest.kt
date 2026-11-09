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
