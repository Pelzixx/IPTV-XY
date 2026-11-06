package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgMergeLogicTest {
    private fun e(t: String, s: Long, end: Long) = EpgEntry(t, "", s, end)

    @Test fun zwei_quellen_versetzt_nur_eine_bleibt() {
        // Quelle 1: 22:00–23:00, 23:00–23:45; Quelle 2 dieselben Sendungen 5 Minuten versetzt
        val l = listOf(e("A", 0, 3600), e("B", 3600, 6300), e("A2", 300, 3900), e("B2", 3900, 6600))
        assertEquals(listOf("A", "B"), EpgMergeLogic.noOverlap(l).map { it.title })
    }

    @Test fun kleine_ueberschneidung_bleibt() {
        val l = listOf(e("A", 0, 3660), e("B", 3600, 7200))
        assertEquals(listOf("A", "B"), EpgMergeLogic.noOverlap(l).map { it.title })
    }

    @Test fun gleicher_beginn_erste_quelle_gewinnt() {
        val l = listOf(e("Quelle1", 0, 3600), e("Quelle2", 0, 3600))
        assertEquals(listOf("Quelle1"), EpgMergeLogic.noOverlap(l).map { it.title })
    }

    @Test fun unsortiert_wird_sortiert() {
        val l = listOf(e("B", 3600, 7200), e("A", 0, 3600))
        assertEquals(listOf("A", "B"), EpgMergeLogic.noOverlap(l).map { it.title })
    }
}
