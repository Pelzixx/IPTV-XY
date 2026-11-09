package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemberLogicTest {
    @Test fun name_einfach() = assertEquals("vojadsvv_xy", MemberLogic.norm("  vojadsvv_xy "))
    @Test fun name_mit_at_und_server() = assertEquals("vojadsvv_xy", MemberLogic.norm("@Vojadsvv_XY:matrix.example.de"))
    @Test fun name_leer() = assertNull(MemberLogic.norm("  "))
    @Test fun name_mit_leerzeichen() = assertNull(MemberLogic.norm("vo jads"))
    @Test fun frist_vom_panel() = assertEquals(5L, MemberLogic.deadline(5L, 100L))
    @Test fun frist_sieben_tage() = assertEquals(100L + MemberLogic.GRACE_MS, MemberLogic.deadline(null, 100L))
    @Test fun stichtag_ungueltig() = assertNull(MemberLogic.parseDay("20.11.2026"))

    @Test fun nachrichten() {
        val m = MemberLogic.parseMessages("""{"ok":true,"messages":[{"id":"a1","title":"Hallo","text":"Wartung heute","ts":5},{"id":"","text":"x"},{"id":"b","text":""}]}""")
        assertEquals(listOf(MemberMsg("a1", "Hallo", "Wartung heute", 5)), m)
    }

    @Test fun config_member() {
        val c = PresetLogic.parseConfig("""{"servers":[],"member":"https://panel/app/member.php?key=k","memberFrom":"2026-11-20"}""")!!
        assertEquals("https://panel/app/member.php?key=k", c.member)
        assertEquals("2026-11-20", c.memberFrom)
    }
}
