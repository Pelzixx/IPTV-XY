package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalLogicTest {
    private fun ev(h: Int?, a: Int?, fin: Boolean = false) =
        SportEvent("k", SportsLogic.FOOTBALL, "Bundesliga", "Bayern", "Dortmund", "Bayern – Dortmund", "", 0, 6900, emptyList(), h, a, fin, "OpenLigaDB")

    @Test fun tor_wird_erkannt() = assertEquals("Tor", GoalLogic.change(ev(1, 0), ev(2, 0)))
    @Test fun nichts_neu() = assertNull(GoalLogic.change(ev(1, 0), ev(1, 0)))
    @Test fun abpfiff() = assertEquals("Abpfiff", GoalLogic.change(ev(2, 1), ev(2, 1, true)))
    @Test fun ohne_vorherigen_stand_keine_meldung() = assertNull(GoalLogic.change(null, ev(3, 0)))
    @Test fun anpfiff_null_null_ist_kein_tor() = assertNull(GoalLogic.change(ev(null, null), ev(0, 0)))
    @Test fun zeile() = assertEquals("Bayern 2:1 Dortmund", GoalLogic.line(ev(2, 1)))
}
