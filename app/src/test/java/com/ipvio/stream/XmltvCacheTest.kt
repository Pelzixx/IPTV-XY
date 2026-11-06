package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class XmltvCacheTest {
    private val now = 1_800_000_000L
    private val progs = mapOf(
        "ard.de" to listOf(EpgEntry("Tagesschau", "Nachrichten", now - 600, now + 300), EpgEntry("Film", "", now + 300, now + 6000)),
        "alt.de" to listOf(EpgEntry("Uralt", "", now - 30 * 86400L, now - 29 * 86400L))
    )

    private fun bytes(key: String) = ByteArrayOutputStream().also { XmltvCache.write(it, key, now - 100, progs) }.toByteArray()

    @Test fun speichern_und_lesen() {
        val key = XmltvCache.key(listOf("http://a/epg.xml.gz"))
        val (savedAt, map) = XmltvCache.read(ByteArrayInputStream(bytes(key)), key, now)!!
        assertEquals(now - 100, savedAt)
        assertEquals(progs["ard.de"], map["ard.de"])
        // Sendungen, die länger als 7 Tage vorbei sind, fallen weg
        assertNull(map["alt.de"])
    }

    @Test fun andere_quellen_passen_nicht() {
        val b = bytes(XmltvCache.key(listOf("http://a")))
        assertNull(XmltvCache.read(ByteArrayInputStream(b), XmltvCache.key(listOf("http://b")), now))
    }
}
