package app.opentv

import app.opentv.ui.info.InfoHome
import app.opentv.xy.XyPresetLogic
import app.opentv.xy.XyServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XyPresetLogicTest {
    @Test fun server_aus_secret() {
        assertEquals(
            listOf(XyServer("XY TV", "http://xy-tv.xyz:8080"), XyServer("Server 2", "http://b.example")),
            XyPresetLogic.parseServers("XY TV|http://xy-tv.xyz:8080/;b.example"),
        )
    }

    @Test fun server_aus_config() {
        val l = XyPresetLogic.parseConfig("""{"servers":[{"name":"XY Radio","url":"http://r.example","epg":["https://e/r.xml.gz"]}]}""")!!
        assertEquals(XyServer("XY Radio", "http://r.example", "https://e/r.xml.gz"), l[0])
        assertNull(XyPresetLogic.parseConfig("kaputt"))
        assertTrue(XyPresetLogic.sameHost("r.example/", "HTTP://r.example"))
    }

    @Test fun mitteilung_und_laufzeit() {
        val n = InfoHome.parseNews("""{"title":"Hallo","text":"","qr":"https://x"}""")!!
        assertEquals("Hallo", n.title)
        assertNull(InfoHome.parseNews("""{"title":""}"""))
        assertEquals(1L, InfoHome.daysLeft(2_000_000L + 3_600_000L, 2_000_000L))
        assertEquals(-1L, InfoHome.daysLeft(1_000L, 2_000L))
        assertNull(InfoHome.daysLeft(null, 1L))
    }
}
