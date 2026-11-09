package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetLogicTest {
    @Test fun server_mit_und_ohne_namen() {
        val s = PresetLogic.parseServers("Haupt|http://a.tld:8080/ ; b.tld:80\n\n")
        assertEquals(listOf(PresetServer("Haupt", "http://a.tld:8080"), PresetServer("Server 2", "http://b.tld:80")), s)
    }

    @Test fun leer_und_kaputt() {
        assertTrue(PresetLogic.parseServers("").isEmpty())
        assertTrue(PresetLogic.parseServers(" ; |  ;Name|").isEmpty())
        assertNull(PresetLogic.normalize("a b"))
    }

    @Test fun epg_adressen() {
        assertEquals(
            listOf("https://e1/x.xml.gz", "http://e2/y.xml"),
            PresetLogic.parseUrls("https://e1/x.xml.gz;ftp://nein\nhttp://e2/y.xml;https://e1/x.xml.gz")
        )
    }

    @Test fun server_wiedererkennen() {
        val s = PresetLogic.parseServers("A|http://a.tld:8080;B|https://b.tld")
        assertEquals(0, PresetLogic.indexOf("A.tld:8080/", s))
        assertEquals(1, PresetLogic.indexOf("https://b.tld", s))
        assertEquals(-1, PresetLogic.indexOf("http://c.tld", s))
    }

    @Test fun handy_ohne_server_nur_mit_festem_server() {
        val f = mapOf("user" to "u", "pass" to "p")
        assertNull(PhoneImportLogic.toInput(f))
        assertTrue(PhoneImportLogic.toInput(f, noHostOk = true)!!.isXtream)
    }

    @Test fun panel_liefert_mitteilung_diagnose_und_sicherung() {
        val c = PresetLogic.parseConfig("""{"servers":[{"name":"T","url":"http://h:8080"}],
            "news":{"title":"Hallo","text":"Neu","qr":"https://q"},"diag":"https://p/app/diag.php?key=k","backup":"ftp://x"}""")!!
        assertEquals("Hallo", HomeInfoLogic.parseNews(c.news!!)!!.title)
        assertEquals("https://p/app/diag.php?key=k", c.diag)
        assertNull(c.backup)
        assertNull(PresetLogic.parseConfig("""{"servers":[]}""")!!.news)
    }
}
