package de.berlin.transitwatch.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.berlin.transitwatch.R
import de.berlin.transitwatch.core.*
import de.berlin.transitwatch.data.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BerlinTime = DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm:ss", Locale.GERMAN).withZone(BerlinZone)

private val LightColors = lightColorScheme(
    primary = Color(0xFF075D4D), onPrimary = Color.White,
    primaryContainer = Color(0xFFC1F0DF), onPrimaryContainer = Color(0xFF103C31),
    secondaryContainer = Color(0xFFDEE8FC), onSecondaryContainer = Color(0xFF243956),
    surface = Color(0xFFFAFCF9), onSurface = Color(0xFF172520),
    surfaceContainer = Color(0xFFEDF3EF), surfaceVariant = Color(0xFFE0E9E2),
    onSurfaceVariant = Color(0xFF40554B), outline = Color(0xFF6D8177),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DE0CD), onPrimary = Color(0xFF004437),
    primaryContainer = Color(0xFF184C41), onPrimaryContainer = Color(0xFFC1F0DF),
    secondaryContainer = Color(0xFF2D4160), onSecondaryContainer = Color(0xFFDDE8FF),
    surface = Color(0xFF101B17), onSurface = Color(0xFFDFEAE3),
    surfaceContainer = Color(0xFF1B2822), surfaceVariant = Color(0xFF263830),
    onSurfaceVariant = Color(0xFFB9CEC2), outline = Color(0xFF869E91),
)

private enum class Tab(val label: String, val title: String, val icon: Int) {
    OVERVIEW("Übersicht", "Linienwache Berlin", R.drawable.ic_overview),
    PLACES("Pausen", "Pausenorte", R.drawable.ic_place),
    SETTINGS("Optionen", "Einstellungen", R.drawable.ic_settings),
    DIAGNOSTICS("Diagnose", "Diagnose", R.drawable.ic_diagnosis),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchApp(vm: WatchViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val overview by vm.overview.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val settingsError by vm.settingsError.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.OVERVIEW) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val openLink: (String) -> Unit = { url ->
        try {
            uri.openUri(url)
        } catch (_: IllegalArgumentException) {
            scope.launch { snackbar.showSnackbar("Kein Browser zum Öffnen des Links verfügbar.") }
        }
    }
    LifecycleResumeEffect(vm) {
        val clock = scope.launch {
            while (true) {
                vm.updateTime()
                delay(30_000)
            }
        }
        onPauseOrDispose { clock.cancel() }
    }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
        val scrollStateHolder = rememberSaveableStateHolder()
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(tab.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(item.label, maxLines = 1) },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.surface,
        ) { inset ->
            Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.TopCenter) {
                scrollStateHolder.SaveableStateProvider(tab.name) {
                    Column(
                        Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (tab) {
                            Tab.OVERVIEW -> Dashboard(overview, vm::refreshDepartures, openLink)
                            Tab.PLACES -> PausePlaces()
                            Tab.SETTINGS -> {
                                settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                Settings(categories, vm::setCategory)
                            }
                            Tab.DIAGNOSTICS -> Diagnostics(state, vm::run, vm::reset)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(tracked: TrackedEvent) {
    val event = tracked.event
    val status = if (tracked.activationDerivedFromSchedule && tracked.status in setOf(EventStatus.ACTIVE, EventStatus.UPDATED)) {
        "Beginn laut Zeitplan — unbestätigt"
    } else when (tracked.status) {
        EventStatus.PLANNED -> "Geplant"
        EventStatus.ACTIVE -> "Aktiv"
        EventStatus.UPDATED -> "Geändert"
        EventStatus.RESOLVED -> "Bestätigt aufgehoben"
        EventStatus.UNKNOWN -> "Status unklar"
    }
    Panel(event.title) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(event.lines.joinToString { it.name }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("· $status", style = MaterialTheme.typography.bodyMedium)
        }
        if (tracked.freshness != Freshness.FRESH) {
            Text(when (tracked.freshness) {
                Freshness.MISSING -> "In letzter Antwort nicht enthalten — keine Entwarnung"
                Freshness.STALE -> "Veraltet — keine Bestätigung des aktuellen Betriebs"
                else -> "Aktualität unbekannt"
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        event.section?.let { Text(it, fontWeight = FontWeight.Medium) }
        Text(event.description, style = MaterialTheme.typography.bodyMedium)
        event.startsAt?.let { Text("Beginn: ${displayTime(it)}", style = MaterialTheme.typography.bodySmall) }
        event.expectedEndAt?.let { Text("Voraussichtlich bis: ${displayTime(it)}", style = MaterialTheme.typography.bodySmall) }
        event.replacement?.let { Text("${it.mode}: ${it.description}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
        if (tracked.status == EventStatus.RESOLVED) Text("Aufhebung durch expliziten Testnachweis bestätigt.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PausePlaces() {
    Panel("Automatische Pausen") {
        Text("Die Kartenauswahl für Pausenorte ist in Vorbereitung.")
        Text("Vorgesehener Standardradius: 50 Meter.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Settings(categories: PreviewCategories, setCategory: (PreviewCategory, Boolean) -> Unit) {
    Panel("Benachrichtigungsvorschau") {
        Text("Diese Auswahl gilt für kommende Testläufe. Echte Android-Benachrichtigungen folgen mit dem Hintergrundbetrieb.", style = MaterialTheme.typography.bodyMedium)
        CategoryRow("Akute Störungen", categories.acute) { setCategory(PreviewCategory.ACUTE, it) }
        CategoryRow("Geplante Bauarbeiten", categories.planned) { setCategory(PreviewCategory.PLANNED, it) }
        CategoryRow("Wesentliche Änderungen", categories.changes) { setCategory(PreviewCategory.CHANGES, it) }
        CategoryRow("Bestätigte Entwarnungen", categories.resolution) { setCategory(PreviewCategory.RESOLUTION, it) }
    }
    Panel("Geplanter Hintergrundbetrieb") {
        Text("Standard: 15 Minuten außerhalb der Pausenorte", fontWeight = FontWeight.Medium)
        Text("Noch nicht aktiviert. Android kann Prüfungen im Hintergrund verzögern; eine Echtzeitüberwachung wird nicht garantiert.")
    }
    Panel("Berechtigungen & Datenschutz") {
        Text("Die App fragt bei Bedarf den festen Bahnhof Alexanderplatz ab. Sie erfasst keinen Gerätestandort.")
        Text("Testzustand und Einstellungen bleiben im lokalen App-Speicher.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CategoryRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Diagnostics(state: DemoUiState, run: (FixtureScenario) -> Unit, reset: () -> Unit) {
    Panel("Demo und Testdaten") {
        Text("Simulierte Meldungen für die Prüfung der Warnlogik.")
        Text(state.providerStatus, style = MaterialTheme.typography.bodyMedium)
        Text("Letzter Testaufruf: ${displayTime(state.checkedAt)}", style = MaterialTheme.typography.bodySmall)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    Panel("Ereignisverarbeitung prüfen") {
        Text("Beginne mit Testmeldungen. Wiederholte Abfragen sollen keine neue Vorschau erzeugen. Eine verschwundene Meldung bleibt offen.")
        listOf(
            FixtureScenario.NORMAL to "Testmeldungen laden",
            FixtureScenario.CHANGED to "Ersatzbus ergänzen",
            FixtureScenario.MISSING to "Meldung fehlt",
            FixtureScenario.FAILURE to "API-Ausfall simulieren",
            FixtureScenario.STALE to "Veraltete Daten",
            FixtureScenario.RESOLVED to "Explizite Aufhebung simulieren",
        ).forEach { (scenario, label) ->
            OutlinedButton(onClick = { run(scenario) }, enabled = !state.loading && state.error == null, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
        Text("Nach einer Aufhebung bleibt dieselbe Ereignis-ID abgeschlossen. Für einen neuen Durchlauf den Testzustand zurücksetzen.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = reset, enabled = !state.loading) { Text("Testzustand zurücksetzen") }
    }
    if (state.events.isNotEmpty()) {
        Text("Simulierte Ereignisse", style = MaterialTheme.typography.titleLarge)
        state.events.forEach { EventCard(it) }
    }
    if (state.previews.isNotEmpty()) Panel("Letzte Benachrichtigungsvorschau") {
        state.previews.forEach { message ->
            Text(message.title, fontWeight = FontWeight.SemiBold)
            Text(message.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Panel("Lokal gespeicherter Verlauf") {
        if (state.logs.isEmpty()) Text("Noch keine Testläufe.")
        state.logs.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text("Maximal 20 Diagnosezeilen, ohne Gerätestandort. Ereignisse und Vorschauverlauf bleiben nach einem App-Neustart erhalten.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

private fun displayTime(instant: Instant?): String = instant?.let(BerlinTime::format) ?: "Noch nicht geprüft"
