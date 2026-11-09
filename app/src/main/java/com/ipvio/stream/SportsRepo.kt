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
    // Weitere bei OpenLigaDB gepflegte Wettbewerbe (mit Ergebnissen und Toren, geprüft 2026-11-09). Wichtig laut Nutzer:
    // Bundesliga, 2. und 3. Liga, DFB-Pokal, Nationalmannschaft. Kürzel mit Jahreszahl gelten nur für die Saison 2026/27.
    private val OLDB = listOf("bl1" to "Bundesliga", "bl2" to "2. Bundesliga", "bl3" to "3. Liga", "dfb" to "DFB-Pokal",
        "DFBN" to "Länderspiele", "ucl" to "Champions League", "uel2026" to "Europa League", "uecl2026" to "Conference League",
        "ffb1" to "Frauen-Bundesliga", "del" to "Eishockey")

    /** Tabellen (Wunsch des Nutzers): Liga → Zeilen, alle 10 Minuten neu. */
    var tables by mutableStateOf<Map<String, List<TableRow>>>(emptyMap())
        private set
    @Volatile private var tablesAt = 0L
    private val TABLES = listOf("bl1" to "Bundesliga", "bl2" to "2. Bundesliga", "bl3" to "3. Liga", "ffb1" to "Frauen-Bundesliga", "del" to "Eishockey")

    private suspend fun loadTables() {
        if (System.currentTimeMillis() - tablesAt < 10 * 60_000) return
        val cal = java.util.Calendar.getInstance()
        // Saison beginnt im Sommer: ab Juli das laufende Jahr, sonst das Vorjahr
        val season = cal.get(java.util.Calendar.YEAR) - if (cal.get(java.util.Calendar.MONTH) >= java.util.Calendar.JULY) 0 else 1
        val res = kotlinx.coroutines.coroutineScope {
            TABLES.map { (s, n) -> async(Dispatchers.IO) { n to runCatching { table(s, season) }.getOrDefault(emptyList()) } }.awaitAll()
        }.filter { it.second.isNotEmpty() }.toMap()
        if (res.isNotEmpty()) { tables = res; tablesAt = System.currentTimeMillis() }
    }

    private fun table(shortcut: String, season: Int): List<TableRow> {
        val body = client.newCall(Request.Builder().url("https://api.openligadb.de/getbltable/$shortcut/$season").header("User-Agent", "IPTV-XY").build())
            .execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return emptyList()
        return SportsLogic.parseTable(body)
    }

    /** Was gerade auf den Sportsendern läuft (Rubrik „Live“), ohne die Sender laufender Spiele aus [events]. */
    var airing by mutableStateOf<List<SportEvent>>(emptyList())
        private set

    /** Laufende Sendungen der Sportsender: gleiche Sendung auf mehreren Sendern zusammengefasst, beste Qualität zuerst. */
    private fun airingNow(sc: Scan, ev: List<SportEvent>): List<SportEvent> {
        val now = System.currentTimeMillis() / 1000
        val busy = ev.filter { it.live(now) }.flatMap { it.channels }.toHashSet()
        return sc.entries.filter { (ch, e) -> e.start <= now && e.end > now && ch.id !in busy && e.title.isNotBlank() && SportsLogic.liveBroadcast(e.title, e.desc) }
            .groupBy { (_, e) -> e.title.trim().lowercase() + "|" + e.start }
            .map { (k, l) ->
                val e = l.first().second
                SportEvent("air|$k", SportsLogic.FOOTBALL, "Jetzt im TV", null, null, e.title.trim(), e.desc, e.start, e.end, bestFirst(l.map { it.first }).map { it.id })
            }
            .sortedWith(compareByDescending<SportEvent> { it.channels.size }.thenBy { it.title })
            .take(60)
    }

    suspend fun refresh(live: List<Item>) {
        if (loading) return
        loading = true
        try {
            runCatching { loadTables() }
            // Spielstände parallel laden, während das TV-Programm durchsucht wird
            kotlinx.coroutines.coroutineScope {
                val jobs = OLDB.map { (s, n) -> async(Dispatchers.IO) { runCatching { openLiga(s, n) }.getOrDefault(emptyList()) } }
                val old = scan
                val count = Xmltv.channelCount
                val fresh = old != null && (old.live === live || old.live.size == live.size) && old.epgCount == count && System.currentTimeMillis() - old.at < 10 * 60_000
                val scanJob = if (fresh) null else async(Dispatchers.Default) {
                    fromEpg(live, count) { part ->
                        // Bundesliga-Sender sind da: schon anzeigen, die übrigen Sportsender kommen gleich dazu
                        val m = merge(part, lastOldb)
                        withContext(Dispatchers.Main) { events = m; updatedAt = System.currentTimeMillis() }
                    }
                }
                val oldb = jobs.awaitAll().flatten()
                if (oldb.isNotEmpty()) lastOldb = oldb
                // Spielstände schon zeigen, während das Programm der Sportsender noch geholt wird
                if (scanJob != null && events.isEmpty()) {
                    events = withContext(Dispatchers.Default) { merge(old ?: Scan(emptyList(), emptyList(), live, 0, 0), lastOldb) }
                    updatedAt = System.currentTimeMillis()
                }
                scanJob?.await()?.let { scan = it }
            }
            val sc = scan ?: return
            events = withContext(Dispatchers.Default) { merge(sc, lastOldb) }
            airing = withContext(Dispatchers.Default) { airingNow(sc, events) }
            updatedAt = System.currentTimeMillis()
        } finally { loading = false }
    }

    /** Nur die Spielstände neu holen (Tor-Alarm, jede Minute): ohne erneute Programmsuche. */
    suspend fun refreshScores() {
        val sc = scan ?: return
        val oldb = kotlinx.coroutines.coroutineScope {
            OLDB.map { (s, n) -> async(Dispatchers.IO) { runCatching { openLiga(s, n) }.getOrDefault(emptyList()) } }.awaitAll().flatten()
        }
        if (oldb.isEmpty()) return
        lastOldb = oldb
        events = withContext(Dispatchers.Default) { merge(sc, lastOldb) }
        updatedAt = System.currentTimeMillis()
    }

    /** Vom Anbieter geholtes Programm je Sender-Grundname, 15 Minuten gültig (Öffnen des Reiters ist dann sofort fertig). */
    private val provCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<EpgEntry>>>()
    private val PRIO_CH = Regex("""bundesliga|konferenz|top ?event|dazn|liga|fu(ß|ss)ball|sport ?1\b|sportdigital|magenta""", RegexOption.IGNORE_CASE)

    private class Cand(val ch: Item, val name: String, val base: String, val c: SportsLogic.Chan, val xml: List<EpgEntry>)

    /**
     * Programm durchsuchen. Sportsender ohne XMLTV: Programm beim Anbieter holen (wie der TV-Guide), je Sender nur
     * einmal für alle Qualitäten. Zuerst Bundesliga-/Konferenz-/DAZN-Sender, dann ist [onPartial] schon ein
     * brauchbares Ergebnis; danach die übrigen Sportsender.
     */
    private suspend fun fromEpg(live: List<Item>, count: Int, onPartial: suspend (Scan) -> Unit): Scan {
        val cands = live.mapNotNull { ch ->
            val name = ChannelEdit.title(ch) + " " + ch.group
            val c = SportsLogic.chan(name)
            if (c.relevant) Cand(ch, name, SportsLogic.baseName(ChannelEdit.title(ch)), c, Xmltv.forItem(ch)) else null
        }
        val nowMs = System.currentTimeMillis()
        val missing = cands.filter { it.xml.isEmpty() && it.c.sport && !SportsLogic.foreign(it.name) && Epg.xtreamApi(it.ch, "get_short_epg") != null }
            .sortedByDescending { SportsLogic.isPayTvSport(it.name) }
            .groupBy { it.base }.entries.take(80)
            .filter { (b, _) -> provCache[b]?.let { nowMs - it.first < 15 * 60_000 } != true }
        val (prio, rest) = missing.partition { (b, _) -> PRIO_CH.containsMatchIn(b) }
        val sem = kotlinx.coroutines.sync.Semaphore(16)
        suspend fun fetch(list: List<Map.Entry<String, List<Cand>>>) = kotlinx.coroutines.coroutineScope {
            list.map { (base, l) ->
                async(Dispatchers.IO) {
                    sem.acquire()
                    try { provCache[base] = System.currentTimeMillis() to runCatching { Epg.now(l.first().ch) }.getOrDefault(emptyList()) } finally { sem.release() }
                }
            }.awaitAll()
        }
        fetch(prio)
        if (rest.isNotEmpty()) { onPartial(build(cands, live, count, false)); fetch(rest) }
        return build(cands, live, count, true)
    }

    private fun build(cands: List<Cand>, live: List<Item>, count: Int, final: Boolean): Scan {
        val now = System.currentTimeMillis() / 1000
        val from = now - 3 * 3600; val to = now + 48 * 3600
        class Acc(val hit: SportHit, val e: EpgEntry, val chans: MutableList<Item>)
        val acc = LinkedHashMap<String, Acc>()
        val entries = ArrayList<Pair<Item, EpgEntry>>()
        // Viele Sender teilen sich dasselbe Programm (SD/HD/FHD): jedes Programm nur einmal je Senderart auswerten
        val done = java.util.IdentityHashMap<List<EpgEntry>, HashMap<String, List<String>>>()
        // Einmal je App-Start ins Log: was Sky/DAZN gerade zeigen (zum Prüfen der Zuordnung per ADB)
        val log = final && !logged; if (final) logged = true
        for (cd in cands) {
            val ch = cd.ch; val name = cd.name; val c = cd.c
            val list = cd.xml.ifEmpty { if (c.sport) provCache[cd.base]?.second.orEmpty() else emptyList() }
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

    private fun oldbGet(path: String): JSONArray? =
        client.newCall(Request.Builder().url("https://api.openligadb.de/getmatchdata/$path").header("User-Agent", "IPTV-XY").build())
            .execute().use { if (it.isSuccessful) it.body?.string() else null }?.let { runCatching { JSONArray(it) }.getOrNull() }

    private fun openLiga(shortcut: String, league: String): List<SportEvent> {
        val cur = oldbGet(shortcut) ?: return emptyList()
        // OpenLigaDB liefert den „aktuellen“ Spieltag; ist der komplett vorbei, schaltet er manchmal erst spät um.
        // Dann den nächsten Spieltag dazu holen, sonst fehlen am neuen Spieltag die Live-Stände.
        val a = JSONArray()
        for (i in 0 until cur.length()) a.put(cur.get(i))
        val allDone = cur.length() > 0 && (0 until cur.length()).all { cur.optJSONObject(it)?.optBoolean("matchIsFinished") == true }
        if (allDone) cur.optJSONObject(0)?.let { m ->
            val season = m.optInt("leagueSeason"); val grp = m.optJSONObject("group")?.optInt("groupOrderID") ?: 0
            if (season > 0 && grp > 0) oldbGet("$shortcut/$season/${grp + 1}")?.let { n -> for (i in 0 until n.length()) a.put(n.get(i)) }
        }
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
            val hockey = league == "Eishockey"
            out.add(SportEvent("oldb|$shortcut|${m.optInt("matchID")}", if (hockey) SportsLogic.ICE else SportsLogic.FOOTBALL, league, home, away, "$home – $away",
                group, start, start + (if (hockey) 150 else 115) * 60, emptyList(), sh, sa, finished, "OpenLigaDB"))
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
        // Wiederholungen: dieselbe Paarung ist laut OpenLigaDB schon gespielt (z. B. „VFB – SVW“ nachts) → nicht als Spiel zeigen
        fun replay(e: SportEvent) = e.home != null && e.away != null && oldb.any { o ->
            o.finished && o.start < e.start - 3 * 3600 && SportsLogic.sameTeam(e.home, o.home!!) && SportsLogic.sameTeam(e.away, o.away!!)
        }
        epg.filter { it.key !in used && !replay(it) && !SportsLogic.nightReplay(it.league, it.start, endSec = it.end) }.forEach { res.add(it) }
        // Laufende zuerst, dann kommende, zuletzt beendete; darin Bundesliga & Co. vor den übrigen Wettbewerben
        return res.sortedWith(compareBy<SportEvent> { if (it.live(now)) 0 else if (it.end > now) 1 else 2 }
            .thenBy { SportsLogic.prio(it.league) }.thenBy { it.start })
    }
}
