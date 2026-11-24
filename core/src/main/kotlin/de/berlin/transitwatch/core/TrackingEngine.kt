package de.berlin.transitwatch.core

import java.time.Duration
import java.time.Instant

data class TrackingContext(
    val now: Instant,
    val relevantLines: Set<LineId>,
    val monitoringActive: Boolean = true,
    val summarizeInitial: Boolean = true,
    val planningHorizon: Duration = Duration.ofDays(14),
    val maxDataAge: Duration = Duration.ofMinutes(30),
    val enabledNotificationKinds: Set<NotificationKind> = NotificationKind.entries.toSet(),
) {
    init {
        require(!planningHorizon.isNegative)
        require(!maxDataAge.isNegative && !maxDataAge.isZero)
    }
}

/**
 * Pure reducer. Persist returned state AND notifications atomically before delivery.
 * Never treat absence, a failure, or a forecast as an end confirmation.
 */
class TrackingEngine {
    fun reduce(state: TrackingState, result: ProviderResult, context: TrackingContext): TrackingOutcome {
        val entries = state.events.mapValues { (_, previous) ->
            if (previous.status != EventStatus.RESOLVED && Duration.between(previous.lastSeenAt, context.now) > context.maxDataAge)
                previous.copy(freshness = Freshness.STALE) else previous
        }.toMutableMap()
        val notifications = mutableListOf<NotificationCommand>()
        val validBatch = result is ProviderResult.Success &&
            result.generatedAt <= context.now.plusSeconds(300) &&
            result.fetchedAt <= context.now.plusSeconds(300) &&
            result.generatedAt <= result.fetchedAt.plusSeconds(300) &&
            Duration.between(result.fetchedAt, context.now) <= context.maxDataAge &&
            Duration.between(result.generatedAt, context.now) <= context.maxDataAge
        if (!validBatch) {
            entries.replaceAll { key, previous ->
                if (key.provider == result.provider && previous.status != EventStatus.RESOLVED)
                    previous.copy(freshness = Freshness.STALE) else previous
            }
            return TrackingOutcome(TrackingState(entries), emptyList())
        }
        val incomingKeys = result.events.map { it.key }.toSet()
        entries.replaceAll { key, previous ->
            if (key.provider == result.provider && key !in incomingKeys && previous.status != EventStatus.RESOLVED)
                previous.copy(freshness = if (result.completeness == Completeness.COMPLETE) Freshness.MISSING else Freshness.STALE)
            else previous
        }
        val newlyNotified = mutableListOf<EventKey>()
        for ((key, versions) in result.events.filter { it.key.provider == result.provider }.groupBy { it.key }) {
            val latestTime = versions.maxOf { it.sourceUpdatedAt }
            val newest = versions.filter { it.sourceUpdatedAt == latestTime }
            val old = entries[key]
            // Equal-time contradictory versions have no defensible winner.
            if (newest.map { it.fingerprint() to it.resolution }.distinct().size != 1) {
                if (old != null) entries[key] = old.copy(freshness = Freshness.UNKNOWN)
                continue
            }
            val event = newest.first()
            if (event.sourceUpdatedAt > context.now.plusSeconds(300) ||
                event.sourceUpdatedAt > result.generatedAt.plusSeconds(300)) continue
            if (old != null && event.sourceUpdatedAt < old.event.sourceUpdatedAt) continue
            // Terminal identities cannot reopen. A provider adapter must identify a new occurrence.
            if (old?.status == EventStatus.RESOLVED) continue
            if (old != null && event.sourceUpdatedAt == old.event.sourceUpdatedAt &&
                (event.fingerprint() != old.event.fingerprint() || event.resolution != old.event.resolution)) {
                entries[key] = old.copy(freshness = Freshness.UNKNOWN)
                continue
            }
            val resolution = event.resolution
            val confirmed = event.status == EventStatus.RESOLVED && result.supportsExplicitResolution &&
                resolution != null && resolution.provider == key.provider && resolution.reference.isNotBlank() &&
                resolution.confirmedAt <= context.now.plusSeconds(300) &&
                (old == null || resolution.confirmedAt >= old.event.sourceUpdatedAt)
            val derivedActivation = event.status == EventStatus.PLANNED && event.startsAt != null && event.startsAt <= context.now
            val effective = when {
                confirmed -> EventStatus.RESOLVED
                event.status == EventStatus.RESOLVED -> EventStatus.UNKNOWN
                derivedActivation -> EventStatus.ACTIVE
                event.status == EventStatus.UPDATED -> EventStatus.ACTIVE
                else -> event.status
            }
            val fingerprint = event.fingerprint(effective)
            val changed = old != null && fingerprint != old.event.fingerprint(
                if (old.status == EventStatus.UPDATED) EventStatus.ACTIVE else old.status)
            val storedStatus = if (effective == EventStatus.ACTIVE && (changed || old?.status == EventStatus.UPDATED)) EventStatus.UPDATED else effective
            var tracked = TrackedEvent(event, storedStatus, Freshness.FRESH, result.fetchedAt,
                old?.notifiedFingerprint, old?.wasNotified ?: false, derivedActivation)
            val relevant = event.lines.any { it in context.relevantLines } &&
                (effective != EventStatus.PLANNED || (event.startsAt != null && event.startsAt <= context.now.plus(context.planningHorizon)))
            val mayNotify = effective != EventStatus.UNKNOWN && when (effective) {
                EventStatus.RESOLVED -> old?.wasNotified == true
                else -> context.monitoringActive && relevant
            }
            val kind = when {
                effective == EventStatus.RESOLVED -> NotificationKind.RESOLVED
                old?.wasNotified == true -> NotificationKind.CHANGE
                effective == EventStatus.PLANNED -> NotificationKind.PLANNED
                else -> NotificationKind.ACUTE
            }
            if (mayNotify && kind in context.enabledNotificationKinds && fingerprint != old?.notifiedFingerprint) {
                notifications += NotificationCommand(key.notificationTag, kind, listOf(key),
                    when {
                        kind == NotificationKind.RESOLVED -> "Entwarnung · ${event.lines.joinToString { it.name }}"
                        derivedActivation && old?.activationDerivedFromSchedule != true -> "Beginn laut Planung · ${event.title}"
                        else -> event.title
                    },
                    if (kind == NotificationKind.RESOLVED) "Aufhebung durch die Quelle bestätigt." else buildString {
                        append(event.section ?: event.description)
                        event.replacement?.let { append(" · ").append(it.description) }
                    })
                if (old?.wasNotified != true) newlyNotified += key
                tracked = tracked.copy(notifiedFingerprint = fingerprint, wasNotified = true)
            }
            entries[key] = tracked
        }
        if (context.summarizeInitial && NotificationKind.SUMMARY in context.enabledNotificationKinds && state.events.values.none { it.wasNotified } && newlyNotified.size > 1) {
            val keys = newlyNotified.sortedWith(compareBy({ it.provider }, { it.id }))
            notifications.removeAll { it.keys.size == 1 && it.keys.first() in keys }
            notifications += NotificationCommand("initial-summary", NotificationKind.SUMMARY, keys,
                "${keys.size} relevante Einschränkungen", keys.joinToString("\n") { entries.getValue(it).event.title })
        }
        return TrackingOutcome(TrackingState(entries), notifications)
    }

    /** Include open previously announced events even when location changes or monitoring is paused. */
    fun request(state: TrackingState, context: TrackingContext): ProviderRequest = ProviderRequest(
        lines = if (context.monitoringActive) context.relevantLines else emptySet(), from = context.now,
        until = context.now.plus(context.planningHorizon),
        trackedOpenEvents = state.events.values.filter { it.wasNotified && it.status != EventStatus.RESOLVED }
            .map { it.event.key }.toSet(),
    )
}
