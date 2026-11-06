package app.opentv

import app.opentv.data.model.Programme
import app.opentv.xy.XyEpgAssignLogic
import org.junit.Assert.assertEquals
import org.junit.Test

class XyEpgAssignLogicTest {
    private fun p(feed: Long, start: Long) = Programme(feedId = feed, epgChannelId = "ard.de", startUtcMillis = start, endUtcMillis = start + 10, title = "T$feed")

    @Test fun nur_zugewiesene_in_reihenfolge() {
        val cand = listOf(listOf(p(1, 0), p(2, 0), p(1, 10)))
        assertEquals(listOf(1L, 1L), XyEpgAssignLogic.pick(cand, listOf(1, 2)).map { it.feedId })
        assertEquals(listOf(2L), XyEpgAssignLogic.pick(cand, listOf(2, 1)).map { it.feedId })
        assertEquals(emptyList<Programme>(), XyEpgAssignLogic.pick(cand, listOf(3)))
    }

    @Test fun ohne_zuweisung_alles_wie_bisher() {
        val cand = listOf(emptyList(), listOf(p(5, 0), p(6, 0)))
        assertEquals(listOf(5L), XyEpgAssignLogic.pick(cand, null).map { it.feedId })
    }

    @Test fun naechster_kandidat_wenn_erster_nichts_zugewiesenes_hat() {
        val cand = listOf(listOf(p(9, 0)), listOf(p(2, 0)))
        assertEquals(listOf(2L), XyEpgAssignLogic.pick(cand, listOf(2)).map { it.feedId })
    }
}
