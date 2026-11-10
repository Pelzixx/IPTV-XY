package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgNamesTest {
    @Test fun normalizesProviderNames() {
        assertEquals("daserste", EpgNames.norm("DE| DAS ERSTE HD"))
        assertEquals("daserste", EpgNames.norm("Das Erste"))
        // Hochgestellte Kürzel wie bei XY TV
        assertEquals("daserste", EpgNames.norm("Das Erste ʰᵈ"))
        assertEquals("3sat", EpgNames.norm("3sat ᶠʰᵈ"))
        assertEquals("skybundesliga", EpgNames.norm("Sky Bundesliga ᵘʰᵈ"))
        assertEquals("skybundesliga", EpgNames.norm("Sky Bundesliga ʳᵃʷ"))
        assertEquals("zdf", EpgNames.norm("DE: ZDF FHD"))
        assertEquals("prosiebenmaxx", EpgNames.norm("DE| PROSIEBEN MAXX HEVC"))
        assertEquals("sat1gold", EpgNames.norm("SAT.1 Gold (1080p)"))
        assertEquals("rtlplus", EpgNames.norm("RTL+ [Geo-blocked]"))
        assertEquals("daserste", EpgNames.norm("DasErste"))
    }
}
