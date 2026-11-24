package de.berlin.transitwatch.data

import de.berlin.transitwatch.core.TransitMode
import java.time.Instant
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test

class DeparturePreviewTest {
    private val now = Instant.parse("2026-11-21T10:00:00Z")

    @Test fun railWarningsAreDeduplicatedAndOtherModesAndHintsAreExcluded() {
        val result = readDeparturePreview(BOARD, now)
        assertEquals(3, result.railDepartures)
        val warning = result.notices.single()
        assertEquals("example-incident", warning.id)
        assertEquals(setOf("S5", "U2"), warning.lines)
        assertEquals(setOf(TransitMode.S_BAHN, TransitMode.U_BAHN), warning.modes)
        assertEquals(Instant.parse("2026-11-21T13:00:00Z"), warning.validUntil)
    }

    @Test fun disappearedWarningIsRetainedAsUnconfirmedWithOriginalTime() {
        val previous = readDeparturePreview(BOARD, now)
        val updated = readDeparturePreview("""{"departures":[]}""", now.plusSeconds(1800))
            .retainUnconfirmedFrom(previous)
        assertEquals(0, updated.railDepartures)
        val warning = updated.notices.single()
        assertFalse(warning.observedInLastResponse)
        assertEquals(now, warning.lastSeen)
        val again = previous.copy(checkedAt = now.plusSeconds(3600))
            .retainUnconfirmedFrom(updated)
        assertTrue(again.notices.single().observedInLastResponse)
    }

    @Test fun errorPayloadCannotBeInterpretedAsAnEmptyBoard() {
        assertThrows(JSONException::class.java) { readDeparturePreview("""{"error":"temporarily unavailable"}""", now) }
    }

    companion object {
        private const val WARNING = """{"id":"example-incident","type":"warning","summary":"Unterbrechung","text":"Testtext","validUntil":"2026-11-21T14:00:00+01:00"}"""
        private const val BOARD = """{"departures":[
          {"line":{"name":"S5","product":"suburban"},"remarks":[$WARNING]},
          {"line":{"name":"U2","product":"subway"},"remarks":[$WARNING]},
          {"line":{"name":"U8","product":"subway"},"remarks":[{"type":"hint","text":"Hinweis"}]},
          {"line":{"name":"100","product":"bus"},"remarks":[{"id":"bus","type":"warning","summary":"Bus"}]}
        ]}"""
    }
}
