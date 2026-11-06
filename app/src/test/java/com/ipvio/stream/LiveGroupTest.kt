package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Test

/** Film nur bei Videodatei-Endung; Kategorienamen mit „Kino“, „Cinema“, „VOD“ machen keinen Film. */
class LiveGroupTest {
    private val m3u = """#EXTM3U
#EXTINF:-1 group-title="DE | Sky Cinema",Sky Cinema Action HD
http://h/u/p/101
#EXTINF:-1 group-title="YouTube VOD",Lofi Radio
http://h/live/u/p/102.ts
#EXTINF:-1 group-title="Kino",Kinowelt TV
http://h/x/stream.m3u8
#EXTINF:-1 group-title="Filme",Matrix (1999)
http://h/movie/u/p/2.mp4
#EXTINF:-1 group-title="Live",Dune (2021)
http://h/a/b/dune.MKV?token=1
#EXTINF:-1 group-title="VOD Klassiker",Nosferatu
http://h/files/nosferatu
"""

    @Test fun kinosender_bleiben_live() {
        val items = M3uParser.parse(m3u)
        fun kind(t: String) = items.first { it.title.startsWith(t) }.kind
        assertEquals(Kind.LIVE, kind("Sky Cinema"))
        assertEquals(Kind.LIVE, kind("Lofi"))
        assertEquals(Kind.LIVE, kind("Kinowelt"))
        assertEquals(Kind.MOVIE, kind("Matrix"))
        assertEquals(Kind.MOVIE, kind("Dune"))
        // Ohne Videodatei-Endung kein Film, auch in einer VOD-Kategorie
        assertEquals(Kind.LIVE, kind("Nosferatu"))
    }
}
