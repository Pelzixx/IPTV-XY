package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeInfoLogicTest {
    @Test fun zugang_aus_player_api() {
        val a = HomeInfoLogic.parseAccount("""{"user_info":{"status":"Active","exp_date":"1800000000","max_connections":"2","active_cons":1,"is_trial":"0"}}""")!!
        assertEquals("Active", a.status)
        assertEquals(1800000000L, a.expSec)
        assertEquals(2, a.maxCons)
        assertEquals(1, a.activeCons)
        assertEquals(false, a.trial)
        // Unbegrenzt: exp_date null
        assertNull(HomeInfoLogic.parseAccount("""{"user_info":{"status":"Active","exp_date":null}}""")!!.expSec)
        assertNull(HomeInfoLogic.parseAccount("kein json"))
    }

    @Test fun tage_bis_ablauf() {
        assertEquals(1L, HomeInfoLogic.daysLeft(1000 + 3600, 1000))
        assertEquals(2L, HomeInfoLogic.daysLeft(1000 + 86400 + 1, 1000))
        assertEquals(-1L, HomeInfoLogic.daysLeft(999, 1000))
        assertNull(HomeInfoLogic.daysLeft(null, 1000))
    }

    @Test fun mitteilung() {
        val n = HomeInfoLogic.parseNews("""{"title":"Hallo","text":"Neu: Sport","qr":"https://matrix.to/#/#x:y","qrLabel":"Support"}""")!!
        assertEquals("Hallo", n.title)
        assertEquals("https://matrix.to/#/#x:y", n.qr)
        assertNull(n.image)
        assertNull(HomeInfoLogic.parseNews("""{"title":"","text":""}"""))
        assertTrue(HomeInfoLogic.parseNews("""{"image":"https://x/y.png"}""") != null)
    }
}
