package de.berlin.transitwatch.data

import android.util.Base64
import de.berlin.transitwatch.core.*
import java.time.Instant
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class PreviewMessage(val title: String, val body: String, val category: String)

data class DemoUiState(
    val loading: Boolean = true,
    val events: List<TrackedEvent> = emptyList(),
    val lines: Set<LineId> = emptySet(),
    val checkedAt: Instant? = null,
    val providerStatus: String = "Noch kein Testlauf",
    val previews: List<PreviewMessage> = emptyList(),
    val decision: String = "Testdaten manuell laden, um die Verarbeitung zu prüfen.",
    val logs: List<String> = emptyList(),
    val error: String? = null,
)

/** Offline demonstration only. No requests, GPS access or operating-system notifications. */
class DemoRepository(private val dao: WatchDao) {
    private val lock = Mutex()
    private var tracking = TrackingState()
    private var fixtureAnchor = Instant.now()
    private val mutable = MutableStateFlow(DemoUiState())
    val state: StateFlow<DemoUiState> = mutable
    private val engine = TrackingEngine()

    // Small synthetic station catalogue, deliberately not a production Berlin inventory.
    private val fixedPoint = GeoPoint(52.5200, 13.4050)
    private val demoStations = listOf(
        Station("demo-a", "DEMO · Station A", GeoPoint(52.5210, 13.4060), FixtureProvider.demoLines),
        Station("demo-b", "DEMO · Ferne Station", GeoPoint(52.6500, 13.7000), setOf(FixtureProvider.S5)),
        Station("demo-c", "DEMO · Busstation", fixedPoint, setOf(LineId("vbb", TransitMode.BUS, "100"))),
    )
    private val lines = LineSelector.select(fixedPoint, demoStations)

    suspend fun load() = lock.withLock {
        try {
            val events = dao.events().flatMap { entity ->
                val decoded = TrackingStateCodec.decode(Base64.decode(entity.payload, Base64.NO_WRAP)).events
                require(decoded.keys == setOf(EventKey(entity.providerId, entity.providerEventId))) { "Ungültige Ereigniszuordnung" }
                decoded.entries
            }.associate { it.toPair() }
            val ledger = dao.ledger().associate { it.eventKey to it.fingerprint }
            events.forEach { (key, tracked) ->
                require(tracked.notifiedFingerprint == ledger[key.notificationTag]) { "Inkonsistenter Vorschauverlauf" }
            }
            val restoredAt = Instant.now()
            tracking = TrackingState(events.mapValues { (_, tracked) ->
                if (tracked.status != EventStatus.RESOLVED &&
                    (tracked.lastSeenAt > restoredAt || Duration.between(tracked.lastSeenAt, restoredAt) > Duration.ofMinutes(30))) {
                    tracked.copy(freshness = Freshness.STALE)
                } else tracked
            })
            val meta = dao.demoState()?.let { JSONObject(it.payload) }
            require(meta == null || meta.optInt("version") == 1) { "Unbekannte Version des Testzustands" }
            fixtureAnchor = meta?.optString("fixtureAnchor")?.takeIf(String::isNotBlank)?.let(Instant::parse) ?: fixtureAnchor
            val previous = mutable.value
            mutable.value = previous.copy(
                loading = false,
                events = orderedEvents(),
                lines = lines,
                checkedAt = meta?.optString("checkedAt")?.takeIf(String::isNotBlank)?.let(Instant::parse),
                providerStatus = meta?.optString("providerStatus") ?: previous.providerStatus,
                decision = meta?.optString("decision") ?: previous.decision,
                previews = meta?.optJSONArray("previews")?.let { values ->
                    (0 until values.length()).map { index ->
                        val item = values.getJSONObject(index)
                        PreviewMessage(item.getString("title"), item.getString("body"), item.getString("category"))
                    }
                } ?: emptyList(),
                logs = meta?.optJSONArray("logs")?.let { values ->
                    (0 until values.length()).map(values::getString)
                } ?: emptyList(),
            )
        } catch (error: Exception) {
            mutable.value = mutable.value.copy(loading = false, error = "Testzustand konnte nicht geladen werden. ${error.javaClass.simpleName}")
        }
    }

    suspend fun run(scenario: FixtureScenario, categories: PreviewCategories) = lock.withLock {
        if (mutable.value.error != null) return@withLock
        mutable.value = mutable.value.copy(loading = true)
        try {
            val now = Instant.now()
            val result = FixtureProvider(scenario, fixtureAnchor).snapshot(now)
            val enabledKinds = buildSet {
                if (categories.acute) add(NotificationKind.ACUTE)
                if (categories.planned) add(NotificationKind.PLANNED)
                if (categories.changes) add(NotificationKind.CHANGE)
                if (categories.resolution) add(NotificationKind.RESOLVED)
                add(NotificationKind.SUMMARY)
            }
            val outcome = engine.reduce(tracking, result, TrackingContext(now, lines, enabledNotificationKinds = enabledKinds))
            val messages = outcome.notifications.map { PreviewMessage(it.title, it.body, it.kind.name) }
            val providerStatus = when (result) {
                is ProviderResult.Success -> if (scenario == FixtureScenario.STALE) "Veraltete Testdaten" else "Fixture erfolgreich geladen"
                is ProviderResult.Failure -> "Simulierter Ausfall — vorhandene Meldungen bleiben offen"
                is ProviderResult.Unavailable -> "Quelle nicht verfügbar"
            }
            val decision = if (messages.isEmpty()) "Keine neue Benachrichtigungsvorschau." else "${messages.size} neue Benachrichtigungsvorschau(en)."
            val newState = mutable.value.copy(
                loading = false,
                events = orderedEvents(outcome.state),
                checkedAt = now,
                providerStatus = providerStatus,
                previews = if (messages.isEmpty()) mutable.value.previews else messages,
                decision = decision,
                logs = (listOf("$now · ${scenario.name} · ${messages.size} Vorschau(en)") + mutable.value.logs).take(20),
            )
            persist(outcome.state, newState)
            tracking = outcome.state
            mutable.value = newState
        } catch (error: Exception) {
            mutable.value = mutable.value.copy(loading = false, error = "Testlauf nicht gespeichert. ${error.javaClass.simpleName}")
        }
    }

    suspend fun reset() = lock.withLock {
        try {
            val fresh = DemoUiState(loading = false, lines = lines, decision = "Testzustand zurückgesetzt.")
            fixtureAnchor = Instant.now()
            persist(TrackingState(), fresh)
            tracking = TrackingState()
            mutable.value = fresh
        } catch (error: Exception) {
            mutable.value = mutable.value.copy(loading = false, error = "Zurücksetzen fehlgeschlagen. ${error.javaClass.simpleName}")
        }
    }

    private fun orderedEvents(state: TrackingState = tracking) = state.events.values.sortedBy { it.event.key.id }

    private suspend fun persist(state: TrackingState, ui: DemoUiState) {
        val eventRows = state.events.map { (key, value) ->
            EventEntity(key.provider, key.id, Base64.encodeToString(TrackingStateCodec.encode(TrackingState(mapOf(key to value))), Base64.NO_WRAP))
        }
        val ledgerRows = state.events.mapNotNull { (key, value) ->
            value.notifiedFingerprint?.let { NotificationLedgerEntity(key.notificationTag, it) }
        }
        val previews = JSONArray().apply {
            ui.previews.forEach { item -> put(JSONObject().put("title", item.title).put("body", item.body).put("category", item.category)) }
        }
        val meta = JSONObject()
            .put("version", 1)
            .put("fixtureAnchor", fixtureAnchor.toString())
            .put("checkedAt", ui.checkedAt?.toString() ?: "")
            .put("providerStatus", ui.providerStatus)
            .put("decision", ui.decision)
            .put("previews", previews)
            .put("logs", JSONArray(ui.logs))
        dao.replaceSnapshot(eventRows, ledgerRows, DemoStateEntity(payload = meta.toString()))
    }
}
