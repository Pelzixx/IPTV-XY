package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsScheduleTest {
    @Test fun zeitraum() {
        assertTrue(HomeInfoLogic.active(0, 0, 100))
        assertFalse(HomeInfoLogic.active(200, 0, 100))
        assertTrue(HomeInfoLogic.active(100, 200, 150))
        assertFalse(HomeInfoLogic.active(100, 200, 200))
    }

    @Test fun liste_und_objekt() {
        val l = HomeInfoLogic.parseNewsAll("""[{"title":"Willkommen","text":"Hallo"},{"title":"Später","from":2000000000}]""")
        assertEquals(2, l.size)
        assertEquals(2000000000L, l[1].from)
        assertEquals(1, HomeInfoLogic.parseNewsAll("""{"title":"Nur eine"}""").size)
        // Erste gerade gültige wird angezeigt
        assertEquals("Willkommen", l.firstOrNull { HomeInfoLogic.active(it.from, it.until, 1_800_000_000) }?.title)
    }
}
