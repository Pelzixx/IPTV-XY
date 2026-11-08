package com.ipvio.stream

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Angaben des Anbieters zu Filmen und Serien (Xtream: „rating“ und „added“ bzw. „last_modified“), wie TiviMate sie zum
 * Sortieren „nach Bewertung“ und „nach Datum zuletzt hinzugefügt“ nutzt. Unabhängig von TMDB.
 *
 * Schlüssel: Film = ID des Eintrags (Hash seiner Adresse), Serie = Hash der Serien-Adresse (erste Folge). Gespeichert je
 * Wiedergabeliste in einer kleinen Datei, damit die Werte auch nach dem Laden aus dem Zwischenspeicher da sind.
 */
object VodMeta {
    class Info(val rating: Double?, val added: Long)

    private val map = ConcurrentHashMap<String, Info>()
    @Volatile private var loaded = false

    private fun dir(c: Context) = File(c.filesDir, "vodmeta").also { it.mkdirs() }

    fun keyOfUrl(url: String): String = url.hashCode().toString()

    private fun keyOf(i: Item): String? = when (i.kind) {
        Kind.MOVIE -> i.id
        Kind.SERIES -> i.episodes.firstOrNull()?.url?.takeIf { it.isNotEmpty() }?.let { keyOfUrl(it) }
        Kind.LIVE -> null
    }

    fun rating(i: Item): Double? = keyOf(i)?.let { map[it]?.rating }
    fun added(i: Item): Long = keyOf(i)?.let { map[it]?.added } ?: 0L
    fun hasAdded(): Boolean = map.values.any { it.added > 0 }

    /** Alle gespeicherten Dateien einlesen (einmal, im Hintergrund). */
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            dir(c).listFiles()?.forEach { f ->
                f.forEachLine { l ->
                    val p = l.split('\t')
                    if (p.size >= 3) map[p[0]] = Info(p[1].toDoubleOrNull(), p[2].toLongOrNull() ?: 0L)
                }
            }
        }
    }

    /** Sammelt beim Laden einer Xtream-Liste die Angaben und schreibt sie am Ende in die Datei dieser Liste. */
    class Collector {
        private val sb = StringBuilder()
        fun add(url: String, rating: Double?, added: Long) {
            if (rating == null && added <= 0) return
            val k = keyOfUrl(url)
            map[k] = Info(rating, added)
            sb.append(k).append('\t').append(rating ?: "").append('\t').append(added).append('\n')
        }
        fun save(c: Context, listUrl: String) {
            runCatching { File(dir(c), keyOfUrl(listUrl) + ".tsv").writeText(sb.toString()) }
        }
    }
}
