/*
 * XY IPTV – EPG-Quellen je Anbieter zuweisen und ordnen (wie bei TiviMate je Playlist).
 * Teil von XY IPTV, basierend auf OpenTV. Lizenz: GNU GPL v3.0 oder später.
 */
package app.opentv.xy

import android.content.Context
import app.opentv.data.model.Programme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Je Anbieter (sourceId) die zugewiesenen Guide-Quellen (feedIds) in Prioritätsreihenfolge.
 * Kein Eintrag = alle Quellen (Verhalten von OpenTV).
 */
object XyEpgAssign {
    private val _state = MutableStateFlow<Map<Long, List<Long>>>(emptyMap())
    val state: StateFlow<Map<Long, List<Long>>> = _state
    private var loaded = false

    private fun prefs(c: Context) = c.getSharedPreferences("xy_epg_assign", Context.MODE_PRIVATE)

    fun init(c: Context) {
        if (loaded) return
        loaded = true
        val m = HashMap<Long, List<Long>>()
        for ((k, v) in prefs(c).all) {
            val id = k.removePrefix("s").toLongOrNull() ?: continue
            m[id] = (v as? String).orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }
        }
        _state.value = m
    }

    /** Zuweisung eines Anbieters setzen; null = zurück auf „alle Quellen“. */
    fun set(c: Context, sourceId: Long, feeds: List<Long>?) {
        val m = _state.value.toMutableMap()
        if (feeds == null) { m.remove(sourceId); prefs(c).edit().remove("s$sourceId").apply() }
        else { m[sourceId] = feeds; prefs(c).edit().putString("s$sourceId", feeds.joinToString(",")).apply() }
        _state.value = m
    }
}

/** Reine Logik ohne Android (Unit-Tests). */
object XyEpgAssignLogic {
    /**
     * Programm eines Senders aus den Kandidaten: nur zugewiesene Quellen ([assigned] = null: alle), und von diesen die erste
     * in der Reihenfolge, die etwas liefert – so mischen sich keine doppelten Einträge aus mehreren Quellen.
     */
    fun pick(candidates: List<List<Programme>>, assigned: List<Long>?): List<Programme> {
        for (list in candidates) {
            if (list.isEmpty()) continue
            val byFeed = list.groupBy { it.feedId }
            val order = assigned ?: byFeed.keys.sorted()
            for (feed in order) byFeed[feed]?.let { return it }
            if (assigned == null) return list
        }
        return emptyList()
    }
}
