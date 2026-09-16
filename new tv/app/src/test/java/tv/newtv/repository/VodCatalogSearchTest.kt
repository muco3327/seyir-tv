package tv.newtv.repository

import org.junit.Assert.*
import org.junit.Test

class VodCatalogSearchTest {
    @Test fun siloMatchesExactlyAndRanksBeforeLongerTitles() {
        assertTrue(VodCatalogEngine.matchesQuery("Silo", "silo"))
        assertTrue(VodCatalogEngine.matchesQuery("Silo 2023 Türkçe Dublaj", "SİLO"))
        assertFalse(VodCatalogEngine.matchesQuery("Sil Baştan", "silo"))
        val titles = listOf("Silo: Origins", "The Silos", "Silo")
            .sortedBy { VodCatalogEngine.relevanceRank(it, "silo") }
        assertEquals("Silo", titles.first())
    }

    @Test fun multiWordSearchRequiresEveryWord() {
        assertTrue(VodCatalogEngine.matchesQuery("Game of Thrones", "game throne"))
        assertFalse(VodCatalogEngine.matchesQuery("Game Night", "game throne"))
    }
}
