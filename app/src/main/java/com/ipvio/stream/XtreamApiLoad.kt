package com.ipvio.stream

import android.util.JsonReader
import android.util.JsonToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStreamReader
import java.io.Writer
import java.net.URLEncoder

/**
 * Schnelles Laden bei Xtream-Zugängen (wie TiviMate/OpenTV): statt der kompletten M3U-Liste – in der jede Serienfolge eine
 * eigene Zeile ist – werden über player_api.php nur Sender, Filme und Serien geholt. Daraus entsteht eine kompakte M3U-Datei
 * im gewohnten Format, sodass Einlesen, Sprachfilter, Zwischenspeicher, Favoriten und „Weiterschauen“ unverändert bleiben.
 * Je Serie steht nur ein Platzhalter darin; die Folgen lädt [SeriesLazy] beim Öffnen der Serie.
 * Die Idee folgt OpenTV (https://github.com/opentvproject/opentv, GPL-3.0).
 */
object XtreamApiLoad {
    /** Gesamtzahl geschriebener Einträge; [onPhase] meldet „Sender/Filme/Serien werden geladen …“. */
    fun writeM3u(
        client: OkHttpClient, host: String, user: String, pass: String, out: Writer,
        onPhase: (String) -> Unit, onCount: (Int) -> Unit, cancelled: () -> Boolean,
        liveExt: String = "ts", withLive: Boolean = true, withVod: Boolean = true,
        meta: VodMeta.Collector? = null
    ): Int {
        val api = "$host/player_api.php?username=${URLEncoder.encode(user, "UTF-8")}&password=${URLEncoder.encode(pass, "UTF-8")}&action="
        val w = XtreamM3uLogic.Writer(out, host, user, pass)
        w.liveExt = liveExt
        w.meta = meta
        w.header()
        // TiviMate (Xtream Codes Parameter): „TV-Kanäle einbeziehen“ / „VOD einbeziehen“
        if (withLive) {
            onPhase("Sender werden geladen …")
            val liveCats = categories(client, api + "get_live_categories")
            stream(client, api + "get_live_streams") { o -> w.live(o, liveCats); onCount(w.count); if (cancelled()) throw java.util.concurrent.CancellationException() }
        }
        if (!withVod) { out.flush(); return w.count }
        onPhase("Filme werden geladen …")
        val vodCats = categories(client, api + "get_vod_categories")
        stream(client, api + "get_vod_streams") { o -> w.movie(o, vodCats); onCount(w.count); if (cancelled()) throw java.util.concurrent.CancellationException() }
        onPhase("Serien werden geladen …")
        val serCats = categories(client, api + "get_series_categories")
        stream(client, api + "get_series") { o -> w.series(o, serCats); onCount(w.count); if (cancelled()) throw java.util.concurrent.CancellationException() }
        out.flush()
        return w.count
    }

    private fun categories(client: OkHttpClient, url: String): Map<String, String> {
        val m = HashMap<String, String>()
        runCatching { stream(client, url) { o -> val id = o["category_id"]; val n = o["category_name"]; if (id != null && n != null) m[id] = n } }
        return m
    }

    /** Liest ein JSON-Feld [ {…}, {…} ] Eintrag für Eintrag (große Listen passen so nicht als Ganzes in den Speicher). */
    private fun stream(client: OkHttpClient, url: String, each: (Map<String, String>) -> Unit) {
        client.newCall(Request.Builder().url(url).header("User-Agent", "IPTV-XY/1.0").build()).execute().use { r ->
            if (!r.isSuccessful) throw PlaylistError(PlaylistLogic.httpError(r.code))
            val body = r.body ?: throw PlaylistError("Der Server hat keine Daten geliefert.")
            JsonReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { jr ->
                jr.isLenient = true
                // Manche Server liefern {} statt [] bei leeren Listen
                if (jr.peek() != JsonToken.BEGIN_ARRAY) { jr.skipValue(); return }
                jr.beginArray()
                val o = HashMap<String, String>()
                while (jr.hasNext()) {
                    if (jr.peek() != JsonToken.BEGIN_OBJECT) { jr.skipValue(); continue }
                    o.clear()
                    jr.beginObject()
                    while (jr.hasNext()) {
                        val k = jr.nextName()
                        when (jr.peek()) {
                            JsonToken.STRING -> o[k] = jr.nextString()
                            JsonToken.NUMBER -> o[k] = jr.nextString()
                            JsonToken.BOOLEAN -> o[k] = if (jr.nextBoolean()) "1" else "0"
                            else -> jr.skipValue()
                        }
                    }
                    jr.endObject()
                    each(o)
                }
                jr.endArray()
            }
        }
    }
}

/** Schreibt die kompakte M3U-Datei (ohne Android, testbar). */
object XtreamM3uLogic {
    /** Kennzeichen im Platzhalter einer Serie: …/series/user/pass/xtser12345.lazy */
    const val LAZY_PREFIX = "xtser"
    const val LAZY_SUFFIX = ".lazy"

    fun lazySeriesUrl(host: String, user: String, pass: String, seriesId: String) = "$host/series/$user/$pass/$LAZY_PREFIX$seriesId$LAZY_SUFFIX"

    /** Serien-Nummer aus dem Platzhalter, sonst null. */
    fun lazySeriesId(url: String): String? {
        val i = url.lastIndexOf("/$LAZY_PREFIX")
        if (i < 0 || !url.endsWith(LAZY_SUFFIX)) return null
        return url.substring(i + 1 + LAZY_PREFIX.length, url.length - LAZY_SUFFIX.length).takeIf { it.isNotEmpty() }
    }

    /** Attributwert ohne Zeichen, die die #EXTINF-Zeile zerbrechen würden. */
    private fun a(s: String?): String = (s ?: "").replace('"', '\'').replace('\n', ' ').replace('\r', ' ').trim()
    private fun t(s: String?): String = (s ?: "").replace('\n', ' ').replace('\r', ' ').replace(',', ' ').trim()

    class Writer(private val out: java.io.Writer, private val host: String, private val user: String, private val pass: String) {
        /** Ausgabeformat der Sender (TiviMate: „Ausgabeformat“): ts oder m3u8. */
        var liveExt = "ts"
        /** Bewertung und Hinzufügedatum des Anbieters (Sortierung wie TiviMate). */
        var meta: VodMeta.Collector? = null
        var count = 0
            private set

        fun header() { out.write("#EXTM3U\n") }

        private fun entry(attrs: String, name: String, url: String) {
            out.write("#EXTINF:-1 "); out.write(attrs); out.write(","); out.write(name); out.write("\n"); out.write(url); out.write("\n")
            count++
        }

        fun live(o: Map<String, String>, cats: Map<String, String>) {
            val id = o["stream_id"] ?: return
            val name = t(o["name"]).ifEmpty { return }
            val sb = StringBuilder()
            sb.append("tvg-id=\"").append(a(o["epg_channel_id"])).append("\" tvg-name=\"").append(a(name))
                .append("\" tvg-logo=\"").append(a(o["stream_icon"])).append("\" group-title=\"").append(a(cats[o["category_id"]] ?: "Sonstige")).append('"')
            if (o["tv_archive"] == "1") {
                val d = o["tv_archive_duration"]?.toIntOrNull() ?: 0
                if (d > 0) sb.append(" catchup=\"xc\" catchup-days=\"").append(d).append('"')
            }
            entry(sb.toString(), name, "$host/live/$user/$pass/$id.$liveExt")
        }

        fun movie(o: Map<String, String>, cats: Map<String, String>) {
            val id = o["stream_id"] ?: return
            val name = t(o["name"]).ifEmpty { return }
            val ext = o["container_extension"]?.trim()?.trimStart('.')?.ifEmpty { null } ?: "mp4"
            val url = "$host/movie/$user/$pass/$id.$ext"
            meta?.add(url, ProviderLogic.ratingOf(o["rating"], o["rating_5based"]), o["added"]?.toLongOrNull() ?: 0L)
            entry("tvg-logo=\"${a(o["stream_icon"])}\" group-title=\"${a(cats[o["category_id"]] ?: "Filme")}\"", name, url)
        }

        fun series(o: Map<String, String>, cats: Map<String, String>) {
            val id = o["series_id"] ?: return
            val name = t(o["name"]).ifEmpty { return }
            val url = lazySeriesUrl(host, user, pass, id)
            meta?.add(url, ProviderLogic.ratingOf(o["rating"], o["rating_5based"]), o["last_modified"]?.toLongOrNull() ?: 0L)
            entry("tvg-logo=\"${a(o["cover"])}\" group-title=\"${a(cats[o["category_id"]] ?: "Serien")}\"", name, url)
        }
    }
}
