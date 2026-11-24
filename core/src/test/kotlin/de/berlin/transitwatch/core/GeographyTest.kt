package de.berlin.transitwatch.core

import java.time.Instant
import kotlin.math.PI
import org.junit.Assert.*
import org.junit.Test

class GeographyTest {
    private val origin = GeoPoint(52.52, 13.405)
    private val now = Instant.parse("2026-11-17T12:00:00Z")
    private val s5 = LineId("vbb", TransitMode.S_BAHN, "S5")
    private val u2 = LineId("vbb", TransitMode.U_BAHN, "U2")
    private val bus = LineId("vbb", TransitMode.BUS, "100")
    private fun north(meters: Double) = GeoPoint(origin.latitude + meters / 6_371_008.8 * 180.0 / PI, origin.longitude)
    private fun fix(meters: Double = 0.0, seconds: Long = 0, accuracy: Double = 5.0) =
        LocationFix(north(meters), accuracy, now.plusSeconds(seconds))
    private fun place(id: String = "home", meters: Double = 0.0) = PausePlace(id, id, north(meters))
    private fun stations() = listOf(
        Station("near", "Nah", north(4_999.0), setOf(s5, u2, bus)),
        Station("far", "Fern", north(5_001.0), setOf(LineId("vbb", TransitMode.S_BAHN, "S7"))),
    )

    @Test fun includesInsideAndExactBoundaryButNotOutside() {
        val atBoundary = Station("boundary", "Grenze", north(5_000.0), setOf(s5))
        assertEquals(setOf(s5, u2), LineSelector.select(origin, stations() + atBoundary))
        assertEquals(setOf(s5), LineSelector.select(origin, listOf(atBoundary)))
    }

    @Test fun includesSeveralLinesAndDeduplicatesVariantsWithoutBus() {
        val catalog = stations() + Station("branch", "Zweig", origin, setOf(s5))
        val selected = LineSelector.select(origin, catalog)
        assertEquals(2, selected.size)
        assertTrue(s5 in selected)
        assertFalse(bus in selected)
        // Relevance is expressed in whole-line identities, independent of an event's location.
        assertTrue(setOf(s5).any { it in selected })
    }

    @Test fun substantialLocationChangeRecalculates() {
        val cache = CachedLineSelector()
        val initial = cache.select(fix(), stations(), now)
        val near = cache.select(fix(20.0, 10), stations(), now.plusSeconds(10))
        val moved = cache.select(fix(-500.0, 30), stations(), now.plusSeconds(30))
        assertTrue(initial.recomputed)
        assertFalse(near.recomputed)
        assertTrue(moved.recomputed)
        assertEquals(emptySet<LineId>(), moved.lines)
    }

    @Test fun missingLocationUsesBoundedCacheAndNeverRefreshesItsAge() {
        val cache = CachedLineSelector()
        cache.select(fix(), stations(), now)
        val fallback = cache.select(null, stations(), now.plusSeconds(600))
        assertEquals(LocationState.CACHED, fallback.locationState)
        assertEquals(now, fallback.fixAt)
        val expired = cache.select(null, stations(), now.plusSeconds(1801))
        assertEquals(LocationState.UNAVAILABLE, expired.locationState)
        assertTrue(expired.lines.isEmpty())
    }

    @Test fun staleAndPoorFixesCannotRefreshCache() {
        val cache = CachedLineSelector()
        cache.select(fix(), stations(), now)
        val poor = cache.select(fix(1_000.0, 600, 900.0), stations(), now.plusSeconds(600))
        assertEquals(LocationState.CACHED, poor.locationState)
        assertEquals(now, poor.fixAt)
        val future = cache.select(fix(0.0, 10_000), stations(), now.plusSeconds(1801))
        assertEquals(LocationState.UNAVAILABLE, future.locationState)
    }

    @Test fun timeAndCatalogVersionInvalidateSelection() {
        val cache = CachedLineSelector()
        cache.select(fix(), stations(), now, "v1")
        assertTrue(cache.select(fix(0.0, 10), stations(), now.plusSeconds(10), "v2").recomputed)
        assertTrue(cache.select(fix(0.0, 910), stations(), now.plusSeconds(910), "v2").recomputed)
    }

    @Test fun entersAfterTwoStableSamplesAndRetainsFiftyMeterDefault() {
        val engine = HybridPauseEngine()
        val home = place()
        assertEquals(50.0, home.radiusMeters, 0.0)
        assertEquals(PauseState.UNKNOWN, engine.process(fix(), listOf(home), now, true).state)
        val result = engine.process(fix(seconds = 30), listOf(home), now.plusSeconds(30), true)
        assertEquals(PauseState.PAUSED, result.state)
        assertEquals(setOf("home"), result.placeIds)
    }

    @Test fun exitRequiresAccuracyHysteresisAndStability() {
        val engine = pausedEngine()
        assertEquals(PauseState.PAUSED, engine.process(fix(54.0, 60), listOf(place()), now.plusSeconds(60), true).state)
        assertEquals(PauseState.PAUSED, engine.process(fix(80.0, 90), listOf(place()), now.plusSeconds(90), true).state)
        assertEquals(PauseState.MONITORING, engine.process(fix(80.0, 120), listOf(place()), now.plusSeconds(120), true).state)
    }

    @Test fun overlappingZonesRemainPausedWhenOneIsLeft() {
        val places = listOf(place(), place("office", 60.0))
        val engine = HybridPauseEngine()
        engine.process(fix(), places, now, true)
        engine.process(fix(seconds = 30), places, now.plusSeconds(30), true)
        val moved = engine.process(fix(60.0, 60), places, now.plusSeconds(60), true)
        assertEquals(PauseState.PAUSED, moved.state)
        assertEquals(setOf("office"), moved.placeIds)
    }

    @Test fun oneGpsJumpDoesNotExitAndReturnResetsCandidate() {
        val engine = pausedEngine()
        assertEquals(PauseState.PAUSED, engine.process(fix(3_000.0, 40), listOf(place()), now.plusSeconds(40), true).state)
        assertEquals(PauseState.PAUSED, engine.process(fix(0.0, 60), listOf(place()), now.plusSeconds(60), true).state)
        assertEquals(PauseState.PAUSED, engine.process(fix(3_000.0, 90), listOf(place()), now.plusSeconds(90), true).state)
    }

    @Test fun wifiCannotIndependentlyDeclareEntryOrExit() {
        val home = place().copy(wifiFingerprint = "local-hash")
        val engine = HybridPauseEngine()
        assertEquals(PauseState.UNKNOWN, engine.process(null, listOf(home), now, true, "local-hash").state)
        engine.process(fix(), listOf(home), now, true, "local-hash")
        val inside = engine.process(fix(seconds = 30), listOf(home), now.plusSeconds(30), true, "local-hash")
        assertTrue(inside.wifiSupported)
        assertEquals(PauseState.PAUSED, engine.process(fix(seconds = 60), listOf(home), now.plusSeconds(60), true, "different").state)
    }

    @Test fun badAccuracyIsUncertainAndKnownStateExpires() {
        val engine = pausedEngine()
        assertEquals(PauseState.PAUSED, engine.process(fix(0.0, 60, 200.0), listOf(place()), now.plusSeconds(60), true).state)
        assertEquals(PauseState.UNKNOWN, engine.process(fix(0.0, 331, 200.0), listOf(place()), now.plusSeconds(331), true).state)
    }

    @Test fun permissionLossAndOldFixBecomeUnknown() {
        val engine = pausedEngine()
        assertEquals(PauseState.UNKNOWN, engine.process(fix(seconds = 60), listOf(place()), now.plusSeconds(60), false).state)
        assertEquals(PauseState.UNKNOWN, engine.process(fix(), listOf(place()), now.plusSeconds(301), true).state)
    }

    @Test fun duplicateOrOutOfOrderFixesCannotCompleteEntry() {
        val engine = HybridPauseEngine()
        engine.process(fix(), listOf(place()), now, true)
        assertEquals(PauseState.UNKNOWN, engine.process(fix(), listOf(place()), now.plusSeconds(60), true).state)
        assertEquals(PauseState.PAUSED, engine.process(fix(seconds = 61), listOf(place()), now.plusSeconds(61), true).state)
    }

    @Test fun registrationCapDoesNotDeleteAnyStoredPlaces() {
        val all = (0 until 125).map { place(it.toString(), it * 100.0) }
        val selected = selectGeofences(all, origin)
        assertEquals(100, selected.size)
        assertEquals(125, all.size)
        assertEquals("0", selected.first().id)
        assertEquals("99", selected.last().id)
    }

    @Test fun disabledPlacesAreNeitherPausedNorRegistered() {
        val disabled = place().copy(enabled = false)
        assertTrue(selectGeofences(listOf(disabled), origin).isEmpty())
        assertEquals(PauseState.MONITORING, HybridPauseEngine().process(null, listOf(disabled), now, true).state)
    }

    @Test fun futureFixCannotConfirmPauseOrRefreshSelection() {
        val engine = pausedEngine()
        assertEquals(PauseState.UNKNOWN, engine.process(fix(seconds = 400), listOf(place()), now.plusSeconds(60), true).state)
        val selector = CachedLineSelector()
        assertEquals(LocationState.UNAVAILABLE, selector.select(fix(seconds = 400), stations(), now).locationState)
    }

    @Test fun widelySeparatedSamplesCannotProveStableEntry() {
        val engine = HybridPauseEngine()
        engine.process(fix(), listOf(place()), now, true)
        assertEquals(PauseState.UNKNOWN, engine.process(fix(seconds = 600), listOf(place()), now.plusSeconds(600), true).state)
        assertEquals(PauseState.PAUSED, engine.process(fix(seconds = 630), listOf(place()), now.plusSeconds(630), true).state)
    }

    @Test fun twoDifferentDistantPlacesCannotProveStableEntry() {
        val places = listOf(place(), place("far", 3_000.0))
        val engine = HybridPauseEngine()
        engine.process(fix(), places, now, true)
        assertEquals(PauseState.UNKNOWN, engine.process(fix(3_000.0, 30), places, now.plusSeconds(30), true).state)
        assertEquals(PauseState.PAUSED, engine.process(fix(3_000.0, 60), places, now.plusSeconds(60), true).state)
    }

    @Test fun registrationKeepsLargeContainingZoneWithinCapacity() {
        val nearby = (1..100).map { place(it.toString(), it * 100.0) }
        val containing = place("large", 5_000.0).copy(radiusMeters = 5_100.0)
        assertTrue(containing in selectGeofences(nearby + containing, origin))
    }

    private fun pausedEngine(): HybridPauseEngine = HybridPauseEngine().also {
        it.process(fix(), listOf(place()), now, true)
        it.process(fix(seconds = 30), listOf(place()), now.plusSeconds(30), true)
    }
}
