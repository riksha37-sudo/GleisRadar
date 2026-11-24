# Übergabe an Sol – Berliner ÖPNV-Störungswächter

Stand: 17.11.2026. Der Nutzer möchte jetzt mit dem Sol-Modell weiterarbeiten. Bestehenden Code fortsetzen, keine Neuentwicklung von null und keine erneute vollständige Phase-0-Recherche.

## Arbeitsverzeichnis und zuerst lesen

Projekt: `/workspace/work/berlin-stoerungswaechter`.


Lesereihenfolge: dieses Dokument, `README.md`, `docs/VALIDATION.md`, `docs/IMPLEMENTATION_PLAN.md`; bei fachlichen Änderungen zusätzlich `core/README.md` und `docs/ARCHITECTURE.md`. VBB-Details stehen bereits in `docs/DATA_SOURCES.md`, `docs/FEASIBILITY.md` und `docs/VBB_HANDOFF.md`.

Ein lokales Git-Repository ist initialisiert, aber noch ohne Commit. Die Projektdateien sind untracked. Nicht zurücksetzen oder überschreiben. Es gibt keinen eingerichteten Remote und keine Veröffentlichung.

## Verifizierter Stand

- Phase 0 dokumentiert: offizielle VBB/HIM-, GTFS-/GTFS-RT-Quellen, Transport.rest, S-Bahn/BVG, Android-Hintergrundgrenzen, WLAN, Kartenlizenzen und Watch-Spiegelung.
- Kotlin-Kern vorhanden: normierte Datenmodelle, Provider-Vertrag, synthetische Fixtures, gesperrter VBB-Adapter, 5-km-Auswahl ganzer Linien, begrenzter Standortcache, konservative 50-m-Pausenlogik, höchstens 100 Geofence-Kandidaten, Ereignis-/Meldungsverfolgung und versionierter Persistenzcodec.
- **45 Kotlin-Tests erfolgreich**, einschließlich Regressionen für Reihenfolge widersprüchlicher Entwarnungsnachweise und Kategorien, die vor der Benachrichtigungsverbuchung gefiltert werden. XML-Berichte vorhanden.
- **15 Python-Importer-Tests erfolgreich.** Der echte VBB-GTFS-Feed wurde verarbeitet: 736 bediente Plattformen/Haltestellen, 25 Linien. Quellen-/Lizenz-/Zeitraummetadaten enthalten.
- Android-App-Code vorhanden: vier deutsche Compose-Tabs, Room-Schema/DAOs, gespeicherter Testzustand und Vorschauverlauf, DataStore-Kategorien, manuelle Fixture-Szenarien. Keine tatsächliche Liveüberwachung.
- **Arbeitspaket 1 abgeschlossen.** Android Debug-Build erfolgreich, Lint mit 0 Fehlern und 8 Warnungen. Die Signatur der Debug-APK wurde geprüft. Ein Geräte- oder Emulatortest steht aus.

## Erstes Arbeitspaket abgeschlossen

Die App baut mit Compose BOM **2026.06.01** und Compose **1.11.4** auf AGP **8.13.2**, Gradle **8.13**, Kotlin **2.3.21** sowie compileSdk/targetSdk **36**. Die APK liegt unter `app/build/outputs/apk/debug/app-debug.apk`. Größe, SHA-256, Lint-Bericht und Prüfbefehle stehen in [VALIDATION.md](VALIDATION.md).

In der vorbereiteten Cloud-Umgebung vor Gradle `source /workspace/work/toolchain/env.sh` ausführen. Der lokale Build-Log liegt unter `/workspace/work/android-package1-final.log`. Ein späterer Gerätetest kann den Ablauf aus [APP_NOTES.md](APP_NOTES.md) verwenden.

## Zweites Paket und App-Oberfläche

Version 0.2 lädt den statischen VBB-Stationsindex aus `app/src/main/assets/station-index.json`. Innerhalb seines Zeitfensters berechnet sie die S-/U-Bahnlinien um einen festen Punkt in Berlin-Mitte. Das Zeitfenster endet am 01.12.2026. Aktualisieren nach [scripts/README.md](../scripts/README.md). Der App-Lader prüft Schema, Quellenangabe, Prüfsummenformat und Gültigkeit. Der Rohfeed ist ein separater VBB-Download.

Die Übersicht bietet eine manuelle Abfahrtsvorschau für Alexanderplatz mit S-/U-Bahn-Filter und Links zu den offiziellen Betreiberseiten. Der Abruf und der Testmodus sind getrennt. Die Stichprobe bleibt auf einen Bahnhof beschränkt. Fehlende Warnungen bleiben als unbestätigt sichtbar. Produktiver HIM-Zugriff, laufende Abdeckung ganzer Linien und bestätigte Aufhebungen stehen weiterhin aus.

[UI_DESIGN.md](UI_DESIGN.md) beschreibt die Oberfläche und den Ablauf zur Abnahme auf dem Pixel. Der [Prüfstand](VALIDATION.md) beschreibt die ausgeführten Tests. Das nächste Implementierungspaket ist die Pausenkarte mit 50-m-Standardradius.

## Danach: Standort und Hintergrund in getrennten Paketen

**Paket 3 – Karte und reale Pausenorte:** MapLibre/geeigneten zulässigen Tile-Anbieter, sichtbare Attribution, Marker zum Hinzufügen/Bearbeiten/Löschen, Radius pro Ort mit Standard 50 m, Room-CRUD. Keine Koordinatenform als Ersatz für die geforderte Karte und keine kostenpflichtigen Dienste ohne Abstimmung.

**Paket 4 – Android-Standortadapter:** gestufte Berechtigungen, Fused Location, GeofencingClient, optionale WLAN-Indizien, Überlappungen, Registrierungslimit 100, Neustart und Rechteentzug. Der vorhandene `HybridPauseEngine` ist reine Fachlogik, noch kein Android-Geofencing. Zeitstempel aus Androids monotoner Messzeit verlässlich auf die Domäne abbilden. 50 m nicht heimlich erhöhen.

**Paket 5 – Hintergrund und Android-Benachrichtigungen:** eindeutige WorkManager-Aufträge, standardmäßig 15 Minuten; Ereignisprüfungen bei bestätigtem Austritt/Bewegung; reduzierte Nachverfolgung offener bereits gemeldeter Ereignisse in Pausen. Persistente Outbox vor Systemzustellung ergänzen, Channels und Rechte beachten, stabile Tags, initiale Zusammenfassung, Aktualisierung/Entfernung/Entwarnung. Vorschau-Ledger ist keine bestätigte Systemzustellung. Keine Echtzeitgarantie.

**Paket 6 – Geräte-/Watch-Abnahme:** Doze, App Standby, App-/Geräteneustart, Netzwerkverlust, 50-m-Gehversuche, Akkuverbrauch sowie Galaxy-Watch-4-Spiegelung. Implementiert, automatisiert getestet und auf echtem Gerät bestätigt getrennt berichten.

## VBB parallel als externer Blocker

**Die API-Anfrage wurde bereits gestellt. Nicht erneut stellen und niemanden ohne ausdrücklichen Auftrag kontaktieren.** Keine Zugangsdaten oder echte API-Antworten vorliegend.

Laut geprüfter offizieller Dokumentation steht am 30.11.2026 eine Migration an. Im neuen System sind vorher HIM-Testdaten beschrieben. Der Umkreis-Endpunkt hat eine dokumentierte Abdeckungseinschränkung. Eine gemeinsame `accessId` darf nicht offen in einer veröffentlichten APK liegen; daher bleibt das serverlose Nutzungsmodell mit VBB zu klären. Keinen eigenen Server stillschweigend ergänzen.

Nach Rückmeldung: Vertrag/Zugriff prüfen, reale redigierte Antwort-Fixtures anlegen, IDs/Filter/Pagination/Änderungen und insbesondere explizite Aufhebung verifizieren. Dann den austauschbaren Liveadapter implementieren. Die übrigen lokalen Arbeitspakete können unabhängig davon weitergehen.

Unverhandelbare Regel: **Verschwinden aus einer Antwort, API-Ausfall, abgelaufenes Prognoseende oder ein nicht verifiziertes `act=false` sind keine Entwarnung.** Ohne belegte Semantik bleibt `supportsExplicitResolution=false`.

## Arbeitsweise für die Fortsetzung

Pro Arbeitspaket Code, passende Tests und Dokumentation abschließen. Vorhandene Ergebnisse wiederverwenden. Keine erfolgreichen Builds oder Geräteprüfungen erfinden. Keine unnötige erneute Recherche. Erst nach wirklich blockierenden Produkt-/Kosten-/Datenschutzentscheidungen nachfragen. Bei Modell-/Sitzungswechsel diesen Übergabestand aktualisieren.
