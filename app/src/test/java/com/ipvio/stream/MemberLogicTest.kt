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

class DeviceIdTest {
    @Test fun fest_und_gehasht() {
        val a = MemberLogic.deviceId("1a2b3c4d5e6f7a8b")
        org.junit.Assert.assertEquals(a, MemberLogic.deviceId("1A2B3C4D5E6F7A8B"))
        org.junit.Assert.assertTrue(a!!.startsWith("a-") && a.length == 26 && !a.contains("1a2b3c4d"))
    }
    @Test fun sammel_id_nicht() {
        org.junit.Assert.assertNull(MemberLogic.deviceId("9774d56d682e549c"))
        org.junit.Assert.assertNull(MemberLogic.deviceId(null))
        org.junit.Assert.assertNull(MemberLogic.deviceId("0000000000000000"))
    }
}

class DevicesTextTest {
    @Test fun anzeige() {
        org.junit.Assert.assertEquals("1 / 2", MemberLogic.devicesText(MemberLogic.devices(org.json.JSONObject("""{"ok":true,"devices":1,"maxDevices":2}"""))!!))
        org.junit.Assert.assertEquals("3 / ∞", MemberLogic.devicesText(MemberLogic.devices(org.json.JSONObject("""{"ok":true,"devices":3,"maxDevices":0}"""))!!))
        org.junit.Assert.assertNull(MemberLogic.devices(org.json.JSONObject("""{"ok":true}""")))
    }
}
