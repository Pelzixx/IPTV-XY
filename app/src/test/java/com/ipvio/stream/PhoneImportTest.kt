package com.ipvio.stream

import org.junit.Assert.*
import org.junit.Test
import java.net.Socket

class PhoneImportTest {
    @Test fun formWirdZerlegtUndDekodiert() {
        val f = PhoneImportLogic.parseForm("code=1234&url=http%3A%2F%2Fa.tld%2Fx.m3u%3Fu%3D1%26p%3D2")
        assertEquals("1234", f["code"])
        assertEquals("http://a.tld/x.m3u?u=1&p=2", f["url"])
    }

    @Test fun eingabePruefung() {
        assertNotNull(PhoneImportLogic.toInput(mapOf("url" to " http://a/b.m3u ")))
        assertNull(PhoneImportLogic.toInput(mapOf("url" to "ftp://a")))
        assertTrue(PhoneImportLogic.toInput(mapOf("host" to "h", "user" to "u", "pass" to "p"))!!.isXtream)
        assertNull(PhoneImportLogic.toInput(mapOf("host" to "h", "user" to "u")))
        assertNull(PhoneImportLogic.toInput(emptyMap()))
        // Stalker-Portal: Portal + gültige MAC
        assertTrue(PhoneImportLogic.toInput(mapOf("portal" to "http://p.tld/c/", "mac" to "00:1A:79:12:34:56"))!!.url.startsWith("stalker://"))
        assertNull(PhoneImportLogic.toInput(mapOf("portal" to "http://p.tld/c/", "mac" to "falsch")))
    }

    @Test fun codeHatVierStellen() {
        repeat(50) { assertEquals(4, PhoneImportLogic.newCode(java.util.Random(it.toLong())).length) }
    }

    private fun post(port: Int, body: String): String {
        Socket("127.0.0.1", port).use { s ->
            val b = body.toByteArray()
            s.getOutputStream().write(("POST /send HTTP/1.1\r\nHost: x\r\nContent-Length: ${b.size}\r\n\r\n").toByteArray() + b)
            s.getOutputStream().flush()
            return s.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }

    @Test fun serverNimmtNurMitRichtigemCodeAn() {
        var got: PhoneInput? = null
        val pi = PhoneImport { got = it }
        assertTrue(pi.start())
        try {
            assertTrue(post(pi.port, "code=0000&url=http%3A%2F%2Fa%2Fb.m3u").startsWith("HTTP/1.1 403"))
            assertNull(got)
            assertTrue(post(pi.port, "code=${pi.code}&url=http%3A%2F%2Fa%2Fb.m3u").startsWith("HTTP/1.1 200"))
            assertEquals("http://a/b.m3u", got?.url)
        } finally { pi.stop() }
    }

    @Test fun sperreNachFuenfFehlversuchen() {
        val pi = PhoneImport { }
        assertTrue(pi.start())
        try {
            repeat(5) { post(pi.port, "code=x&url=http%3A%2F%2Fa") }
            assertTrue(post(pi.port, "code=${pi.code}&url=http%3A%2F%2Fa").startsWith("HTTP/1.1 429"))
        } finally { pi.stop() }
    }

    @Test fun handySeiteInDerSpracheDesHandys() {
        assertEquals(0, PhoneImportLogic.langIndex("de-AT,de;q=0.9,en;q=0.8"))
        assertEquals(2, PhoneImportLogic.langIndex("tr-TR,tr;q=0.9"))
        assertEquals(5, PhoneImportLogic.langIndex("sr-Latn-RS"))
        assertEquals(1, PhoneImportLogic.langIndex("ja-JP,nl;q=0.5"))
        assertEquals(1, PhoneImportLogic.langIndex(""))
        I18nCore.load(DictA, DictB, DictC, DictD, DictE, DictF, DictG, DictH, DictI, DictJ, DictK)
        assertTrue(PhoneImportLogic.page(2).contains("Televizyona gönder"))
        assertTrue(PhoneImportLogic.page(0).contains("An den Fernseher senden"))
        assertTrue(PhoneImportLogic.page(1).contains("Send to TV"))
        assertTrue(PhoneImportLogic.message(7, "ok").contains("Envoyé"))
    }

    @Test fun vorgegebener_server_nur_mit_benutzer_und_passwort() {
        val urls = listOf("http://a.tld:8080", "http://b.tld:80")
        val x = PhoneImportLogic.toInput(mapOf("srv" to "1", "user" to "u", "pass" to "p"), presetUrls = urls)!!
        assertTrue(x.isXtream)
        assertEquals("http://b.tld:80", x.host)
        assertNull(PhoneImportLogic.toInput(mapOf("srv" to "5", "user" to "u", "pass" to "p"), presetUrls = urls))
        assertNull(PhoneImportLogic.toInput(mapOf("srv" to "0", "user" to "u"), presetUrls = urls))
    }

    @Test fun handy_seite_zeigt_vorgegebene_server_ohne_adressen() {
        val p = PhoneImportLogic.page(0, listOf("XY TV", "XY <Backup>"), "IPTV-XY", "XY TV")
        assertTrue(p.contains("Anmelden · XY TV"))
        assertTrue(p.contains("<option value=\"1\">XY &lt;Backup&gt;</option>"))
        assertTrue(p.contains("Selbst eingeben"))
        assertTrue(!PhoneImportLogic.page(0).contains("name=\"srv\""))
    }
}
