package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideLogicTest {
    @Test fun ueberschneidung_wird_gekuerzt() {
        val l = GuideLogic.noOverlap(listOf(EpgEntry("Der", "", 300, 400), EpgEntry("Promihof", "", 100, 350), EpgEntry("Doppelt", "", 100, 200)))
        assertEquals(listOf("Promihof" to 300L, "Der" to 400L), l.map { it.title to it.end })
    }
}
