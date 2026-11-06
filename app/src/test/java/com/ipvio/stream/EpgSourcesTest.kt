package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgSourcesTest {
    private val list = listOf("http://a/1.xml", "http://a/2.xml.gz", "http://srv:80/xmltv.php?username=u&password=p")

    @Test fun mit_fester_quelle_ohne_playlist_kopf() {
        assertEquals(
            listOf("https://epg/de.xml.gz", "http://srv:80/xmltv.php?username=u&password=p"),
            EpgSources.pick(list, listOf("https://epg/de.xml.gz"), emptyList())
        )
    }

    @Test fun ohne_eigene_quellen_alles_aus_der_playlist() {
        assertEquals(
            listOf("http://srv:80/xmltv.php?username=u&password=p", "http://a/1.xml", "http://a/2.xml.gz"),
            EpgSources.pick(list, emptyList(), emptyList())
        )
    }

    @Test fun herkunft_und_name() {
        val t = EpgSources.pickTagged(list, listOf("https://fest/de.xml.gz"), listOf("https://radio/epg.xml"), emptyList(), "XY Radio")
        assertEquals(listOf(EpgSrc.SERVER, EpgSrc.FIXED, EpgSrc.PROVIDER), t.map { it.kind })
        assertEquals(listOf("XY Radio", null, "XY Radio"), t.map { it.forName })
        assertEquals("Anbieter-EPG", EpgSources.label(t[2]))
        assertEquals("fest", EpgSources.label(t[1]))
    }

    @Test fun eigene_epg_der_playlist_ersetzt_anbieter() {
        val t = EpgSources.pickTagged(list, emptyList(), emptyList(), emptyList(), "XY TV", "https://meins/epg.xml.gz")
        assertEquals(listOf("https://meins/epg.xml.gz"), t.map { it.url })
        assertEquals(EpgSrc.PL_EPG, t[0].kind)
    }
}
