# Risiken und Entscheidungen

Stand: 17.11.2026. Die Anfrage an VBB wurde laut Nutzer bereits gestellt.
Ein fehlender Zugang verhindert die Live-Abnahme, nicht die Vorbereitung von
Modellen, Provider-Vertrag, Fixtures und reproduzierbaren Regeltests.

| Priorität | Risiko / Auswirkung | Vorgehen | Nachweis für Freigabe |
|---|---|---|---|
| kritisch | Eine nicht mehr gelieferte Meldung wird irrtümlich entwarnt | Kein Schluss von Abwesenheit, abgelaufenem Prognoseende oder Feed-Löschung auf Behebung. Explizite, vom jeweiligen Provider belegte Aufhebung verlangen. Sonst UNKNOWN/STALE | dokumentierter VBB-Datenvertrag und echte Rohdaten für mindestens einen vollständigen Ereignislebenszyklus |
| kritisch | API-Freigabe oder Störungsdatenumfang unvollständig | Anfrage bereits gestellt; keine erneute Anfrage versenden. Produktive Integration bis dokumentiertem Vertrag sperren, Fixture-Provider sichtbar kennzeichnen | Zugang, Lizenz, Grenzen, Pagination, Aktualität und Statussemantik bestätigt |
| hoch | 50-m-Pausenort wird verspätet oder nicht erkannt | 50 m beibehalten; Geofence-Hinweis, begrenzte Standortprüfung, zeitliche Stabilität und optionales WLAN kombinieren | Messreihe auf realem Zieltelefon; keine ausgedachte Zuverlässigkeitsquote |
| hoch | Pausieren durch falsches WLAN / ungenaue Position | WLAN nur unterstützend; GPS-Unsicherheit und Alter beachten, Evidenz ablaufen lassen, überlappende Orte gemeinsam prüfen | Regeltests plus Indoor-/Mesh-/WLAN-aus-Gerätetests |
| hoch | Doze oder OEM-Optimierung verzögert Warnungen | 15 Minuten als gewünschtes Mindestintervall, keine Echtzeitgarantie. Zusätzliche eindeutige Ereignisaufträge, sichtbares Datenalter | 24-h-Protokoll mit gesperrtem Display, Energiesparen und Offline-Phasen |
| hoch | Daten mehrerer Quellen widersprechen sich | Providerkennungen getrennt halten; Zusammenführung nur mit belastbarem Identitätsbeleg. Keine unscharfe Textähnlichkeit zur Entwarnung | Konflikt- und ID-Wechsel-Fixtures; Quellprovenienz im Modell |
| hoch | Neustart erzeugt doppelte Meldungen oder verliert offene Ereignisse | Zustand und Benachrichtigungsrevision atomar persistieren; stabile Android-Notification-Kennung; wiederholbare Verarbeitung | Neustart-/Crash-Tests rund um Persistenz und Notification-Versand |
| hoch | Fehlende Rechte verhindern Automation | Kontextbezogene Rechteführung, keine Abstürze, eingeschränkten Betrieb anzeigen; keine Berechtigung als erteilt annehmen | Entzug jedes relevanten Rechts im laufenden Betrieb |
| mittel | Unvollständige Stationsdaten lassen Linien fehlen | Versions-/Gültigkeitsdaten des Katalogs, GTFS-Routenvarianten, Stationsgruppen und Kalender berücksichtigen. Gesamte gefundene Linie überwachen | verifizierter VBB-Katalog, Importtests und Stichproben an Umsteigebahnhöfen |
| mittel | Über 100 Orte können nicht gleichzeitig überwacht werden | Alle speichern; maximal 100 priorisierte Geofences registrieren. Auswahlalter und unregistrierte Orte diagnostizieren | mehr als 100 synthetische Orte sowie physischer Auswahlwechsel |
| mittel | Benachrichtigung auf Watch kommt nicht an | Android- und Wearable-Einstellungen erklären, kurze Titel, feste Kanäle | Galaxy Watch 4 tatsächlich koppeln und Warnung/Update/Entwarnung prüfen |
| mittel | Kartenanbieter sieht Ausschnitt/IP oder sperrt Nutzung | Nur bei Kartenansicht abrufen, Provider austauschbar, Bedingungen und Attribution beachten; kein Bulk-Download | konkrete Tile-Konfiguration und Datenschutztext vor Veröffentlichung prüfen |
| mittel | App-Backups enthalten private Pausenorte | Cloud-Backup für private Daten ausschließen; keine Standort- oder WLAN-Logs | Manifest/Backup-Regeln prüfen, Backup-/Restore-Test |
| mittel | Bibliotheken lassen sich nicht gemeinsam bauen | feste Versionen/Wrapper, dokumentierte Kompatibilität, reproduzierbarer Build; Toolchain getrennt vom Quellarchiv halten | erfolgreicher JVM-Test und Android-Build mit protokollierten Pins |

## Verbindliche Grenzen

- Der MVP heißt erst nach den Abnahmekriterien des Master-Prompts funktionsfähig.
  Ein startbares Fixture-Dashboard ist eine Entwicklungsstufe.
- Ein erfolgreicher HTTP-Status beweist weder Vollständigkeit noch Aktualität
  der Verkehrsmeldungen. `NO_DATA`, `UNAVAILABLE`, `PARTIAL` und `STALE` sind
  fachlich andere Zustände als „keine Störung“.
- Ein geplantes Enddatum ist keine bestätigte Aufhebung. Der Ablauf einer
  geplanten Maßnahme und eine tatsächliche Wiederaufnahme des Verkehrs werden
  getrennt modelliert.
- Ein Warnungsabschluss einer Quelle darf nicht pauschal ein möglicherweise
  anderes Ereignis einer anderen Quelle beenden.
- Keine kostenpflichtige API oder Kartenleistung, kein eigener Server und
  keine Übertragung privater Pausenorte werden stillschweigend eingeführt.
- Keine Aussage „auf Galaxy Watch 4 getestet“, „15 Minuten garantiert“ oder
  „50 Meter zuverlässig“ ohne passende Messung.

## Begründung der Android-Risiken

Androids [Geofencing-Dokumentation](https://developer.android.com/develop/sensors-and-location/location/geofencing)
beschreibt die Grenzen kleiner Radien, verzögerte Übergänge und das 100-Orte-Limit.
[WorkManager](https://developer.android.com/reference/kotlin/androidx/work/PeriodicWorkRequest)
unterliegt Betriebssystemoptimierungen. Die konkrete Hybridstrategie und
offenen Gerätemessungen stehen in `ANDROID_CONSTRAINTS.md`. Datenquellen und
Entwarnungsbelege werden in `DATA_SOURCES.md` und `FEASIBILITY.md` behandelt.

## Nächste Entscheidung nach VBB-Rückmeldung

Die Antwort und gegebenenfalls vertrauliche Dokumentation lokal prüfen, ohne
Zugangsdaten einzuchecken. Zuerst feststellen, ob ein dokumentiertes Feld/ein
dokumentierter Mechanismus tatsächlich „Betrieb wiederhergestellt“ bedeutet.
Danach reale Antworten redigieren und als Vertragstests aufnehmen. Falls VBB
keine ausreichende Entwarnungssemantik liefert, bleibt automatische Entwarnung
deaktiviert; der offene Zustand wird als unbekannt/überholt dargestellt. Dann
muss vor einer Live-Abnahme gemeinsam entschieden werden, ob ein eingeschränkter
MVP akzeptabel ist. Diese Entscheidung ist derzeit noch nicht erforderlich.
