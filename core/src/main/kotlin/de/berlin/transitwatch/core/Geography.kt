package de.berlin.transitwatch.core

import java.time.Duration
import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_008.8

data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

data class LocationFix(val point: GeoPoint, val accuracyMeters: Double, val at: Instant) {
    init { require(accuracyMeters.isFinite() && accuracyMeters >= 0.0) }
}

enum class TransitMode { S_BAHN, U_BAHN, BUS, TRAM, REGIONAL, LONG_DISTANCE }

/** name is a provider-normalized canonical line, e.g. S5. Variants share this identity. */
data class LineId(val region: String, val mode: TransitMode, val name: String) {
    init { require(region.isNotBlank() && name.isNotBlank()) }
}

data class Station(val id: String, val name: String, val point: GeoPoint, val lines: Set<LineId>)

data class PausePlace(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val radiusMeters: Double = 50.0,
    val enabled: Boolean = true,
    val wifiFingerprint: String? = null,
) {
    init {
        require(id.isNotBlank() && name.isNotBlank())
        require(radiusMeters.isFinite() && radiusMeters > 0.0)
    }
}

fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val deltaLat = lat2 - lat1
    val deltaLon = Math.toRadians(b.longitude - a.longitude)
    val haversine = sin(deltaLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}

object LineSelector {
    /** The result contains entire canonical lines, never only the station's nearby segment. */
    fun select(point: GeoPoint, stations: Collection<Station>, radiusMeters: Double = 5_000.0): Set<LineId> {
        require(radiusMeters.isFinite() && radiusMeters >= 0.0)
        return stations.asSequence()
            .filter { distanceMeters(point, it.point) <= radiusMeters + 0.000001 }
            .flatMap { it.lines.asSequence() }
            .filter { it.mode == TransitMode.S_BAHN || it.mode == TransitMode.U_BAHN }
            .toSet()
    }
}

enum class LocationState { FRESH, CACHED, UNAVAILABLE }

data class LineSelection(
    val lines: Set<LineId>,
    val locationState: LocationState,
    val fixAt: Instant?,
    val recomputed: Boolean,
)

/**
 * Uses an in-memory location cache, never a movement history. The repository must change
 * catalogVersion whenever station/line membership changes. Unusable fixes cannot refresh its age.
 */
class CachedLineSelector(
    private val movementThresholdMeters: Double = 250.0,
    private val recomputeAfter: Duration = Duration.ofMinutes(15),
    private val maxLocationAge: Duration = Duration.ofMinutes(30),
    private val maxAccuracyMeters: Double = 500.0,
) {
    init {
        require(movementThresholdMeters.isFinite() && movementThresholdMeters > 0)
        require(maxAccuracyMeters.isFinite() && maxAccuracyMeters > 0)
        require(!recomputeAfter.isNegative && !recomputeAfter.isZero)
        require(!maxLocationAge.isNegative && !maxLocationAge.isZero)
    }
    private var lastValidFix: LocationFix? = null
    private var selectedAt: Instant? = null
    private var selectedFrom: GeoPoint? = null
    private var selectedCatalogVersion: String? = null
    private var lines: Set<LineId> = emptySet()

    fun select(
        fix: LocationFix?,
        stations: Collection<Station>,
        now: Instant,
        catalogVersion: String = "default",
    ): LineSelection {
        val accepted = fix?.takeIf {
            !it.at.isAfter(now) && Duration.between(it.at, now) <= maxLocationAge &&
                it.accuracyMeters <= maxAccuracyMeters &&
                (lastValidFix == null || !it.at.isBefore(lastValidFix!!.at))
        }
        if (accepted != null) lastValidFix = accepted
        val usable = lastValidFix?.takeIf { !it.at.isAfter(now) && Duration.between(it.at, now) <= maxLocationAge }
            ?: return LineSelection(emptySet(), LocationState.UNAVAILABLE, lastValidFix?.at, false)
        val recompute = selectedFrom == null || selectedAt == null ||
            catalogVersion != selectedCatalogVersion ||
            distanceMeters(usable.point, selectedFrom!!) >= movementThresholdMeters ||
            Duration.between(selectedAt, now) >= recomputeAfter || now.isBefore(selectedAt)
        if (recompute) {
            lines = LineSelector.select(usable.point, stations)
            selectedFrom = usable.point
            selectedAt = now
            selectedCatalogVersion = catalogVersion
        }
        val state = if (accepted != null && Duration.between(accepted.at, now) <= Duration.ofMinutes(2)) {
            LocationState.FRESH
        } else LocationState.CACHED
        return LineSelection(lines, state, usable.at, recompute)
    }
}

enum class PauseState { UNKNOWN, PAUSED, MONITORING }

data class PauseDecision(
    val state: PauseState,
    val placeIds: Set<String>,
    val reason: String,
    val wifiSupported: Boolean = false,
)

/**
 * Conservative domain policy, not an Android GeofencingClient implementation. A transition
 * needs two distinct location samples spanning 30 seconds. Wi-Fi is corroboration only.
 * Permission/fix loss becomes UNKNOWN; callers must never display UNKNOWN as certainly outside.
 */
class HybridPauseEngine(
    private val stabilityDuration: Duration = Duration.ofSeconds(30),
    private val maxFixAge: Duration = Duration.ofMinutes(5),
    private val exitHysteresisMeters: Double = 10.0,
) {
    init {
        require(!stabilityDuration.isNegative && !stabilityDuration.isZero)
        require(!maxFixAge.isNegative && !maxFixAge.isZero)
        require(exitHysteresisMeters >= 0.0 && exitHysteresisMeters.isFinite())
    }
    private var state = PauseState.UNKNOWN
    private var placeIds = emptySet<String>()
    private var lastConfirmedAt: Instant? = null
    private var lastAcceptedAt: Instant? = null
    private var placesSignature: List<PausePlace>? = null
    private var candidate: Candidate? = null

    private data class Candidate(
        val state: PauseState,
        val firstAt: Instant,
        val lastAt: Instant,
        val samples: Int,
        val placeIds: Set<String>,
    )

    fun process(
        fix: LocationFix?,
        places: Collection<PausePlace>,
        now: Instant,
        permissionGranted: Boolean,
        wifiFingerprint: String? = null,
    ): PauseDecision {
        if (!permissionGranted) return unknown("Standortberechtigung fehlt")
        val active = places.filter { it.enabled }.sortedBy { it.id }
        if (active != placesSignature) {
            state = PauseState.UNKNOWN
            placeIds = emptySet()
            lastConfirmedAt = null
            lastAcceptedAt = null
            candidate = null
            placesSignature = active
        }
        if (active.isEmpty()) {
            state = PauseState.MONITORING
            placeIds = emptySet()
            candidate = null
            return PauseDecision(state, placeIds, "Keine aktiven Pausenorte")
        }
        if (fix == null || fix.at.isAfter(now) || Duration.between(fix.at, now) > maxFixAge) {
            return unknown("Kein hinreichend aktueller Standort")
        }
        if (lastAcceptedAt != null && !fix.at.isAfter(lastAcceptedAt)) {
            return retainedOrUnknown(now, "Warte auf neue Standortmessung")
        }
        lastAcceptedAt = fix.at
        val confidentlyInside = active.filter {
            distanceMeters(fix.point, it.point) + fix.accuracyMeters <= it.radiusMeters
        }
        val confidentlyOutside = active.all {
            distanceMeters(fix.point, it.point) - fix.accuracyMeters >= it.radiusMeters + exitHysteresisMeters
        }
        val observed = when {
            confidentlyInside.isNotEmpty() -> PauseState.PAUSED
            confidentlyOutside -> PauseState.MONITORING
            else -> null
        }
        if (observed == null) {
            candidate = null
            return retainedOrUnknown(now, "Standortgenauigkeit oder Randbereich unklar")
        }
        val observedPlaceIds = confidentlyInside.map { it.id }.toSet()
        val wifiSupported = wifiFingerprint != null && confidentlyInside.any { it.wifiFingerprint == wifiFingerprint }
        if (observed == state) {
            placeIds = observedPlaceIds
            lastConfirmedAt = fix.at
            candidate = null
            return PauseDecision(state, placeIds, "Standort bestätigt", wifiSupported)
        }
        val pending = candidate
        val consistentCandidate = pending != null && pending.state == observed &&
            Duration.between(pending.lastAt, fix.at) <= maxFixAge &&
            (observed != PauseState.PAUSED || pending.placeIds.intersect(observedPlaceIds).isNotEmpty())
        val next = if (consistentCandidate) {
            pending.copy(lastAt = fix.at, samples = pending.samples + 1,
                placeIds = pending.placeIds.intersect(observedPlaceIds))
        } else Candidate(observed, fix.at, fix.at, 1, observedPlaceIds)
        candidate = next
        if (next.samples >= 2 && Duration.between(next.firstAt, next.lastAt) >= stabilityDuration) {
            state = observed
            placeIds = observedPlaceIds
            lastConfirmedAt = fix.at
            candidate = null
            return PauseDecision(state, placeIds, "Standortwechsel stabil bestätigt", wifiSupported)
        }
        return retainedOrUnknown(now, "Standortwechsel wird geprüft")
    }

    private fun retainedOrUnknown(now: Instant, reason: String): PauseDecision {
        val confirmed = lastConfirmedAt
        if (confirmed == null || confirmed.isAfter(now) || Duration.between(confirmed, now) > maxFixAge) {
            state = PauseState.UNKNOWN
            placeIds = emptySet()
        }
        return PauseDecision(state, placeIds, reason)
    }

    private fun unknown(reason: String): PauseDecision {
        state = PauseState.UNKNOWN
        placeIds = emptySet()
        candidate = null
        lastConfirmedAt = null
        return PauseDecision(state, placeIds, reason)
    }
}

/**
 * Registration selection only: callers persist every place, including those outside this window.
 * Rank by distance to zone edge so a large zone containing the user remains registered.
 */
fun selectGeofences(places: Collection<PausePlace>, reference: GeoPoint, limit: Int = 100): List<PausePlace> {
    require(limit in 0..100)
    return places.filter { it.enabled }
        .sortedWith(compareBy<PausePlace> { (distanceMeters(reference, it.point) - it.radiusMeters).coerceAtLeast(0.0) }
            .thenBy { distanceMeters(reference, it.point) }.thenBy { it.id })
        .take(min(limit, 100))
}
