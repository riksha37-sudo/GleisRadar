# GTFS-Stations-/Linienindex vorbereiten

`import_gtfs.py` ist ein Entwicklungswerkzeug mit Python-Standardbibliothek. Es liest ausschließlich eine lokale GTFS-ZIP-Datei. Kein Download, keine Standortübertragung, keine App-Live-Anbindung. Der erzeugte Index wird nicht automatisch als App-Ressource installiert.

## Aufruf für den am 17.11.2026 überprüften VBB-Feed

```bash
python3 scripts/import_gtfs.py /pfad/vbb-gtfs.zip /pfad/station-index.json \
  --operator '1|S-Bahn Berlin GmbH|S_BAHN|109' \
  --operator '796|Berliner Verkehrsbetriebe|U_BAHN|400' \
  --from-date 2026-11-17 --through-date 2026-12-01 \
  --source-url https://unternehmen.vbb.de/gtfs \
  --license CC-BY-4.0 \
  --license-url https://creativecommons.org/licenses/by/4.0/ \
  --attribution 'Fahrplandaten: Verkehrsverbund Berlin-Brandenburg GmbH (VBB), CC BY 4.0. Bearbeitung: Filterung auf Berliner S-/U-Bahn und Bildung eines Stations-/Linienindex.'
```

Datum, Operatorregeln und Lizenzangaben müssen zur jeweiligen Feedversion passen. Die im November 2026 geprüften Kennungen sind kein Versprechen unveränderlicher Betreiber-IDs. Abweichende Betreibernamen brechen den Import ab. Die Datensatzrechte und nötige App-Attribution sind in `docs/DATA_SOURCES.md` beschrieben.

## Semantik

- Verknüpft `routes → trips → stop_times → stops` über alle Linienvarianten und Richtungen.
- Betreiber-ID **und exakter Betreibername** werden geprüft. Nur explizite Routentypen werden übernommen; Ersatzbuslinien vom Typ700 erscheinen nicht als eigenständig überwachte S-/U-Linien. Deren SEV-Meldungen gehören später in den Störungsprovider.
- Gleichnamige Linien desselben Modus werden innerhalb eines ausgewählten Betreibers zusammengeführt. Solche Namen bei mehreren ausgewählten Betreibern werden als mehrdeutig zurückgewiesen: getrennte regionale Indizes oder genauere Identitäten wären dann nötig.
- Das inklusive Zeitfenster verwendet Wochentage und `calendar_dates`-Ausnahmen. Es umfasst die Vereinigung aller in diesem Fenster bedienten Halte, keinen minutengenauen Linienfahrplan. Höchstens366 Tage pro Import. Der App-Lader muss später Zeitfenster und Feedalter prüfen.
- Ohne beide Datumsargumente entsteht ausdrücklich `draft-whole-feed-union`: eine Vereinigung aller Fahrten im Feed, nicht für eine ungeprüfte aktuelle Standortzuordnung geeignet.
- Bediente Plattform-/Haltestellen-IDs und ihre tatsächlichen Koordinaten bleiben erhalten. `parentStationId` bleibt Metadatum. Elternpunkte werden nicht als Ersatzkoordinaten verwendet und Linien nicht anhand ähnlicher Stationsnamen zusammengelegt. Dadurch bleiben Abstände zum5-km-Rand nachvollziehbar; die App dedupliziert ausgewählte Linien.
- Fehlende Referenzen, ungültige Koordinaten bedienter Halte, fehlende ausgewählte Fahrten in `stop_times` und mehrdeutige Kennungen brechen den Import ab. Nicht bediente Eltern dürfen ohne Koordinaten vorliegen. Eine vorherige Zieldatei bleibt bei Validierungsfehlern erhalten.
- Der GTFS-Stop-ID wird keine HIM-ID zugeschrieben. Ein späterer Provider benötigt eine verifizierte Kennungszuordnung.
- `liveServiceVerified=false` bleibt immer gesetzt. Statische Fahrplandaten bestätigen weder akute Störungen noch Entwarnungen.

Metadaten enthalten Original-SHA256, Importzeitpunkt/Version, Operatorregeln, Kalenderbereich, Auswahlfenster, Quellen-/Lizenzverweise, Attribution und Zählwerte. Die vollständige ZIP-Datei wird nie in den Index eingebettet.

`stop_times` wird zeilenweise gelesen. SQLite hält die Fahrtverknüpfungen und eindeutigen Haltestellen-/Linienpaare; ein begrenzter Fahrtcache vermeidet wiederholte Abfragen. Haltestellen, Kalender und die Menge der tatsächlich gesehenen ausgewählten Fahrt-IDs bleiben im Arbeitsspeicher. Es wird keine Liste der Millionen `stop_times`-Zeilen aufgebaut.

## Tests

```bash
python3 -m unittest discover -s scripts/tests -v
```

Die synthetischen Fixtures prüfen Richtungen/Verzweigungen, Deduplizierung, entfernte Linienhalte, SEV-/Betreiberausschlüsse, Kalenderausnahmen, Plattformdaten und kontrolliertes Abbrechen bei fehlerhaften Daten. Sie benötigen weder Internet noch den echten VBB-Feed.
