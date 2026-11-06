package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteConfigTest {
    @Test fun config_lesen() {
        val c = PresetLogic.parseConfig(
            """{"servers":[{"name":"XY TV","url":"neu.example:8080/"},{"name":"","url":"http://b.example"},{"name":"kaputt","url":"a b"}],
               "epg":["https://e/x.xml.gz","ftp://nein"]}"""
        )!!
        assertEquals(listOf(PresetServer("XY TV", "http://neu.example:8080"), PresetServer("Server 2", "http://b.example")), c.servers)
        assertEquals(listOf("https://e/x.xml.gz"), c.epg)
        assertNull(PresetLogic.parseConfig("kein json"))
    }

    @Test fun playlist_zieht_mit_um() {
        val old = listOf(PresetServer("XY TV", "http://alt.example:8080"))
        val new = listOf(PresetServer("XY TV", "http://neu.example:8080"))
        val url = "http://alt.example:8080/get.php?username=u&password=p&type=m3u_plus&output=ts"
        val want = "http://neu.example:8080/get.php?username=u&password=p&type=m3u_plus&output=ts"
        // über den Namen
        assertEquals(want, PresetLogic.retarget(url, "XY TV", emptyList(), new))
        // über die alte Adresse, auch wenn die Playlist anders heißt
        assertEquals(want, PresetLogic.retarget(url, "Playlist 1", old, new))
        // schon richtig oder fremder Server: nichts tun
        assertNull(PresetLogic.retarget(want, "XY TV", old, new))
        assertNull(PresetLogic.retarget("http://fremd/get.php?username=u&password=p", "Meins", old, new))
        assertNull(PresetLogic.retarget("http://x/list.m3u", "XY TV", old, new))
    }
}
