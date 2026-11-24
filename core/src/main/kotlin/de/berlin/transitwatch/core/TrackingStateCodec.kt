package de.berlin.transitwatch.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Instant
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Explicit versioned local persistence format, without reflective Java object deserialization.
 * Keep the Room state and notification outbox in one transaction. Unknown/corrupt versions must
 * surface a diagnostic; never silently clear the ledger and send every warning again.
 */
object TrackingStateCodec {
    private const val MAGIC = 0x42545731
    private const val VERSION = 1
    private const val MAX_ENTRIES = 10000
    private const val MAX_TEXT_BYTES = 1_048_576
    private const val MAX_STATE_BYTES = 64 * 1_048_576

    fun encode(state: TrackingState): ByteArray {
        require(state.events.size <= MAX_ENTRIES)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(state.events.size)
            state.events.entries.sortedWith(compareBy({ it.key.provider }, { it.key.id })).forEach { (key, tracked) ->
                require(key == tracked.event.key)
                val event = tracked.event
                out.text(key.provider); out.text(key.id)
                require(event.lines.size <= MAX_ENTRIES && event.stations.size <= MAX_ENTRIES)
                out.writeInt(event.lines.size)
                event.lines.sortedWith(compareBy({ it.region }, { it.mode.name }, { it.name })).forEach {
                    out.text(it.region); out.text(it.mode.name); out.text(it.name)
                }
                out.text(event.type.name); out.text(event.title); out.text(event.description)
                out.writeInt(event.stations.size); event.stations.forEach { out.text(it) }
                out.nullable(event.section); out.nullable(event.startsAt?.toString()); out.nullable(event.expectedEndAt?.toString())
                out.text(event.sourceUpdatedAt.toString()); out.text(event.status.name)
                out.writeBoolean(event.replacement != null)
                event.replacement?.let { out.text(it.mode); out.text(it.description) }
                out.nullable(event.sourceUrl)
                out.writeBoolean(event.resolution != null)
                event.resolution?.let { out.text(it.provider); out.text(it.confirmedAt.toString()); out.text(it.reference) }
                out.text(tracked.status.name); out.text(tracked.freshness.name); out.text(tracked.lastSeenAt.toString())
                out.nullable(tracked.notifiedFingerprint); out.writeBoolean(tracked.wasNotified)
                out.writeBoolean(tracked.activationDerivedFromSchedule)
            }
        }
        return bytes.toByteArray().also { require(it.size <= MAX_STATE_BYTES) }
    }

    fun decode(bytes: ByteArray): TrackingState {
        require(bytes.size <= MAX_STATE_BYTES) { "Tracking state exceeds the storage limit" }
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Invalid tracking state" }
            require(input.readInt() == VERSION) { "Unsupported tracking state version" }
            val events = linkedMapOf<EventKey, TrackedEvent>()
            repeat(input.count()) {
                val key = EventKey(input.text(), input.text())
                require(key !in events) { "Duplicate event key in tracking state" }
                val lines = List(input.count()) { LineId(input.text(), TransitMode.valueOf(input.text()), input.text()) }.toSet()
                val type = EventType.valueOf(input.text()); val title = input.text(); val description = input.text()
                val stations = List(input.count()) { input.text() }
                val section = input.nullable(); val start = input.nullable()?.let(Instant::parse)
                val expectedEnd = input.nullable()?.let(Instant::parse); val updated = Instant.parse(input.text())
                val sourceStatus = EventStatus.valueOf(input.text())
                val replacement = if (input.readBoolean()) ReplacementService(input.text(), input.text()) else null
                val sourceUrl = input.nullable()
                val resolution = if (input.readBoolean()) ResolutionEvidence(input.text(), Instant.parse(input.text()), input.text()) else null
                val status = EventStatus.valueOf(input.text()); val freshness = Freshness.valueOf(input.text())
                val lastSeen = Instant.parse(input.text()); val fingerprint = input.nullable(); val notified = input.readBoolean()
                val derived = input.readBoolean()
                events[key] = TrackedEvent(Disruption(key, lines, type, title, description, stations, section,
                    start, expectedEnd, updated, sourceStatus, replacement, sourceUrl, resolution),
                    status, freshness, lastSeen, fingerprint, notified, derived)
            }
            require(input.available() == 0) { "Trailing bytes in tracking state" }
            return TrackingState(events)
        }
    }

    private fun DataOutputStream.text(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_TEXT_BYTES)
        writeInt(bytes.size); write(bytes)
    }
    private fun DataOutputStream.nullable(value: String?) { writeBoolean(value != null); if (value != null) text(value) }
    private fun DataInputStream.text(): String {
        val count = readInt()
        require(count in 0..MAX_TEXT_BYTES && count <= available()) { "Invalid text length" }
        val bytes = ByteArray(count).also(::readFully)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    private fun DataInputStream.nullable(): String? = if (readBoolean()) text() else null
    private fun DataInputStream.count(): Int = readInt().also { require(it in 0..MAX_ENTRIES) { "Invalid collection length" } }
}
