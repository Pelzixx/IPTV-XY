package com.ipvio.stream

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Einstellungen je Wiedergabeliste (TiviMate: Logo Priorität, Archiv, User-Agent, Gruppen verwalten).
 * Schlüssel ist der Name der Liste; die Werte liegen in [Tm].
 */
object PlOpts {
    /** Steigt bei jeder Änderung der ausgeblendeten Gruppen (Senderliste neu filtern). */
    var version by mutableIntStateOf(0)
        private set
    fun bump() { version++ }

    private fun k(list: String, what: String) = "plo_" + what + "_" + list.trim().lowercase().hashCode()

    /** -1 = wie unter Optik > Logos, sonst 0 Playlist, 1 EPG, 2 Ordner. */
    fun logoPrio(list: String?): Int = if (list == null) -1 else Tm.i(k(list, "logo"), -1)
    fun setLogoPrio(c: Context, list: String, v: Int) = Tm.set(c, k(list, "logo"), v)

    /** Archiv: 0 Automatisch (aus der Playlist), 1 Keine. */
    fun archive(list: String?): Int = if (list == null) 0 else Tm.i(k(list, "arch"), 0)
    fun setArchive(c: Context, list: String, v: Int) = Tm.set(c, k(list, "arch"), v)
    /** Zeitversatz für das Archiv in Stunden (−12 … +12). */
    fun archOffset(list: String?): Int = if (list == null) 0 else Tm.i(k(list, "archoff"), 0)
    fun setArchOffset(c: Context, list: String, v: Int) = Tm.set(c, k(list, "archoff"), v)

    fun ua(list: String?): String = if (list == null) "" else Tm.s(k(list, "ua")).trim()
    fun setUa(c: Context, list: String, v: String) = Tm.set(c, k(list, "ua"), v.trim())

    // Xtream Codes Parameter (TiviMate): Ausgabeformat, TV-Kanäle und VOD einbeziehen
    fun xtExt(list: String?): String = if (list != null && Tm.i(k(list, "xtfmt"), 0) == 1) "m3u8" else "ts"
    fun setXtFmt(c: Context, list: String, v: Int) = Tm.set(c, k(list, "xtfmt"), v)
    fun xtLive(list: String?): Boolean = list == null || Tm.b(k(list, "xtlive"), true)
    fun xtVod(list: String?): Boolean = list == null || Tm.b(k(list, "xtvod"), true)
    fun setXtLive(c: Context, list: String, v: Boolean) = Tm.set(c, k(list, "xtlive"), v)
    fun setXtVod(c: Context, list: String, v: Boolean) = Tm.set(c, k(list, "xtvod"), v)

    // Max. Verbindungen (TiviMate): gleichzeitige Streams dieser Liste (0 = unbegrenzt)
    fun maxConn(list: String?): Int = if (list == null) 0 else Tm.i(k(list, "maxconn"), 0)
    fun setMaxConn(c: Context, list: String, v: Int) = Tm.set(c, k(list, "maxconn"), v)

    // Playlist-Typ (TiviMate): TV-Playlist oder VOD-Playlist ohne TV-Kanäle, die zu einer übergeordneten Playlist gehört
    fun vod(list: String?): Boolean = list != null && Tm.b(k(list, "vod"), false)
    fun setVod(c: Context, list: String, v: Boolean) { Tm.set(c, k(list, "vod"), v); version++ }
    /** Übergeordnete Wiedergabeliste einer VOD-Playlist ("" = mit jeder Liste zeigen). */
    fun parent(list: String?): String = if (list == null) "" else Tm.s(k(list, "parent")).trim()
    fun setParent(c: Context, list: String, v: String) = Tm.set(c, k(list, "parent"), v.trim())

    // ---------------------------------------------------------------- Gruppen verwalten
    private const val HIDDEN = "plo_hidden_groups"
    private const val SEP = "\n"
    private const val TAB = "\t"
    @Volatile private var cacheRaw: String? = null
    @Volatile private var cache: Set<String> = emptySet()

    private fun hiddenSet(): Set<String> {
        val raw = Tm.s(HIDDEN)
        if (raw !== cacheRaw) { cache = raw.split(SEP).filter { it.isNotEmpty() }.toSet(); cacheRaw = raw }
        return cache
    }
    private fun gk(list: String, group: String) = list.trim().lowercase() + TAB + group

    val anyHidden: Boolean get() = hiddenSet().isNotEmpty()
    fun isHidden(list: String, group: String): Boolean = gk(list, group) in hiddenSet()
    fun hiddenCount(list: String): Int { val p = list.trim().lowercase() + TAB; return hiddenSet().count { it.startsWith(p) } }

    fun setHidden(c: Context, list: String, group: String, hide: Boolean) {
        val s = hiddenSet().toMutableSet()
        if (hide) s.add(gk(list, group)) else s.remove(gk(list, group))
        Tm.set(c, HIDDEN, s.joinToString(SEP))
        version++
    }

    // „Neu hinzugekommene Gruppen anzeigen“ (TiviMate, je Liste): aus = neue Gruppen gleich ausblenden
    fun showNew(list: String): Boolean = Tm.b(k(list, "shownew"), true)
    fun setShowNew(c: Context, list: String, v: Boolean) = Tm.set(c, k(list, "shownew"), v)

    /** Nach dem Laden: bekannte Gruppen merken, neue bei „aus“ ausblenden. */
    fun trackGroups(c: Context, list: String, groups: List<String>) {
        val key = k(list, "known")
        val known = Tm.s(key).split(SEP).filter { it.isNotEmpty() }.toSet()
        if (known.isNotEmpty() && !showNew(list)) groups.filter { it !in known }.forEach { setHidden(c, list, it, true) }
        if (groups.any { it !in known }) Tm.set(c, key, (known + groups).joinToString(SEP))
    }

    /** Sender ausgeblendeter Gruppen entfernen (nur Live-TV; Filme und Serien bleiben). */
    fun filter(l: List<Item>, listOf: (Item) -> String?): List<Item> {
        val h = hiddenSet()
        if (h.isEmpty()) return l
        return l.filter { it.kind != Kind.LIVE || gk(listOf(it) ?: return@filter true, it.group) !in h }
    }
}

/** Kindersicherung > Kanal sperren (TiviMate): gesperrte Sender brauchen die PIN; entsperrt bis zum nächsten App-Start. */
object ChLock {
    private const val KEY = "locked_channels"
    /** Entsperrt in dieser Sitzung: Sender-ID -> Zeitpunkt (ms). */
    private val session = HashMap<String, Long>()
    var version by mutableIntStateOf(0)
        private set
    /** Einstellungen in dieser Sitzung entsperrt (Zeitpunkt in ms, 0 = nein). */
    @Volatile var settingsAt = 0L

    /** Wie lange ein Entsperren gilt (ms); 0 = bis zum nächsten App-Start, -1 = bis zum Wechsel auf einen offenen Sender. */
    private fun keepMs(): Long = when (Tm.i(Tm.PC_UNLOCK, 0)) {
        1 -> 10 * 60_000L; 2 -> 30 * 60_000L; 3 -> 60 * 60_000L; 4 -> 120 * 60_000L; 5 -> -1L; else -> 0L
    }
    private fun valid(at: Long?): Boolean {
        if (at == null || at == 0L) return false
        val k = keepMs()
        return k <= 0L || System.currentTimeMillis() - at < k
    }

    fun list(): Set<String> = Tm.s(KEY).split('\n').filter { it.isNotEmpty() }.toSet()
    fun isLocked(id: String): Boolean = id in list()
    fun needsPin(id: String): Boolean = Parental.pinSet && isLocked(id) && !valid(session[id])
    fun unlock(id: String) { session[id] = System.currentTimeMillis() }

    // Gruppen sperren (TiviMate: Gruppe > „Gruppe sperren“): alle Sender der Gruppe brauchen die PIN
    private const val GKEY = "locked_groups"
    fun groups(): Set<String> = Tm.s(GKEY).split('\n').filter { it.isNotEmpty() }.toSet()
    fun isGroupLocked(g: String): Boolean = g in groups()
    fun toggleGroup(c: Context, g: String): Boolean {
        val s = groups().toMutableSet()
        val now = if (!s.remove(g)) { s.add(g); true } else false
        Tm.set(c, GKEY, s.joinToString("\n"))
        version++
        return now
    }
    fun needsPinItem(it: Item): Boolean = Parental.pinSet && (
        (isLocked(it.id) && !valid(session[it.id])) || (isGroupLocked(it.group) && !valid(session["g:" + it.group]) && !valid(session[it.id]))
    )
    fun unlockItem(it: Item) { val t = System.currentTimeMillis(); session[it.id] = t; if (isGroupLocked(it.group)) session["g:" + it.group] = t }

    /** Ein nicht gesperrter Sender wird gestartet: bei „bis Wechsel“ alle Freigaben aufheben. */
    fun onOpen(it: Item) { if (keepMs() == -1L && !isLocked(it.id) && !isGroupLocked(it.group)) session.clear() }

    fun settingsNeedPin(): Boolean = Parental.pinSet && Tm.b(Tm.PC_SETTINGS, false) && !valid(settingsAt)

    fun toggle(c: Context, id: String): Boolean {
        val s = list().toMutableSet()
        val now = if (!s.remove(id)) { s.add(id); true } else false
        Tm.set(c, KEY, s.joinToString("\n"))
        version++
        return now
    }
}

/** Kanäle neu anordnen (TiviMate): eigene Reihenfolge je Gruppe; Sortierung „Manuell“. */
object ChOrder {
    var version by mutableIntStateOf(0)
        private set
    private fun k(group: String) = "chorder_" + group.hashCode()

    fun order(group: String): List<String> = Tm.s(k(group)).split('\n').filter { it.isNotEmpty() }

    /** Liste nach der gespeicherten Reihenfolge; neue Sender hinten. */
    fun apply(group: String, l: List<Item>): List<Item> {
        val o = order(group)
        if (o.isEmpty()) return l
        val pos = o.withIndex().associate { it.value to it.index }
        return l.sortedBy { pos[it.id] ?: (o.size + l.indexOf(it)) }
    }

    fun move(c: Context, group: String, current: List<Item>, id: String, to: Int) {
        val ids = current.map { it.id }.toMutableList()
        val i = ids.indexOf(id)
        if (i < 0) return
        ids.removeAt(i)
        ids.add(to.coerceIn(0, ids.size), id)
        Tm.set(c, k(group), ids.joinToString("\n"))
        version++
    }

    fun reset(c: Context, group: String) { Tm.set(c, k(group), ""); version++ }
}

/** Gruppen umbenennen und eigene Sortierung je Gruppe (TiviMate: Gruppenoptionen). */
object GroupOpts {
    private fun k(what: String, group: String) = "grp_" + what + "_" + group.trim().lowercase().hashCode()
    /** Angezeigter Name einer Gruppe (umbenannt oder Original). */
    fun show(group: String): String = Tm.s(k("alias", group)).ifBlank { group }
    fun isRenamed(group: String) = Tm.s(k("alias", group)).isNotBlank()
    fun rename(c: android.content.Context, group: String, alias: String) = Tm.set(c, k("alias", group), alias.trim())
    /** Sortierung der Sender in dieser Gruppe; -1 = wie unter Optik > EPG. */
    fun sort(group: String): Int = Tm.i(k("sort", group), -1)
    fun setSort(c: android.content.Context, group: String, v: Int) = Tm.set(c, k("sort", group), v)
}
