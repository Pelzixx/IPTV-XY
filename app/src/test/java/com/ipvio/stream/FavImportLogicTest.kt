package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class FavImportLogicTest {
    private val ch = listOf(
        "1" to "Das Erste ʳᵃʷ", "2" to "Das Erste HD", "3" to "DE: ZDF FHD", "4" to "Sky Bundesliga 1 (1080p)",
        "5" to "DAZN 1 ᶠʰᵈ", "6" to "ProSieben", "7" to "Sky Sport Bundesliga 10"
    )

    @Test fun zusaetze_werden_ignoriert() {
        val (ids, miss) = FavImportLogic.match(listOf("Das Erste", "zdf", "DE | DAZN 1 HD", "Sky Bundesliga 1"), ch)
        assertEquals(listOf("1", "3", "5", "4"), ids)
        assertEquals(emptyList<String>(), miss)
    }

    @Test fun unbekannte_namen_werden_gemeldet() {
        val (ids, miss) = FavImportLogic.match(listOf("ProSieben", "RTL", "", "ProSieben"), ch)
        assertEquals(listOf("6"), ids)
        assertEquals(listOf("RTL"), miss)
    }

    @Test fun normform() {
        assertEquals("daserste", FavImportLogic.norm("DE: Das Erste [HD] raw"))
        assertEquals("prosieben", FavImportLogic.norm("ProSieben 4K"))
    }
}
