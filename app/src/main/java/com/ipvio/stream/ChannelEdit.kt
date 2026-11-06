package com.ipvio.stream

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** Kategorie „Ausgeblendete Sender“ in Live TV (erscheint nur, wenn es welche gibt). */
const val HIDDEN_CAT = "#HIDDEN"

/**
 * Eigene Änderungen an einzelnen Sendern (OK gedrückt halten auf einem Sender): ausblenden und umbenennen.
 * Gilt je Profil, wie die Favoriten.
 */
object ChannelEdit {
    private val hidden = HashSet<String>()
    private val names = HashMap<String, String>()
    private var loaded = false
    /** Ändert sich bei jeder Änderung, damit Listen und Namen neu gezeichnet werden. */
    var version by mutableIntStateOf(0)
        private set

    private fun prefs(c: Context) = Profiles.sp(c, "ipvio_chedit")

    fun init(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        hidden.addAll(ChannelEditLogic.decodeIds(p.getString("hidden", null)))
        names.putAll(ChannelEditLogic.decodeNames(p.getString("names", null)))
        version++
    }

    private fun save(c: Context) {
        prefs(c).edit()
            .putString("hidden", ChannelEditLogic.encodeIds(hidden))
            .putString("names", ChannelEditLogic.encodeNames(names))
            .apply()
        version++
    }

    fun isHidden(id: String): Boolean = id in hidden
    fun hiddenCount(): Int = hidden.size

    fun toggleHidden(c: Context, id: String) {
        if (!hidden.remove(id)) hidden.add(id)
        save(c)
    }

    /** Angezeigter Name: eigener Name, sonst der des Anbieters. */
    fun title(item: Item): String = names[item.id] ?: item.title
    fun isRenamed(id: String): Boolean = names.containsKey(id)

    /** Leerer Name = zurück zum Namen des Anbieters. */
    fun rename(c: Context, id: String, name: String) {
        val n = ChannelEditLogic.cleanName(name)
        if (n == null) names.remove(id) else names[id] = n
        save(c)
    }
}

/** Reine Logik zu [ChannelEdit] (ohne Android, testbar). */
object ChannelEditLogic {
    const val MAX_NAME = 60

    fun cleanName(s: String): String? = s.replace('\n', ' ').trim().take(MAX_NAME).ifEmpty { null }

    fun encodeIds(ids: Collection<String>): String = ids.joinToString("\n")
    fun decodeIds(s: String?): Set<String> = s.orEmpty().split('\n').filter { it.isNotBlank() }.toSet()

    fun encodeNames(m: Map<String, String>): String = JSONObject(m as Map<*, *>).toString()
    fun decodeNames(s: String?): Map<String, String> = runCatching {
        val o = JSONObject(s ?: return emptyMap())
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrElse { emptyMap() }

    /** Sichtbare Sender: ausgeblendete fallen weg, außer man schaut gerade die ausgeblendeten an. */
    fun <T> visible(list: List<T>, id: (T) -> String, isHidden: (String) -> Boolean, showHidden: Boolean): List<T> =
        if (showHidden) list.filter { isHidden(id(it)) } else list.filter { !isHidden(id(it)) }
}
