package com.ipvio.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Folgen einer Serie erst beim Öffnen laden (Xtream get_series_info), wie TiviMate. Beim schnellen Laden über die
 * Schnittstelle ([XtreamApiLoad]) steht je Serie nur ein Platzhalter in der Liste.
 */
object SeriesLazy {
    private val cache = LruCache<String, Item>(40)

    /** Serie mit Platzhalter statt Folgen? */
    fun isLazy(item: Item): Boolean = item.kind == Kind.SERIES && item.episodes.size == 1 &&
        XtreamM3uLogic.lazySeriesId(item.episodes[0].url) != null

    /** Schon geladene vollständige Serie (z. B. für den Player), sonst null. */
    fun cached(id: String): Item? = cache[id]

    /** Vollständige Serie mit allen Folgen; bei Fehler die übergebene (dann bleibt der Platzhalter). */
    suspend fun full(item: Item): Item {
        if (!isLazy(item)) return item
        cache[item.id]?.let { return it }
        val ph = item.episodes[0].url
        val sid = XtreamM3uLogic.lazySeriesId(ph) ?: return item
        // …/series/user/pass/xtser123.lazy → Server, Benutzer, Passwort
        val m = Regex("""^(https?://[^/]+)/series/([^/]+)/([^/]+)/""").find(ph) ?: return item
        val (host, user, pass) = m.destructured
        val body = withContext(Dispatchers.IO) {
            runCatching {
                val url = "$host/player_api.php?username=${URLEncoder.encode(user, "UTF-8")}&password=${URLEncoder.encode(pass, "UTF-8")}" +
                    "&action=get_series_info&series_id=$sid"
                Net.base.newCall(Request.Builder().url(url).header("User-Agent", "IPTV-XY/1.0").build()).execute()
                    .use { if (it.isSuccessful) it.body?.string() else null }
            }.getOrNull()
        } ?: return item
        val eps = SeriesLazyLogic.episodes(body, host, user, pass)
        if (eps.isEmpty()) return item
        val full = item.copy(episodes = EpisodeTable.of(eps))
        cache[item.id] = full
        return full
    }
}

/** Folgen aus get_series_info (ohne Android, testbar). */
object SeriesLazyLogic {
    fun episodes(body: String, host: String, user: String, pass: String): List<Episode> = runCatching {
        val o = JSONObject(body)
        val eps = o.optJSONObject("episodes") ?: return emptyList()
        val out = ArrayList<Episode>()
        for (key in eps.keys()) {
            val a = eps.optJSONArray(key) ?: continue
            for (i in 0 until a.length()) {
                val e = a.optJSONObject(i) ?: continue
                val id = e.optString("id").ifEmpty { continue }
                val season = e.optString("season").toIntOrNull() ?: key.toIntOrNull() ?: 1
                val num = e.optString("episode_num").toIntOrNull() ?: (i + 1)
                val ext = e.optString("container_extension").trim().trimStart('.').ifEmpty { "mp4" }
                val title = e.optString("title").trim().ifEmpty { EpTitles.of(num) }
                out.add(Episode(season, num, title, "$host/series/$user/$pass/$id.$ext"))
            }
        }
        out.sortWith(compareBy({ it.season }, { it.num }))
        out
    }.getOrElse { emptyList() }
}
