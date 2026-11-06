package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelEditLogicTest {
    @Test fun namen_hin_und_zurueck() {
        val m = mapOf("a" to "Das Erste", "b" to "ZDF „HD“")
        assertEquals(m, ChannelEditLogic.decodeNames(ChannelEditLogic.encodeNames(m)))
    }

    @Test fun kaputte_daten_ergeben_leer() {
        assertEquals(emptyMap<String, String>(), ChannelEditLogic.decodeNames("{kaputt"))
        assertEquals(emptyMap<String, String>(), ChannelEditLogic.decodeNames(null))
    }

    @Test fun ids_hin_und_zurueck() {
        val s = setOf("1", "live:2")
        assertEquals(s, ChannelEditLogic.decodeIds(ChannelEditLogic.encodeIds(s)))
        assertEquals(emptySet<String>(), ChannelEditLogic.decodeIds(""))
    }

    @Test fun name_bereinigen() {
        assertNull(ChannelEditLogic.cleanName("   "))
        assertEquals("Sky Sport", ChannelEditLogic.cleanName("  Sky\nSport "))
        assertEquals(ChannelEditLogic.MAX_NAME, ChannelEditLogic.cleanName("x".repeat(100))!!.length)
    }

    @Test fun ausgeblendete_filtern() {
        val l = listOf("a", "b", "c")
        val hidden = setOf("b")
        assertEquals(listOf("a", "c"), ChannelEditLogic.visible(l, { it }, { it in hidden }, false))
        assertEquals(listOf("b"), ChannelEditLogic.visible(l, { it }, { it in hidden }, true))
    }
}
