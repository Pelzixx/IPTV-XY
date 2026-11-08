package com.ipvio.stream

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray

/**
 * Sport Guide: Spiele aus dem TV-Programm der Sport- und deutschen Sender (nächste 2 Tage, laufende, die letzten
 * 3 Stunden), nur deutsche bzw. für Deutschland wichtige Wettbewerbe (Bundesliga zuerst). Gleiche Spiele auf mehreren
 * Sendern werden zusammengefasst (beste Qualität zuerst). Dazu Live-Spielstände der Bundesliga, 2. Bundesliga, 3. Liga
 * und des DFB-Pokals von OpenLigaDB (frei, ohne Schlüssel, Daten unter ODbL).
 */
object SportsRepo {
    var events by mutableStateOf<List<SportEvent>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var updatedAt by mutableLongStateOf(0L)
        private set

    /** Ergebnis der letzten Programmsuche (teuer, daher nur bei neuem Programm oder alle 10 Minuten). */
    private class Scan(val events: List<SportEvent>, val entries: List<Pair<Item, EpgEntry>>, val live: List<Item>, val epgCount: Int, val at: Long)
    @Volatile private var scan: Scan? = null
    @Volatile private var lastOldb: List<SportEvent> = emptyList()
    @Volatile private var logged = false

    private val client by lazy { Net.withTimeouts(10, 15) }
    private val OLDB = listOf("bl1" to "Bundesliga", "bl2" to "2. Bundesliga", "bl3" to "3. Liga", "dfb" to "DFB-Pokal")

    suspend fun refresh(live: List<Item>) {
        if (loading) return
        loading = true
        try {
            // Spielstände parallel laden, während das TV-Programm durchsucht wird
            val oldb = kotlinx.coroutines.coroutineScope {
                val jobs = OLDB.map { (s, n) -> async(Dispatchers.IO) { runCatching { openLiga(s, n) }.getOrDefault(emptyList()) } }
                val old = scan
                val count = Xmltv.channelCount
                val sc = if (old != null && (old.live === live || old.live.size == live.size) && old.epgCount == count && System.currentTimeMillis() - old.at < 10 * 60_000) old
                    else withContext(Dispatchers.Default) { fromEpg(live, count) }.also { scan = it }
                if (events.isEmpty()) { events = withContext(Dispatchers.Default) { merge(sc, lastOldb) }; updatedAt = System.currentTimeMillis() }
                jobs.awaitAll().flatten()
            }
            if (oldb.isNotEmpty()) lastOldb = oldb
            val sc = scan ?: return
            events = withContext(Dispatchers.Default) { merge(sc, lastOldb) }
            updatedAt = System.currentTimeMillis()
        } finally { loading = false }
    }

    private fun fromEpg(live: List<Item>, count: Int): Scan {
        val now = System.currentTimeMillis() / 1000
        val from = now - 3 * 3600; val to = now + 48 * 3600
        class Acc(val hit: SportHit, val e: EpgEntry, val chans: MutableList<Item>)
        val acc = LinkedHashMap<String, Acc>()
        val entries = ArrayList<Pair<Item, EpgEntry>>()
        // Viele Sender teilen sich dasselbe Programm (SD/HD/FHD): jedes Programm nur einmal je Senderart auswerten
        val done = java.util.IdentityHashMap<List<EpgEntry>, HashMap<String, List<String>>>()
        // Einmal je App-Start ins Log: was Sky/DAZN gerade zeigen (zum Prüfen der Zuordnung per ADB)
        val log = !logged; logged = true
        for (ch in live) {
            val name = ChannelEdit.title(ch) + " " + ch.group
            val c = SportsLogic.chan(name)
            if (!c.relevant) continue
            val list = Xmltv.forItem(ch)
            if (list.isEmpty()) continue
            if (log && c.sport && SportsLogic.isPayTvSport(name)) list.filter { it.end > now && it.start < now + 4 * 3600 }.take(3)
                .forEach { android.util.Log.i("XYSPORT", "${ChannelEdit.title(ch)} | ${it.title} | ${it.desc.take(100)}") }
            if (c.sport) for (e in list) if (e.end > from && e.start < to) entries.add(ch to e)
            val keys = done.getOrPut(list) { HashMap() }.getOrPut("${c.sport}|${c.league}") {
                val out = ArrayList<String>()
                for (e in list) {
                    if (e.end <= from || e.start >= to) continue
                    val h = SportsLogic.parse(e.title, e.desc, c) ?: continue
                    if (!SportsLogic.allowed(h.league)) continue
                    val k = SportsLogic.groupKey(h, e.start)
                    acc.getOrPut(k) { Acc(h, e, ArrayList()) }
                    out.add(k)
                }
                out
            }
            for (k in keys) { val a = acc[k] ?: continue; if (a.chans.none { it.id == ch.id }) a.chans.add(ch) }
        }
        val ev = acc.map { (k, a) ->
            SportEvent(k, a.hit.sport, a.hit.league, a.hit.home, a.hit.away, a.hit.title, a.e.desc, a.e.start, a.e.end, bestFirst(a.chans).map { it.id })
        }
        return Scan(ev, entries, live, count, System.currentTimeMillis())
    }

    /** Beste Sender zuerst: Qualität aus dem Namen, Sender mit Archiv bevorzugt. */
    private fun bestFirst(chans: Collection<Item>): List<Item> =
        chans.distinctBy { it.id }.sortedWith(compareByDescending<Item> { SportsLogic.channelRank(ChannelEdit.title(it)) }.thenByDescending { Catchup.has(it) })

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

    /**
     * OpenLigaDB-Spiele mit dem TV-Programm zusammenführen (Spielstand + Sender), übrige Programm-Ereignisse dazu.
     * Sender je Spiel: 1. Programm mit derselben Paarung, 2. Sportsender, deren Sendung um den Anpfiff beide Teams
     * nennt, 3. die Konferenz der Liga zur selben Zeit (hinten angehängt).
     */
    private fun merge(sc: Scan, oldb: List<SportEvent>): List<SportEvent> {
        val now = System.currentTimeMillis() / 1000
        val epg = sc.events
        val used = HashSet<String>()
        val res = ArrayList<SportEvent>()
        for (o in oldb) {
            if (o.end < now - 3 * 3600 || o.start > now + 48 * 3600) continue
            val e = epg.firstOrNull { it.key !in used && it.home != null && it.away != null &&
                SportsLogic.sameTeam(it.home, o.home!!) && SportsLogic.sameTeam(it.away, o.away!!) && kotlin.math.abs(it.start - o.start) < 3 * 3600 }
            val chans = LinkedHashSet<String>()
            var desc = o.desc
            if (e != null) { used.add(e.key); chans.addAll(e.channels); if (e.desc.isNotBlank()) desc = e.desc }
            if (chans.isEmpty()) {
                val hits = sc.entries.filter { (_, x) -> x.start in (o.start - 2 * 3600)..(o.start + 1800) && x.end > o.start &&
                    (x.title + " " + x.desc.take(400)).let { t -> SportsLogic.mentions(t, o.home!!) && SportsLogic.mentions(t, o.away!!) } }
                bestFirst(hits.map { it.first }).forEach { chans.add(it.id) }
            }
            // Konferenz derselben Liga zur selben Zeit
            epg.filter { it.home == null && it.league == o.league && it.title.endsWith("Konferenz") && it.start <= o.start + 1800 && it.end > o.start }
                .forEach { chans.addAll(it.channels) }
            res.add(o.copy(desc = desc, channels = chans.toList()))
        }
        epg.filter { it.key !in used }.forEach { res.add(it) }
        // Laufende zuerst, dann kommende, zuletzt beendete; darin Bundesliga & Co. vor den übrigen Wettbewerben
        return res.sortedWith(compareBy<SportEvent> { if (it.live(now)) 0 else if (it.end > now) 1 else 2 }
            .thenBy { SportsLogic.prio(it.league) }.thenBy { it.start })
    }
}
