package com.ipvio.stream

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray

/**
 * Sport Guide: Spiele aus dem TV-Programm aller Sender (nächste 2 Tage, laufende, die letzten 3 Stunden),
 * gleiche Spiele auf mehreren Sendern zusammengefasst (beste Qualität zuerst), dazu Live-Spielstände der
 * Bundesliga, 2. Bundesliga und des DFB-Pokals von OpenLigaDB (frei, ohne Schlüssel, Daten unter ODbL).
 */
object SportsRepo {
    var events by mutableStateOf<List<SportEvent>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var updatedAt by mutableLongStateOf(0L)
        private set

    private val client by lazy { Net.withTimeouts(10, 15) }
    private val OLDB = listOf("bl1" to "Bundesliga", "bl2" to "2. Bundesliga", "dfb" to "DFB-Pokal")

    suspend fun refresh(live: List<Item>) {
        if (loading) return
        loading = true
        try {
            val epg = withContext(Dispatchers.Default) { fromEpg(live) }
            val oldb = withContext(Dispatchers.IO) { OLDB.flatMap { (s, n) -> runCatching { openLiga(s, n) }.getOrDefault(emptyList()) } }
            events = withContext(Dispatchers.Default) { merge(epg, oldb) }
            updatedAt = System.currentTimeMillis()
        } finally { loading = false }
    }

    private fun fromEpg(live: List<Item>): List<SportEvent> {
        val now = System.currentTimeMillis() / 1000
        val from = now - 3 * 3600; val to = now + 48 * 3600
        class Acc(val hit: SportHit, val e: EpgEntry, val chans: MutableList<Item>)
        val acc = LinkedHashMap<String, Acc>()
        for (ch in live) {
            val list = Xmltv.forItem(ch)
            if (list.isEmpty()) continue
            val name = ChannelEdit.title(ch) + " " + ch.group
            for (e in list) {
                if (e.end <= from || e.start >= to) continue
                val h = SportsLogic.parse(e.title, e.desc, name) ?: continue
                val k = SportsLogic.groupKey(h, e.start)
                val a = acc.getOrPut(k) { Acc(h, e, ArrayList()) }
                if (a.chans.none { it.id == ch.id }) a.chans.add(ch)
            }
        }
        return acc.map { (k, a) ->
            // beste Sender zuerst: Qualität aus dem Namen, Sender mit Archiv bevorzugt
            val sorted = a.chans.sortedWith(compareByDescending<Item> { SportsLogic.channelRank(ChannelEdit.title(it)) }.thenByDescending { Catchup.has(it) })
            SportEvent(k, a.hit.sport, a.hit.league, a.hit.home, a.hit.away, a.hit.title, a.e.desc, a.e.start, a.e.end, sorted.map { it.id })
        }
    }

    private fun openLiga(shortcut: String, league: String): List<SportEvent> {
        val body = client.newCall(Request.Builder().url("https://api.openligadb.de/getmatchdata/$shortcut").header("User-Agent", "IPTV-XY").build())
            .execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return emptyList()
        val a = JSONArray(body)
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val out = ArrayList<SportEvent>()
        for (i in 0 until a.length()) {
            val m = a.optJSONObject(i) ?: continue
            val start = runCatching { fmt.parse(m.optString("matchDateTimeUTC"))!!.time / 1000 }.getOrNull() ?: continue
            val home = m.optJSONObject("team1")?.optString("teamName").orEmpty()
            val away = m.optJSONObject("team2")?.optString("teamName").orEmpty()
            if (home.isBlank() || away.isBlank()) continue
            val finished = m.optBoolean("matchIsFinished")
            // Stand: letztes Tor, sonst Endergebnis
            var sh: Int? = null; var sa: Int? = null
            m.optJSONArray("goals")?.let { g -> if (g.length() > 0) g.optJSONObject(g.length() - 1)?.let { sh = it.optInt("scoreTeam1"); sa = it.optInt("scoreTeam2") } }
            m.optJSONArray("matchResults")?.let { r ->
                for (j in 0 until r.length()) r.optJSONObject(j)?.takeIf { it.optInt("resultTypeID") == 2 }?.let { if (finished || sh == null) { sh = it.optInt("pointsTeam1"); sa = it.optInt("pointsTeam2") } }
            }
            val now = System.currentTimeMillis() / 1000
            if (sh == null && now >= start && !finished) { sh = 0; sa = 0 }
            val group = m.optJSONObject("group")?.optString("groupName").orEmpty()
            out.add(SportEvent("oldb|$shortcut|${m.optInt("matchID")}", SportsLogic.FOOTBALL, league, home, away, "$home – $away",
                group, start, start + 115 * 60, emptyList(), sh, sa, finished, "OpenLigaDB"))
        }
        return out
    }

    /** OpenLigaDB-Spiele mit den EPG-Ereignissen zusammenführen (Spielstand + Sender), übrige Spiele dazu. */
    private fun merge(epg: List<SportEvent>, oldb: List<SportEvent>): List<SportEvent> {
        val now = System.currentTimeMillis() / 1000
        val used = HashSet<String>()
        val res = ArrayList<SportEvent>()
        for (o in oldb) {
            if (o.end < now - 3 * 3600 || o.start > now + 48 * 3600) continue
            val e = epg.firstOrNull { it.key !in used && it.home != null && it.away != null &&
                SportsLogic.sameTeam(it.home, o.home!!) && SportsLogic.sameTeam(it.away, o.away!!) && kotlin.math.abs(it.start - o.start) < 3 * 3600 }
            if (e != null) {
                used.add(e.key)
                res.add(o.copy(desc = e.desc.ifBlank { o.desc }, channels = e.channels, end = maxOf(o.end, e.end)))
            } else res.add(o)
        }
        epg.filter { it.key !in used }.forEach { res.add(it) }
        // Laufende zuerst, dann nach Beginn; zu Ende gegangene ans Ende
        return res.sortedWith(compareBy<SportEvent> { if (it.live(now)) 0 else if (it.end > now) 1 else 2 }.thenBy { it.start })
    }
}
