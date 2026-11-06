package com.ipvio.stream

/**
 * Reine Regeln für das Navigationsmenü (ohne Android), damit sie getestet werden können.
 * Garantien: Jeder Bereich kommt genau einmal vor, "Start" (0) und "Einstellungen" (5) können nie ausgeblendet werden.
 */
object MenuLogic {
    const val COUNT = 10
    const val HOME = 0
    const val SEARCH = 4
    const val SETTINGS = 5
    /** Seit Runde 13 (Entwurf): TV-Guide, Favoriten (Sender), Meine Liste (Filme/Serien). */
    const val EPG = 6
    const val FAVS = 7
    const val MYLIST = 8
    /** Runde 15 (Nachtrag 17): Verlauf – zuletzt Angesehenes, Filme und Serien getrennt. */
    const val VERLAUF = 9
    /**
     * Runde 20 (TV-Aufbau wie bei großen IPTV-Playern): Suchen, TV, Filme, Serien, Meine Liste; dahinter die eigenen
     * Bereiche (Favoriten, Verlauf, EPG, Home mit Zugang und Mitteilungen), zuletzt Einstellungen.
     */
    val DEFAULT_ORDER = listOf(SEARCH, 3, 1, 2, MYLIST, FAVS, VERLAUF, EPG, 0, 5)
    /**
     * Standardmäßig ausgeblendet: EPG (der Guide steckt in TV), Favoriten (stehen in TV links in den Gruppen) und Verlauf.
     * Alles lässt sich unter Einstellungen > Menü wieder einblenden.
     */
    const val DEFAULT_HIDDEN = "6,7,9"

    fun parseOrder(raw: String?): List<Int> {
        val order = (raw ?: DEFAULT_ORDER.joinToString(",")).split(",").mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0 until COUNT }.distinct().toMutableList()
        for (id in 0 until COUNT) if (id !in order) {
            // Neuer Bereich (gespeicherte Reihenfolge von vorher): vor „Einstellungen“ einreihen, nicht dahinter
            val s = order.indexOf(SETTINGS)
            if (id >= EPG && s >= 0) order.add(s, id) else order.add(id)
        }
        return order
    }

    fun parseHidden(raw: String?): List<Int> =
        (raw ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0 until COUNT && it != HOME && it != SETTINGS }.distinct()

    fun move(order: List<Int>, id: Int, delta: Int): List<Int> {
        val i = order.indexOf(id)
        val j = i + delta
        if (i < 0 || j < 0 || j >= order.size) return order
        val m = order.toMutableList()
        m.removeAt(i); m.add(j, id)
        return m
    }

    fun toggle(hidden: List<Int>, id: Int): List<Int> {
        if (id == HOME || id == SETTINGS || id !in 0 until COUNT) return hidden
        return if (id in hidden) hidden - id else hidden + id
    }

    fun visible(order: List<Int>, hidden: List<Int>): List<Int> = order.filter { it !in hidden }
}
