package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Test

/** Live-Sender in Kategorien mit „Kino“, „Cinema“, „VOD“ im Namen bleiben Live-Sender. */
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
        // Ohne Live-Merkmale in der Adresse zählt der Kategoriename weiter
        assertEquals(Kind.MOVIE, kind("Nosferatu"))
    }
}
