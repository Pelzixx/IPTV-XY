package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class XtreamApiLoadTest {
    private fun m3u(): String {
        val sw = StringWriter()
        val w = XtreamM3uLogic.Writer(sw, "http://h:80", "u", "p")
        w.header()
        w.live(mapOf("stream_id" to "7", "name" to "Das Erste HD", "epg_channel_id" to "ard.de", "stream_icon" to "http://l/a.png",
            "category_id" to "1", "tv_archive" to "1", "tv_archive_duration" to "3"), mapOf("1" to "DE | News"))
        w.movie(mapOf("stream_id" to "8", "name" to "Matrix (1999)", "container_extension" to "mkv", "category_id" to "2"), mapOf("2" to "Filme"))
        w.series(mapOf("series_id" to "9", "name" to "Dark", "cover" to "http://l/d.jpg", "category_id" to "3"), mapOf("3" to "Serien"))
        return sw.toString()
    }

    @Test fun kompakte_liste_wird_richtig_eingelesen() {
        val p = M3uParser.parseStream(java.io.StringReader(m3u()))
        val live = p.items.first { it.kind == Kind.LIVE }
        assertEquals("http://h:80/live/u/p/7.ts", live.url)
        assertEquals("ard.de", live.tvgId)
        assertEquals("DE | News", live.group)
        assertTrue(p.catchup.containsKey(live.id))
        assertEquals("http://h:80/movie/u/p/8.mkv", p.items.first { it.kind == Kind.MOVIE }.url)
        val s = p.items.first { it.kind == Kind.SERIES }
        assertEquals("Dark", s.title)
        assertTrue(SeriesLazy.isLazy(s))
        assertEquals("9", XtreamM3uLogic.lazySeriesId(s.episodes[0].url))
    }

    @Test fun platzhalter_erkennen() {
        assertEquals("123", XtreamM3uLogic.lazySeriesId("http://h/series/u/p/xtser123.lazy"))
        assertNull(XtreamM3uLogic.lazySeriesId("http://h/series/u/p/123.mkv"))
    }

    @Test fun folgen_aus_series_info() {
        val body = """{"episodes":{"1":[{"id":"101","episode_num":"1","title":"Dark - S01E01","container_extension":"mkv","season":"1"},
            {"id":"102","episode_num":2,"title":"","container_extension":"mp4","season":1}],
            "2":[{"id":"201","episode_num":"1","title":"Zwei","container_extension":"mkv"}]}}"""
        val e = SeriesLazyLogic.episodes(body, "http://h", "u", "p")
        assertEquals(3, e.size)
        assertEquals("http://h/series/u/p/101.mkv", e[0].url)
        assertEquals(2, e[2].season)
        assertEquals("Episode 2", e[1].title)
        assertTrue(SeriesLazyLogic.episodes("kaputt", "h", "u", "p").isEmpty())
    }
}
