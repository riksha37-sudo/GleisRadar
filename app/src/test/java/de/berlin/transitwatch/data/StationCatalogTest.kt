package de.berlin.transitwatch.data

import de.berlin.transitwatch.core.TransitMode
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StationCatalogTest {
    private val snapshot = File("src/main/assets/station-index.json").readText()
    private val catalog = readStationCatalog(snapshot)

    @Test fun realIndexFindsWholeRailLinesAroundBerlinMitte() {
        val start = catalog.validFrom.atStartOfDay(BerlinZone).toInstant()
        val lines = catalog.berlinMitteLines(maxOf(start, catalog.importedAt))
        assertTrue(lines.any { it.name == "S5" && it.mode == TransitMode.S_BAHN })
        assertTrue(lines.any { it.name == "U2" && it.mode == TransitMode.U_BAHN })
        assertTrue(lines.all { it.mode == TransitMode.S_BAHN || it.mode == TransitMode.U_BAHN })
    }

    @Test fun windowExpiresAtBerlinMidnightAndFutureImportsAreRejected() {
        val expired = catalog.validThrough.plusDays(1).atStartOfDay(BerlinZone).toInstant()
        assertTrue(catalog.usableAt(expired.minusSeconds(1)))
        assertFalse(catalog.usableAt(expired))
        assertTrue(catalog.berlinMitteLines(expired).isEmpty())
        assertFalse(catalog.usableAt(catalog.importedAt.minusSeconds(1)))
    }

    @Test fun badMetadataCannotSilentlyYieldPartialCatalog() {
        val bad = JSONObject(snapshot).apply { getJSONObject("metadata").put("stationCount", 99) }
        assertThrows(IllegalArgumentException::class.java) { readStationCatalog(bad.toString()) }

        val badHash = JSONObject(snapshot).apply { getJSONObject("metadata").put("sourceSha256", "invalid") }
        assertThrows(IllegalArgumentException::class.java) { readStationCatalog(badHash.toString()) }
    }
}
