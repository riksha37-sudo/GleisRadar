package de.berlin.transitwatch.core

import java.time.Instant
import java.time.Duration

data class ProviderRequest(
    val lines: Set<LineId>,
    val from: Instant,
    val until: Instant = from.plus(Duration.ofDays(14)),
    /** Fetch these identities regardless of current location/pause/filter for possible resolution. */
    val trackedOpenEvents: Set<EventKey> = emptySet(),
)

enum class Completeness { COMPLETE, PARTIAL, UNKNOWN }
sealed class ProviderResult {
    abstract val provider: String
    abstract val fetchedAt: Instant
    data class Success(
        override val provider: String,
        override val fetchedAt: Instant,
        val generatedAt: Instant,
        val events: List<Disruption>,
        val completeness: Completeness = Completeness.UNKNOWN,
        /** Disabled until adapter semantics are verified using a real explicit end case. */
        val supportsExplicitResolution: Boolean = false,
    ) : ProviderResult()
    data class Unavailable(
        override val provider: String,
        override val fetchedAt: Instant,
        val reason: String,
    ) : ProviderResult()
    data class Failure(
        override val provider: String,
        override val fetchedAt: Instant,
        val reason: String,
        val retryable: Boolean = true,
    ) : ProviderResult()
}

interface DisruptionProvider {
    val id: String
    suspend fun fetch(request: ProviderRequest): ProviderResult
}

/** Production is intentionally blocked pending access, conditions, and verified response mapping. */
class VbbProvider : DisruptionProvider {
    override val id: String = "vbb"
    override suspend fun fetch(request: ProviderRequest): ProviderResult = ProviderResult.Unavailable(
        id, request.from, "VBB-Anfrage gestellt. Zugang, Vertragsbedingungen und Datenmapping noch nicht verifiziert.",
    )
}

enum class FixtureScenario { NORMAL, CHANGED, MISSING, RESOLVED, FAILURE, STALE }

/** Synthetic scenarios only. Never present these as current transport information. */
class FixtureProvider(
    val scenario: FixtureScenario = FixtureScenario.NORMAL,
    /** Persist this demo clock alongside state; passage of fetch time must never mutate content. */
    val anchor: Instant = Instant.parse("2026-11-17T12:00:00Z"),
) : DisruptionProvider {
    override val id: String = "fixture"
    override suspend fun fetch(request: ProviderRequest): ProviderResult = snapshot(request.from)

    fun snapshot(now: Instant): ProviderResult {
        if (scenario == FixtureScenario.FAILURE) return ProviderResult.Failure(id, now, "Simulierter Netzausfall")
        val sourceTime = if (scenario == FixtureScenario.STALE) now.minus(Duration.ofHours(3)) else now
        val acute = Disruption(
            key = EventKey(id, "synthetic-s5-001"), lines = setOf(S5), type = EventType.DISRUPTION,
            title = "DEMO · S5 unterbrochen", description = "Fiktive Störung für Entwicklung und Tests.",
            section = "Fiktiver Abschnitt außerhalb des Suchradius", sourceUpdatedAt = sourceTime,
            expectedEndAt = anchor.plus(Duration.ofHours(2)),
            replacement = if (scenario == FixtureScenario.CHANGED) ReplacementService("Bus", "DEMO · Ersatzbusse im betroffenen Abschnitt") else null,
            status = if (scenario == FixtureScenario.RESOLVED) EventStatus.RESOLVED else EventStatus.ACTIVE,
            resolution = if (scenario == FixtureScenario.RESOLVED) ResolutionEvidence(id, now, "fixture:explicit-resolution") else null,
        )
        val planned = Disruption(
            key = EventKey(id, "synthetic-u2-002"), lines = setOf(U2), type = EventType.CONSTRUCTION,
            title = "DEMO · U2 Bauarbeiten", description = "Fiktive geplante Maßnahme, keine reale Verkehrsmeldung.",
            section = "Fiktiver U2-Abschnitt", startsAt = anchor.plus(Duration.ofDays(2)),
            expectedEndAt = anchor.plus(Duration.ofDays(3)), sourceUpdatedAt = sourceTime, status = EventStatus.PLANNED,
        )
        return ProviderResult.Success(id, now, sourceTime,
            if (scenario == FixtureScenario.MISSING) emptyList() else listOf(acute, planned),
            Completeness.COMPLETE, supportsExplicitResolution = true)
    }
    companion object {
        val S5 = LineId("vbb", TransitMode.S_BAHN, "S5")
        val U2 = LineId("vbb", TransitMode.U_BAHN, "U2")
        val demoLines = setOf(S5, U2)
    }
}
