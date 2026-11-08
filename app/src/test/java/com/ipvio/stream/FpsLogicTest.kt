package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class FpsLogicTest {
    private fun pts(stepUs: Long, n: Int = 16) = LongArray(n) { 1_000_000_000_000L + it * stepUs }

    @Test fun raw_sender_25_fps() = assertEquals(25, FpsLogic.fromPts(pts(40_000)))
    @Test fun hd_sender_50_fps() = assertEquals(50, FpsLogic.fromPts(pts(20_000)))
    @Test fun film_23_976_wird_24() = assertEquals(24, FpsLogic.fromPts(pts(41_708)))
    @Test fun ntsc_29_97_wird_30() = assertEquals(30, FpsLogic.snap(29.97f))

    @Test fun einzelner_sprung_stoert_nicht() {
        val p = pts(40_000); for (i in 8 until p.size) p[i] += 400_000
        assertEquals(25, FpsLogic.fromPts(p))
    }

    @Test fun zu_wenige_werte_ergeben_null() = assertEquals(0, FpsLogic.fromPts(LongArray(16) { 5L }))
}
