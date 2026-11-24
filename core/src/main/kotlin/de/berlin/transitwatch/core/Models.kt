package de.berlin.transitwatch.core

import java.security.MessageDigest
import java.time.Instant

/** Identity is namespaced by provider. A reused provider ID needs an explicit new occurrence ID. */
data class EventKey(val provider: String, val id: String) {
    init { require(provider.isNotBlank() && id.isNotBlank()) }
    val notificationTag: String get() = "event:" + digest("${provider.length}:$provider$id")
}

enum class EventType { DISRUPTION, DELAY, CANCELLATION, CLOSURE, CONSTRUCTION, REPLACEMENT_SERVICE }
enum class EventStatus { PLANNED, ACTIVE, UPDATED, RESOLVED, UNKNOWN }
enum class Freshness { FRESH, STALE, MISSING, UNKNOWN }

/** Only a verified provider adapter may construct this from an explicit structured resolution. */
data class ResolutionEvidence(val provider: String, val confirmedAt: Instant, val reference: String)
data class ReplacementService(val mode: String, val description: String)

data class Disruption(
    val key: EventKey,
    val lines: Set<LineId>,
    val type: EventType,
    val title: String,
    val description: String,
    val stations: List<String> = emptyList(),
    val section: String? = null,
    val startsAt: Instant? = null,
    val expectedEndAt: Instant? = null,
    val sourceUpdatedAt: Instant,
    val status: EventStatus = EventStatus.ACTIVE,
    val replacement: ReplacementService? = null,
    val sourceUrl: String? = null,
    val resolution: ResolutionEvidence? = null,
) {
    init { require(title.isNotBlank()) }
    /** Stable, length-prefixed content. Transport timestamps/URL changes do not create alerts. */
    fun fingerprint(effectiveStatus: EventStatus = status): String = digest(buildString {
        fun part(value: String?) { append(value?.length ?: -1).append(':').append(value ?: "") }
        part(lines.size.toString())
        lines.sortedWith(compareBy({ it.region }, { it.mode.name }, { it.name })).forEach {
            part(it.region); part(it.mode.name); part(it.name)
        }
        part(type.name); part(title); part(description); part(stations.size.toString()); stations.forEach(::part); part(section)
        part(startsAt?.toString()); part(expectedEndAt?.toString())
        part(if (effectiveStatus == EventStatus.UPDATED) EventStatus.ACTIVE.name else effectiveStatus.name)
        part(replacement?.mode); part(replacement?.description)
    })
}

data class TrackedEvent(
    val event: Disruption,
    val status: EventStatus,
    val freshness: Freshness,
    val lastSeenAt: Instant,
    val notifiedFingerprint: String? = null,
    val wasNotified: Boolean = false,
    val activationDerivedFromSchedule: Boolean = false,
)

data class TrackingState(val events: Map<EventKey, TrackedEvent> = emptyMap())
enum class NotificationKind { ACUTE, PLANNED, CHANGE, RESOLVED, SUMMARY }
data class NotificationCommand(
    val tag: String,
    val kind: NotificationKind,
    val keys: List<EventKey>,
    val title: String,
    val body: String,
)
data class TrackingOutcome(val state: TrackingState, val notifications: List<NotificationCommand>)

internal fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
