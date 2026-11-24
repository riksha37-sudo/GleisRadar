package de.berlin.transitwatch.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.berlin.transitwatch.R
import de.berlin.transitwatch.core.TransitMode
import de.berlin.transitwatch.data.BerlinZone
import de.berlin.transitwatch.data.DepartureNotice
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Timestamp = DateTimeFormatter.ofPattern("dd.MM. · HH:mm", Locale.GERMAN).withZone(BerlinZone)
private val Date = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)
private val PreviewAge = Duration.ofMinutes(15)

private enum class ModeFilter(val label: String, val mode: TransitMode?) {
    ALL("Alle", null), S("S-Bahn", TransitMode.S_BAHN), U("U-Bahn", TransitMode.U_BAHN)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Dashboard(state: OverviewState, refresh: () -> Unit, openLink: (String) -> Unit) {
    val result = state.preview
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Abfahrtsvorschau", style = MaterialTheme.typography.labelLarge)
            Text("Alexanderplatz", Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("S- und U-Bahn · nächste 60 Minuten", style = MaterialTheme.typography.bodyMedium)
            Button(refresh, enabled = !state.previewLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(when {
                    state.previewLoading -> "Abfahrten werden abgerufen …"
                    result == null -> "Meldungen abrufen"
                    else -> "Meldungen aktualisieren"
                })
            }
            if (state.previewLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Quelle: transport.rest (VBB) · manueller Abruf", style = MaterialTheme.typography.labelMedium)
        }
    }

    if (state.previewFailed) {
        Panel("Abruf fehlgeschlagen") {
            Text(if (result == null) "Versuche es erneut oder öffne die Betreiberseite."
            else "Die letzte erfolgreiche Stichprobe bleibt sichtbar. Der aktuelle Stand ist unbestätigt.",
                Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    if (result != null) {
        val stale = state.now < result.checkedAt || Duration.between(result.checkedAt, state.now) > PreviewAge
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Hinweise zur Stichprobe", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text("Stand: ${Timestamp.format(result.checkedAt)} · ${result.railDepartures} Bahnabfahrten",
                style = MaterialTheme.typography.bodySmall)
            if (stale) Text("Stichprobe ist veraltet.", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge)
        }

        var filter by rememberSaveable { mutableStateOf(ModeFilter.ALL) }
        if (result.notices.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeFilter.entries.forEach { mode ->
                    FilterChip(selected = filter == mode, onClick = { filter = mode }, label = { Text(mode.label) })
                }
            }
        }
        val visible = result.notices.filter { filter.mode == null || filter.mode in it.modes }
        if (visible.isEmpty()) {
            Panel("Keine Warnhinweise in dieser Auswahl") {
                Text("Die Stichprobe deckt einzelne Abfahrten ab. Der übrige Betrieb bleibt unbestätigt.")
            }
        } else {
            visible.forEach { notice -> key(notice.id) { NoticeCard(notice, state.now, stale || state.previewFailed) } }
        }
    }

    Panel("Offizielle Verkehrsmeldungen") {
        OfficialLink("S-Bahn Berlin", "https://sbahn.berlin/fahren/bauen-stoerung/", openLink)
        OfficialLink("BVG · U-Bahn", "https://www.bvg.de/de/verbindungen/stoerungsmeldungen", openLink)
    }
    LineCatalog(state, openLink)
}

@Composable
private fun NoticeCard(notice: DepartureNotice, now: Instant, uncertain: Boolean) {
    Panel(notice.title) {
        if (notice.lines.isNotEmpty()) Text(notice.lines.sorted().joinToString(" · "),
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        val status = when {
            !notice.observedInLastResponse -> "Bei letzter Stichprobe nicht gesehen · Aufhebung unbestätigt"
            notice.validUntil != null && now > notice.validUntil -> "Gültigkeit abgelaufen · Aufhebung unbestätigt"
            uncertain -> "Aktueller Stand unbestätigt"
            else -> null
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge) }
        val description = notice.description.takeUnless { it == notice.title }.orEmpty()
        if (description.isNotBlank()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            if (description.length <= 160 || expanded) Text(description, style = MaterialTheme.typography.bodyMedium)
            if (description.length > 160) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Details schließen" else "Details anzeigen") }
            }
        }
        Text("Zuletzt gesehen: ${Timestamp.format(notice.lastSeen)}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun OfficialLink(label: String, url: String, openLink: (String) -> Unit) {
    OutlinedButton(onClick = { openLink(url) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_external), contentDescription = "Im Browser öffnen", modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineCatalog(state: OverviewState, openLink: (String) -> Unit) {
    val catalog = state.catalog
    Panel("Streckennetz · Berlin-Mitte") {
        when {
            state.catalogLoading -> Text("Fahrplandaten werden geladen …")
            catalog == null -> Text("Der lokale Fahrplanindex konnte nicht gelesen werden.")
            else -> {
                val lines = remember(catalog, state.now) { catalog.berlinMitteLines(state.now) }
                if (!catalog.usableAt(state.now)) {
                    Text("Der Fahrplanindex liegt außerhalb seines Gültigkeitsfensters. Bitte aktualisieren.",
                        color = MaterialTheme.colorScheme.error)
                } else {
                    Text("${lines.size} Linien im Umkreis von 5 km um den Berliner Beispielpunkt.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        lines.sortedWith(compareBy({ it.mode }, { it.name.length }, { it.name })).forEach { line ->
                            val subway = line.mode == TransitMode.U_BAHN
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = if (subway) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (subway) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Text(line.name, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                Text("Gültig ${Date.format(catalog.validFrom)}–${Date.format(catalog.validThrough)} · importiert ${Timestamp.format(catalog.importedAt)}",
                    style = MaterialTheme.typography.bodySmall)
                Text("VBB-Fahrplandaten, bearbeitet. CC BY 4.0.", style = MaterialTheme.typography.labelMedium)
                FlowRow {
                    TextButton(onClick = { openLink("https://unternehmen.vbb.de/digitale-services/datensaetze/") }) { Text("Quelle") }
                    TextButton(onClick = { openLink("https://creativecommons.org/licenses/by/4.0/") }) { Text("Lizenz") }
                }
            }
        }
    }
}
