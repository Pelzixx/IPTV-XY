package com.ipvio.stream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayAfrTest {
    @Test fun fitsMultiples() {
        assertTrue(DisplayAfr.fits(24f, 23.976f))
        assertTrue(DisplayAfr.fits(48f, 24f))
        assertTrue(DisplayAfr.fits(50f, 25f))
        assertTrue(DisplayAfr.fits(60f, 30f))
        assertTrue(DisplayAfr.fits(59.94f, 29.97f))
    }

    @Test fun rejectsMismatch() {
        assertFalse(DisplayAfr.fits(60f, 25f))
        assertFalse(DisplayAfr.fits(50f, 24f))
        assertFalse(DisplayAfr.fits(60f, 0f))
    }
}
