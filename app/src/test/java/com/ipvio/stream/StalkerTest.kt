package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StalkerTest {
    @Test fun listenadresse_hin_und_zurueck() {
        val u = Stalker.listUrl("http://portal.tld:8080/c/", "00:1a:79:aa:bb:cc")
        assertTrue(Stalker.isList(u))
        val r = Stalker.parse(u)
        assertNotNull(r)
        assertEquals("http://portal.tld:8080/c", r!!.portal)
        assertEquals("00:1A:79:AA:BB:CC", r.mac)
        assertNull(r.cmd)
    }

    @Test fun schnittstelle_aus_portaladresse() {
        assertEquals("http://p.tld/portal.php", Stalker.api("http://p.tld/c/"))
        assertEquals("http://p.tld/portal.php", Stalker.api("http://p.tld"))
        assertEquals("http://p.tld/stalker_portal/server/load.php", Stalker.api("http://p.tld/stalker_portal/c/"))
        assertEquals("http://p.tld/server/load.php", Stalker.api("http://p.tld/server/load.php"))
    }

    @Test fun andere_adressen_bleiben() {
        assertEquals("http://x.tv/a.m3u8", Stalker.playable("http://x.tv/a.m3u8"))
        assertTrue(PlaylistLogic.normalizeUrl(Stalker.listUrl("http://p.tld/c/", "00:1A:79:00:00:01")) != null)
    }
}
