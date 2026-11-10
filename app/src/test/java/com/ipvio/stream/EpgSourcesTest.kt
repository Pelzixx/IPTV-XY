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
        assertEquals("EPG XY Radio", EpgSources.label(t[2]))
        assertEquals("Anbieter-EPG", EpgSources.label(t[2].copy(forName = null)))
        assertEquals("fest", EpgSources.label(t[1], brand = ""))
        assertEquals("XY EPG Fest", EpgSources.label(t[1], brand = "XY"))
    }

    @Test fun eigene_epg_quellen_der_playlist_zuerst_dann_anbieter() {
        val t = EpgSources.pickTagged(list, emptyList(), emptyList(), emptyList(), "XY TV", "https://meins/epg.xml.gz\nhttps://zwei/epg.xml")
        assertEquals(
            listOf("https://meins/epg.xml.gz", "https://zwei/epg.xml", "http://srv:80/xmltv.php?username=u&password=p"),
            t.map { it.url }
        )
        assertEquals(listOf(EpgSrc.PL_EPG, EpgSrc.PL_EPG, EpgSrc.PROVIDER), t.map { it.kind })
    }

    @Test fun alte_einzelne_epg_adresse_bleibt_gueltig() {
        val t = EpgSources.pickTagged(list, emptyList(), emptyList(), emptyList(), "XY TV", "https://meins/epg.xml.gz")
        assertEquals("https://meins/epg.xml.gz", t[0].url)
        assertEquals(EpgSrc.PL_EPG, t[0].kind)
    }
}

class EpgOwnerTest {
    private val lists = listOf("XY TV" to "http://srv:80/get.php?username=a&password=p&type=m3u_plus", "XY Backup" to "http://bak:8080/get.php?username=b&password=q")
    @Test fun zugang_gefunden() = assertEquals("XY Backup", EpgSources.ownerOf("http://bak:8080/xmltv.php?username=b&password=q", lists))
    @Test fun anderer_benutzer_nicht() = assertEquals(null, EpgSources.ownerOf("http://srv:80/xmltv.php?username=x&password=p", lists))
}
