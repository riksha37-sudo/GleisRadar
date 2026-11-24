package de.berlin.transitwatch.core

import java.time.Duration
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TrackingEngineTest {
    private val now = Instant.parse("2026-11-17T12:00:00Z")
    private val line = LineId("vbb", TransitMode.S_BAHN, "S5")
    private val engine = TrackingEngine()
    private fun event(time: Instant = now, key: EventKey = EventKey("verified", "one")) = Disruption(
        key, setOf(line), EventType.DISRUPTION, "S5 unterbrochen", "Testmeldung", section = "Weit entfernter Abschnitt",
        sourceUpdatedAt = time, expectedEndAt = now.plusSeconds(60),
    )
    private fun batch(events: List<Disruption>, at: Instant = now, authority: Boolean = true, complete: Completeness = Completeness.COMPLETE) =
        ProviderResult.Success("verified", at, at, events, complete, authority)
    private fun context(at: Instant = now, active: Boolean = true, lines: Set<LineId> = setOf(line)) = TrackingContext(at, lines, active)
    private fun initial() = engine.reduce(TrackingState(), batch(listOf(event())), context())

    @Test fun `new event warns exactly once even after persistent restart`() {
        val first = initial()
        assertEquals(NotificationKind.ACUTE, first.notifications.single().kind)
        val restored = TrackingStateCodec.decode(TrackingStateCodec.encode(first.state))
        val repeated = engine.reduce(restored, batch(listOf(event()), now.plusSeconds(60)), context(now.plusSeconds(60)))
        assertTrue(repeated.notifications.isEmpty())
        assertEquals(first.state.events.keys, repeated.state.events.keys)
    }

    @Test fun `metadata change does not alert while replacement information does`() {
        val first = initial()
        val metadata = event(now.plusSeconds(20)).copy(sourceUrl = "https://example.org/message")
        val second = engine.reduce(first.state, batch(listOf(metadata), now.plusSeconds(20)), context(now.plusSeconds(20)))
        assertTrue(second.notifications.isEmpty())
        val replacement = metadata.copy(sourceUpdatedAt = now.plusSeconds(40), replacement = ReplacementService("Bus", "SEV verfügbar"))
        val third = engine.reduce(second.state, batch(listOf(replacement), now.plusSeconds(40)), context(now.plusSeconds(40)))
        assertEquals(NotificationKind.CHANGE, third.notifications.single().kind)
        assertEquals(first.notifications.single().tag, third.notifications.single().tag)
        assertEquals(EventStatus.UPDATED, third.state.events.getValue(replacement.key).status)
    }

    @Test fun `forecast passing does not resolve ongoing interruption`() {
        val later = now.plusSeconds(3600)
        val outcome = engine.reduce(initial().state, batch(listOf(event()), later), context(later))
        assertTrue(outcome.notifications.isEmpty())
        assertEquals(EventStatus.ACTIVE, outcome.state.events.getValue(event().key).status)
    }

    @Test fun `complete empty and partial empty feeds never imply resolution`() {
        for (completeness in Completeness.entries) {
            val outcome = engine.reduce(initial().state, batch(emptyList(), now.plusSeconds(15), complete = completeness), context(now.plusSeconds(15)))
            assertTrue(outcome.notifications.isEmpty())
            assertEquals(EventStatus.ACTIVE, outcome.state.events.getValue(event().key).status)
            assertNotEquals(Freshness.FRESH, outcome.state.events.getValue(event().key).freshness)
        }
    }

    @Test fun `failure and unconfigured provider retain open event without all clear`() {
        listOf(
            ProviderResult.Failure("verified", now.plusSeconds(30), "timeout"),
            ProviderResult.Unavailable("verified", now.plusSeconds(30), "not configured"),
        ).forEach { result ->
            val outcome = engine.reduce(initial().state, result, context(now.plusSeconds(30)))
            assertTrue(outcome.notifications.isEmpty())
            assertEquals(Freshness.STALE, outcome.state.events.getValue(event().key).freshness)
            assertEquals(EventStatus.ACTIVE, outcome.state.events.getValue(event().key).status)
        }
    }

    @Test fun `explicit trusted resolution emits one all clear while paused and far away`() {
        val later = now.plusSeconds(60)
        val ended = event(later).copy(status = EventStatus.RESOLVED,
            resolution = ResolutionEvidence("verified", later, "provider:confirmed-end"))
        val paused = context(later, active = false, lines = emptySet())
        val second = engine.reduce(initial().state, batch(listOf(ended), later), paused)
        assertEquals(NotificationKind.RESOLVED, second.notifications.single().kind)
        assertEquals(EventStatus.RESOLVED, second.state.events.getValue(ended.key).status)
        val repeated = engine.reduce(second.state, batch(listOf(ended), later.plusSeconds(20)), context(later.plusSeconds(20)))
        assertTrue(repeated.notifications.isEmpty())
    }

    @Test fun `resolution requires explicit evidence matching trusted adapter`() {
        val later = now.plusSeconds(60)
        val variants = listOf(
            event(later).copy(status = EventStatus.RESOLVED),
            event(later).copy(status = EventStatus.RESOLVED, resolution = ResolutionEvidence("other", later, "confirmed")),
            event(later).copy(status = EventStatus.RESOLVED, resolution = ResolutionEvidence("verified", later, "")),
            event(later).copy(status = EventStatus.RESOLVED, resolution = ResolutionEvidence("verified", later.plusSeconds(301), "future")),
        )
        variants.forEach { ended ->
            val outcome = engine.reduce(initial().state, batch(listOf(ended), later), context(later))
            assertTrue(outcome.notifications.isEmpty())
            assertEquals(EventStatus.UNKNOWN, outcome.state.events.getValue(ended.key).status)
        }
        val explicit = event(later).copy(status = EventStatus.RESOLVED, resolution = ResolutionEvidence("verified", later, "confirmed"))
        val untrusted = engine.reduce(initial().state, batch(listOf(explicit), later, authority = false), context(later))
        assertTrue(untrusted.notifications.isEmpty())
        assertEquals(EventStatus.UNKNOWN, untrusted.state.events.getValue(explicit.key).status)
    }

    @Test fun `unknown old closed event does not produce all clear`() {
        val ended = event().copy(status = EventStatus.RESOLVED, resolution = ResolutionEvidence("verified", now, "confirmed"))
        val outcome = engine.reduce(TrackingState(), batch(listOf(ended)), context())
        assertEquals(EventStatus.RESOLVED, outcome.state.events.getValue(ended.key).status)
        assertTrue(outcome.notifications.isEmpty())
    }

    @Test fun `terminal identity never reopens from later active payload`() {
        val ended = event(now.plusSeconds(60)).copy(status = EventStatus.RESOLVED,
            resolution = ResolutionEvidence("verified", now.plusSeconds(60), "confirmed"))
        val resolved = engine.reduce(initial().state, batch(listOf(ended), now.plusSeconds(60)), context(now.plusSeconds(60)))
        val regression = event(now.plusSeconds(120))
        val outcome = engine.reduce(resolved.state, batch(listOf(regression), now.plusSeconds(120)), context(now.plusSeconds(120)))
        assertEquals(EventStatus.RESOLVED, outcome.state.events.getValue(ended.key).status)
        assertTrue(outcome.notifications.isEmpty())
    }

    @Test fun `stale feed cannot resolve or add warnings`() {
        val later = now.plusSeconds(7200)
        val ended = event().copy(status = EventStatus.RESOLVED, resolution = ResolutionEvidence("verified", now, "confirmed"))
        val stale = batch(listOf(ended), later).copy(generatedAt = now)
        val outcome = engine.reduce(initial().state, stale, context(later))
        assertTrue(outcome.notifications.isEmpty())
        assertEquals(EventStatus.ACTIVE, outcome.state.events.getValue(ended.key).status)
        assertEquals(Freshness.STALE, outcome.state.events.getValue(ended.key).freshness)
        assertTrue(engine.reduce(TrackingState(), stale, context(later)).state.events.isEmpty())
    }

    @Test fun `older source revision cannot overwrite a current payload`() {
        val current = event(now.plusSeconds(60)).copy(title = "Neue Information")
        val first = engine.reduce(TrackingState(), batch(listOf(current), now.plusSeconds(60)), context(now.plusSeconds(60)))
        val outcome = engine.reduce(first.state, batch(listOf(event()), now.plusSeconds(120)), context(now.plusSeconds(120)))
        assertEquals("Neue Information", outcome.state.events.getValue(current.key).event.title)
        assertTrue(outcome.notifications.isEmpty())
    }

    @Test fun `conflicting equal time versions have no arbitrary winner`() {
        val conflict = event().copy(title = "Gegenteilige Information")
        val outcome = engine.reduce(initial().state, batch(listOf(event(), conflict)), context())
        assertEquals("S5 unterbrochen", outcome.state.events.getValue(event().key).event.title)
        assertEquals(Freshness.UNKNOWN, outcome.state.events.getValue(event().key).freshness)
        assertTrue(outcome.notifications.isEmpty())
    }

    @Test fun `equal time conflicting resolution evidence is independent of input order`() {
        val at = now.plusSeconds(60)
        val withoutProof = event(at).copy(status = EventStatus.RESOLVED)
        val withProof = withoutProof.copy(resolution = ResolutionEvidence("verified", at, "confirmed"))
        listOf(listOf(withoutProof, withProof), listOf(withProof, withoutProof)).forEach { versions ->
            val outcome = engine.reduce(initial().state, batch(versions, at), context(at))
            assertEquals(EventStatus.ACTIVE, outcome.state.events.getValue(event().key).status)
            assertEquals(Freshness.UNKNOWN, outcome.state.events.getValue(event().key).freshness)
            assertTrue(outcome.notifications.isEmpty())
        }
    }

    @Test fun `providers with same id remain separate and one cannot close another`() {
        val other = event(now.plusSeconds(20), EventKey("second", "one")).copy(status = EventStatus.RESOLVED,
            resolution = ResolutionEvidence("second", now.plusSeconds(20), "confirmed"))
        val outcome = engine.reduce(initial().state, ProviderResult.Success("second", now.plusSeconds(20), now.plusSeconds(20), listOf(other), supportsExplicitResolution = true), context(now.plusSeconds(20)))
        assertEquals(2, outcome.state.events.size)
        assertEquals(EventStatus.ACTIVE, outcome.state.events.getValue(event().key).status)
        assertTrue(outcome.notifications.isEmpty())
        assertNotEquals(event().key.notificationTag, other.key.notificationTag)
    }

    @Test fun `provider batch cannot impersonate another provider identity`() {
        val outcome = engine.reduce(TrackingState(), batch(listOf(event(key = EventKey("injected", "one")))), context())
        assertTrue(outcome.state.events.isEmpty())
    }

    @Test fun `planning horizon is14 days and beginning causes one material update`() {
        val planned = event().copy(status = EventStatus.PLANNED, startsAt = now.plus(Duration.ofDays(14)))
        val first = engine.reduce(TrackingState(), batch(listOf(planned)), context())
        assertEquals(NotificationKind.PLANNED, first.notifications.single().kind)
        val distant = planned.copy(key = EventKey("verified", "later"), startsAt = now.plus(Duration.ofDays(14)).plusSeconds(1))
        assertTrue(engine.reduce(TrackingState(), batch(listOf(distant)), context()).notifications.isEmpty())
        val begins = planned.startsAt!!
        val started = engine.reduce(first.state, batch(listOf(planned), begins), context(begins))
        assertEquals(NotificationKind.CHANGE, started.notifications.single().kind)
        assertTrue(started.notifications.single().title.startsWith("Beginn laut Planung"))
        assertTrue(started.state.events.getValue(planned.key).activationDerivedFromSchedule)
        assertEquals(EventStatus.UPDATED, started.state.events.getValue(planned.key).status)
        assertTrue(engine.reduce(started.state, batch(listOf(planned), begins.plusSeconds(30)), context(begins.plusSeconds(30))).notifications.isEmpty())
    }

    @Test fun `initial relevant events aggregate once and distant segment remains relevant`() {
        val second = event(key = EventKey("verified", "two"))
        val first = engine.reduce(TrackingState(), batch(listOf(event(), second)), context())
        assertEquals(NotificationKind.SUMMARY, first.notifications.single().kind)
        assertEquals(2, first.notifications.single().keys.size)
        assertTrue(engine.reduce(first.state, batch(listOf(event(), second), now.plusSeconds(60)), context(now.plusSeconds(60))).notifications.isEmpty())
    }

    @Test fun `new events stay silent in pause while open announced events stay queried`() {
        val first = initial()
        val paused = context(now.plusSeconds(60), active = false, lines = emptySet())
        val request = engine.request(first.state, paused)
        assertTrue(request.lines.isEmpty())
        assertEquals(setOf(event().key), request.trackedOpenEvents)
        val second = event(now.plusSeconds(60), EventKey("verified", "two"))
        val outcome = engine.reduce(first.state, batch(listOf(second), now.plusSeconds(60)), paused)
        assertTrue(outcome.notifications.isEmpty())
    }

    @Test fun `fixture does not change semantic fields with fetched time`() {
        val fixture = FixtureProvider(anchor = now)
        val first = fixture.snapshot(now) as ProviderResult.Success
        val second = fixture.snapshot(now.plusSeconds(60)) as ProviderResult.Success
        assertEquals(first.events.map { it.fingerprint() }, second.events.map { it.fingerprint() })
        val initial = engine.reduce(TrackingState(), first, TrackingContext(now, FixtureProvider.demoLines))
        val repeated = engine.reduce(initial.state, second, TrackingContext(now.plusSeconds(60), FixtureProvider.demoLines))
        assertTrue(repeated.notifications.isEmpty())
    }

    @Test fun `disabled new categories do not mark events shown and reenable can announce once`() {
        val fixture = FixtureProvider(anchor = now)
        val suppressedContext = TrackingContext(now, FixtureProvider.demoLines,
            enabledNotificationKinds = setOf(NotificationKind.CHANGE, NotificationKind.RESOLVED, NotificationKind.SUMMARY))
        val suppressed = engine.reduce(TrackingState(), fixture.snapshot(now), suppressedContext)
        assertTrue(suppressed.notifications.isEmpty())
        assertTrue(suppressed.state.events.values.none { it.wasNotified || it.notifiedFingerprint != null })
        val later = now.plusSeconds(60)
        val enabled = engine.reduce(suppressed.state, fixture.snapshot(later), suppressedContext.copy(now = later,
            enabledNotificationKinds = suppressedContext.enabledNotificationKinds + NotificationKind.ACUTE))
        assertEquals(NotificationKind.ACUTE, enabled.notifications.single().kind)
        assertEquals(1, enabled.state.events.values.count { it.wasNotified })
        val repeated = engine.reduce(enabled.state, fixture.snapshot(later.plusSeconds(60)), suppressedContext.copy(now = later.plusSeconds(60),
            enabledNotificationKinds = suppressedContext.enabledNotificationKinds + NotificationKind.ACUTE))
        assertTrue(repeated.notifications.isEmpty())
    }

    @Test fun `resolution of a category suppressed warning never creates unseen all clear`() {
        val suppressed = engine.reduce(TrackingState(), batch(listOf(event())),
            context().copy(enabledNotificationKinds = setOf(NotificationKind.RESOLVED)))
        val later = now.plusSeconds(60)
        val ended = event(later).copy(status = EventStatus.RESOLVED,
            resolution = ResolutionEvidence("verified", later, "confirmed"))
        val outcome = engine.reduce(suppressed.state, batch(listOf(ended), later), context(later))
        assertTrue(outcome.notifications.isEmpty())
        assertEquals(EventStatus.RESOLVED, outcome.state.events.getValue(ended.key).status)
    }

    @Test fun `initial summary contains only categories actually enabled`() {
        val fixture = FixtureProvider(anchor = now)
        val outcome = engine.reduce(TrackingState(), fixture.snapshot(now), TrackingContext(now, FixtureProvider.demoLines,
            enabledNotificationKinds = setOf(NotificationKind.ACUTE, NotificationKind.SUMMARY)))
        assertEquals(NotificationKind.ACUTE, outcome.notifications.single().kind)
        assertEquals(setOf(FixtureProvider.S5), outcome.state.events.getValue(outcome.notifications.single().keys.single()).event.lines)
    }

    @Test fun `inconsistent or future transport times cannot create current events`() {
        val invalidBatches = listOf(
            batch(listOf(event())).copy(fetchedAt = now.minusSeconds(3600)),
            batch(listOf(event())).copy(generatedAt = now.plusSeconds(301)),
            batch(listOf(event())).copy(fetchedAt = now.plusSeconds(301)),
            batch(listOf(event(now.plusSeconds(301)))),
        )
        invalidBatches.forEach { invalid ->
            val outcome = engine.reduce(TrackingState(), invalid, context())
            assertTrue(outcome.state.events.isEmpty())
            assertTrue(outcome.notifications.isEmpty())
        }
    }

    @Test fun `fresh feed can carry an old but still active published event`() {
        val publishedYesterday = event(now.minusSeconds(86400))
        val outcome = engine.reduce(TrackingState(), batch(listOf(publishedYesterday)), context())
        assertEquals(NotificationKind.ACUTE, outcome.notifications.single().kind)
        assertEquals(Freshness.FRESH, outcome.state.events.getValue(publishedYesterday.key).freshness)
    }

    @Test fun `persistence keeps all fields and rejects corrupt data`() {
        val elaborate = event().copy(stations = listOf("A", "B"), replacement = ReplacementService("Bus", "SEV"),
            sourceUrl = "https://example.org/ä", startsAt = now.minusSeconds(60),
            resolution = ResolutionEvidence("verified", now, "reference"))
        val state = engine.reduce(TrackingState(), batch(listOf(elaborate)), context()).state
        val bytes = TrackingStateCodec.encode(state)
        assertEquals(state, TrackingStateCodec.decode(bytes))
        assertThrows(IllegalArgumentException::class.java) { TrackingStateCodec.decode(bytes + byteArrayOf(0)) }
        assertThrows(IllegalArgumentException::class.java) { TrackingStateCodec.decode(bytes.copyOf().also { it[0] = 0 }) }
        assertThrows(Exception::class.java) { TrackingStateCodec.decode(bytes.copyOf(8)) }
        val malformedUtf8 = bytes.copyOf()
        val nonAscii = malformedUtf8.indexOfFirst { it.toInt() and 255 == 0xc3 }
        assertTrue(nonAscii >= 0)
        malformedUtf8[nonAscii] = 0xff.toByte()
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { TrackingStateCodec.decode(malformedUtf8) }
    }
}
