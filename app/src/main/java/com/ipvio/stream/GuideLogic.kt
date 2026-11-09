package com.ipvio.stream

/** Reine Logik für den TV-Guide (Unit-Tests). */
object GuideLogic {
    /**
     * Sendungen ohne Überschneidung: nach Beginn sortiert, jede endet spätestens beim Beginn der nächsten;
     * ganz verdeckte (gleicher Beginn) fallen weg.
     */
    fun noOverlap(list: List<EpgEntry>): List<EpgEntry> {
        val s = list.sortedBy { it.start }.distinctBy { it.start }
        return s.mapIndexed { i, e ->
            val next = s.getOrNull(i + 1)?.start
            if (next != null && e.end > next) e.copy(end = next) else e
        }.filter { it.end > it.start }
    }
}
