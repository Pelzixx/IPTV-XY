package com.ipvio.stream

import android.content.Context

/** Knöpfe der Menüleiste im Live-Player (TiviMate: Optik > Player > Menü). [shown] = standardmäßig sichtbar. */
enum class MBtn(val label: String, val shown: Boolean) {
    SEARCH("Suchen", true),
    CHANNELS("Senderliste", true),
    GUIDE("TV-Guide", true),
    ARCHIVE("Archiv", true),
    RECORD("Aufnehmen", true),
    LIVE("Zurück zu Live", true),
    VIDEO("Videospuren", true),
    AUDIO("Audiospuren", true),
    SUBS("Untertitel", true),
    ASPECT("Bildformat", true),
    SLEEP("Sleeptimer", true),
    FAV("Favoritenstatus ändern", true),
    CH_OPTS("Kanaloptionen", true),
    SETTINGS("Einstellungen", true),
    EXTERNAL("Extern öffnen", true),
    HISTORY("Verlauf", false),
    MOVIES("Filme", false),
    SERIES("Serien", false),
    MYLIST("Meine Liste", false),
    EXIT("Beenden", false)
}

/** Reihenfolge und Sichtbarkeit der Menüknöpfe (gespeichert in [Tm]). */
object PlayerMenu {
    fun order(): List<MBtn> {
        val saved = Tm.s(Tm.MENU_ORDER).split(',').mapNotNull { n -> MBtn.entries.firstOrNull { it.name == n } }
        return saved + MBtn.entries.filter { it !in saved }
    }

    /** null = noch nichts geändert (dann gelten die Standardwerte). */
    private fun hiddenSet(): Set<String>? = Tm.s(Tm.MENU_HIDDEN, "#").takeIf { it != "#" }?.split(',')?.filter { it.isNotEmpty() }?.toSet()

    fun visible(b: MBtn): Boolean = hiddenSet()?.let { b.name !in it } ?: b.shown

    fun setVisible(c: Context, b: MBtn, on: Boolean) {
        val h = (hiddenSet() ?: MBtn.entries.filter { !it.shown }.map { it.name }.toSet()).toMutableSet()
        if (on) h.remove(b.name) else h.add(b.name)
        Tm.set(c, Tm.MENU_HIDDEN, h.joinToString(","))
    }

    fun move(c: Context, b: MBtn, d: Int) {
        val l = order().toMutableList()
        val i = l.indexOf(b); val j = i + d
        if (i < 0 || j !in l.indices) return
        l.add(j, l.removeAt(i))
        Tm.set(c, Tm.MENU_ORDER, l.joinToString(",") { it.name })
    }

    fun reset(c: Context) { Tm.set(c, Tm.MENU_ORDER, ""); Tm.set(c, Tm.MENU_HIDDEN, "#") }
}
