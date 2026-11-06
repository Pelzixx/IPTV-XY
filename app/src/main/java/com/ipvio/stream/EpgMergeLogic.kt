package com.ipvio.stream

/**
 * Programm eines Senders bereinigen (ohne Android, testbar).
 *
 * Liefern mehrere EPG-Quellen denselben Sender, kamen früher alle Sendungen in eine Liste. Im Guide lagen dann zwei
 * Programme leicht versetzt übereinander („EMMA…“ und „War das zu hart?“ in einem Kasten).
 */
object EpgMergeLogic {
    /** Kleine Überschneidungen (Sendungen enden oft eine Minute „zu spät“) gelten nicht als doppelt. */
    private const val TOLERANCE = 120L

    /**
     * Nach Beginn sortiert; eine Sendung, die in eine schon übernommene hineinragt, fällt weg. Bei gleichem Beginn bleibt
     * die zuerst gelieferte (frühere Quelle hat Vorrang).
     */
    fun noOverlap(list: List<EpgEntry>): List<EpgEntry> {
        if (list.size < 2) return list
        val sorted = list.sortedBy { it.start }
        val out = ArrayList<EpgEntry>(sorted.size)
        var lastEnd = Long.MIN_VALUE
        for (e in sorted) {
            if (out.isNotEmpty() && e.start < lastEnd - TOLERANCE) continue
            out.add(e)
            lastEnd = e.end
        }
        return out
    }
}
