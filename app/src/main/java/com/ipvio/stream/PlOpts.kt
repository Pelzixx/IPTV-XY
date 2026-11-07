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

    private fun k(list: String, what: String) = "plo_" + what + "_" + list.trim().lowercase().hashCode()

    /** -1 = wie unter Optik > Logos, sonst 0 Playlist, 1 EPG, 2 Ordner. */
    fun logoPrio(list: String?): Int = if (list == null) -1 else Tm.i(k(list, "logo"), -1)
    fun setLogoPrio(c: Context, list: String, v: Int) = Tm.set(c, k(list, "logo"), v)

    /** Archiv: 0 Automatisch (aus der Playlist), 1 Keine. */
    fun archive(list: String?): Int = if (list == null) 0 else Tm.i(k(list, "arch"), 0)
    fun setArchive(c: Context, list: String, v: Int) = Tm.set(c, k(list, "arch"), v)

    fun ua(list: String?): String = if (list == null) "" else Tm.s(k(list, "ua")).trim()
    fun setUa(c: Context, list: String, v: String) = Tm.set(c, k(list, "ua"), v.trim())

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

    /** Sender ausgeblendeter Gruppen entfernen (nur Live-TV; Filme und Serien bleiben). */
    fun filter(l: List<Item>, listOf: (Item) -> String?): List<Item> {
        val h = hiddenSet()
        if (h.isEmpty()) return l
        return l.filter { it.kind != Kind.LIVE || gk(listOf(it) ?: return@filter true, it.group) !in h }
    }
}
