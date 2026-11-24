# Umsetzungsplan und offener Stand

Stand: 21.11.2026. Der Android-Prototyp umfasst den lokalen Stationsindex, eine manuelle Abfahrtsvorschau und die Diagnose mit simulierten Meldungen. Für die verbleibenden Ansichten und die Bedienung auf dem Pixel gilt [UI_DESIGN.md](UI_DESIGN.md).

## Phase 0 – dokumentierte Prüfung

- Datenquellen-Matrix mit Primärquellen und tatsächlich ausgeführten öffentlichen Stichproben erstellt.
- VBB-Migration, unvollständige GTFS-RT-Abdeckung und ungeklärte Aufhebungssemantik dokumentiert.
- Android-Grenzen, 50-m-Risiken, WLAN-Berechtigungen, Watch-Spiegelung und Kartenlizenzen geprüft.
- Entscheidung: lokale Kernentwicklung möglich. Live-Abdeckung und sichere öffentliche API-Verteilung bleiben gesperrt, bis die bereits angefragte VBB-Rückmeldung ausgewertet ist.

## Phase 1 – Entwicklungsgrundlage

- Zwei Module `core` und `app`; Kotlin, Compose, Material 3, Gradle-Wrapper.
- Normiertes Domänenmodell, Provider-Vertrag, gesperrter VBB-Adapter und synthetischer Fixture-Provider.
- Room-Schema, versionierter Ereigniscodec, gespeicherter Vorschau-/Deduplizierungsstand, DataStore-Kategorien.
- Vier minimale App-Ansichten einschließlich kontrollierbarer Testszenarien.
- Build- und CI-Konfiguration; tatsächlicher Ausführungsstand separat in `VALIDATION.md`.

## Phase 2 – Kernfunktionen begonnen

Implementierte Grundlagen: geografische Auswahl ganzer S-/U-Bahnlinien, Cache mit Altersgrenze, Entwarnungsregeln, Deduplizierung, relevante Änderungen, Planungshorizont und Nachverfolgungsanforderung bereits gemeldeter offener Vorgänge. Der GTFS-Importer liefert den eingebauten Stationsindex mit Kalender-/Betreiberfilter und nachvollziehbarer Herkunft. Die manuelle Abfahrtsvorschau bleibt ein eigener Abruf mit Quellenangabe und Zeitstempel.

Noch notwendig:

1. Aktualisierung und Verteilung neuer Stationsindizes festlegen. Vor endgültiger Identitätsfestlegung GTFS-IDs und HIM-GlobalIds anhand echter Daten zuordnen.
2. VBB-Vertrag/Zugang und API-Migration prüfen; dokumentierte Antwortfelder statt angenommener Schemas abbilden. Keine Schlüssel in eine veröffentlichte APK einbetten.
3. HTTP-Client mit Timeouts, Retry/Backoff, Rate-Limits, kontrollierter Pagination, Vollständigkeit und redigierter Diagnose ergänzen.
4. Contract-Fixtures aus echten freigegebenen Antworten hinzufügen. Entwarnungsunterstützung nur nach belegtem Aufhebungsfall aktivieren.
5. Abdeckung und stabile IDs für Störungsereignisse am Vergleich mit den offiziellen Meldungen prüfen. Die Gültigkeitsgrenzen des Stationsindex und die Abrufzeit der Stichprobe werden bereits dargestellt.

## Phase 3 – reale Standortintegration

Der Hybrid-Entscheider ist fachlich vorhanden; die Plattformadapter fehlen. Nächste Aufgaben: MapLibre mit freigegebenem Tile-Anbieter, Kartenmarker-CRUD mit 50-m-Standard, kontextbezogene Standortrechte, sparsame Positionsabfragen, GeofencingClient, unterstützende WLAN-Erkennung, Registrierung nach Neustart/Änderung und Diagnose. Mehr als 100 gespeicherte Orte müssen erhalten bleiben. Standort-/Berechtigungsverlust darf keinen angeblich bestätigten Austritt erzeugen.

WLAN kann die Messung stützen, aber die App darf keine dauernde Scan- oder Aufweckgarantie annehmen. Auf Geräten mit unzuverlässigem 50-m-Radius werden Abweichungen gemessen; der Standard wird nicht stillschweigend geändert.

## Phase 4 – Hintergrund und echte Meldungen

WorkManager mit 15-Minuten-Standard, Netzwerkbedingung und eindeutigen Aufträgen ergänzen. Zonenaustritt und relevante Bewegung lösen zusätzliche Prüfungen aus. Bereits angekündigte offene Ereignisse auch in Pausen reduziert nachverfolgen. System-/Herstellerverzögerungen sichtbar machen.

Vor Android-Zustellung persistente Outbox, Notification Channels, Berechtigungsprüfung, gezielte Aktualisierung/Entfernung sowie Zusammenfassungsverwaltung implementieren. Der derzeitige Vorschauverlauf ist noch keine Zustellbestätigung. Galaxy-Wearable-Einstellungen und Entfernung/Aktualisierung auf der Watch 4 testen.

## Phase 5 – Abnahme auf Geräten

Automatisierte Integrationsprüfungen für Room und Worker; kontrollierter Prozess-/Geräteneustart; Flugmodus, Doze, App Standby, Rechteentzug und abgeschalteter Standort. Reale Geh-/Fahrstrecken mit 50-m-Pausenorten messen, einschließlich Randlagen, Gebäuden, überlappenden Zonen und WLAN-Wechsel. Akkuverbrauch mindestens über mehrere Alltagstage erheben. Erst danach Aussage zur praktischen Hintergrundzuverlässigkeit treffen.

## Entscheidungen, die noch offen sind

| Entscheidung | Status / Anlass |
| --- | --- |
| VBB-Zugang | Anfrage laut Nutzer bereits gestellt; nicht wiederholen |
| Entwarnungssemantik | Keine belastbare öffentliche Bestätigung gefunden; Live-Abnahmeblocker |
| Öffentliche serverlose Verteilung | Gemeinsame Kennung darf nicht offen verteilt werden; VBB-Nutzungsmodell abwarten |
| Karten-/Tile-Anbieter | Noch nicht angebunden; Kosten, Nutzungsrechte und Datenschutz vor Auswahl prüfen |
| GTFS-/HIM-Linienidentitäten | Noch durch echte Daten zu validieren; keine Namensheuristik als dauerhafte Zuordnung |
| Standorthysterese und Cachegrenzen | Konservative Parameter implementiert; durch Gerätetests zu kalibrieren |
| Akkubudget / praktische Latenzen | Noch nicht gemessen |

Keine Wear-OS-App, keine Konten, Cloud-Synchronisation, Routenplanung, anderen Verbünde oder Store-Veröffentlichung in diesem Arbeitsschritt.
