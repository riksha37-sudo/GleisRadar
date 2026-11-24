# Datenquellen – Berlin/VBB

Prüfdatum: **17.11.2026**. Die VBB-API-Anfrage wurde laut Nutzer bereits gestellt. Es wurde keine weitere Anfrage versandt, kein Konto angelegt und keine VBB-Nutzungsbedingung im Namen des Nutzers akzeptiert.

## Entscheidung

**VBB-HIM ist der bevorzugte Kandidat für Störungsereignisse; lokale offizielle GTFS-Stammdaten sind der bevorzugte Kandidat für die vollständige Stations-/Linienzuordnung.** Bis Zugang, Produktionsdaten und Ereignissemantik geprüft sind, arbeitet die Entwicklung ausschließlich mit deutlich gekennzeichneten Fixtures. Die Entscheidung ist eine Architekturentscheidung, keine Behauptung einer bereits funktionsfähigen Live-Anbindung.

| Quelle | Akut / Bau / SEV | Linien, Abschnitt, Zeit | Ereignis-ID / Änderungen / bestätigte Aufhebung | Zugriff, Takt und Qualität | MVP-Entscheidung |
| --- | --- | --- | --- | --- | --- |
| [Offizielle VBB-API](https://unternehmen.vbb.de/digitale-services/api/) / [HIM-Dokumentation](https://opendatavbb.github.io/hafas-api-doku/) | HIM dafür vorgesehen; reale Abdeckung noch zu messen | Filter und Beispielstruktur dokumentiert; SEV-Struktur unbestätigt | ID/Änderungszeit dokumentiert; Aufhebungssemantik **unbestätigt** | Zugang beantragt; Test-/Produktivfreigabe getrennt; kein verifizierter fester Publikationstakt | Primärer Integrationskandidat, noch gesperrt |
| [VBB GTFS Schedule](https://unternehmen.vbb.de/digitale-services/datensaetze/) | Keine akuten Meldungen; Sollfahrplan kann Bauänderungen enthalten | Stationskoordinaten und Fahrten für lokale Zuordnung; keine garantierte textuelle Abschnittserklärung | Fahrplan-IDs, keine Störungs-Lebenszyklen | Offizieller ZIP-Download; laut VBB zweimal wöchentlich; CC BY 4.0 | Stammdatenindex, kein Entwarnungsprovider |
| [VBB GTFS-Realtime](https://production.gtfsrt.vbb.de/) | Einzelne Fahrtprognosen; Service-Alert-Abdeckung nicht nachgewiesen | Stichprobe enthält TripUpdates | Feed-/Fahrtkennungen sind keine gesicherten Störungs-IDs; keine Entwarnung nachgewiesen | Ohne Authentifizierung, max. 7 Abrufe/Minute; CC BY 4.0; informative User-Agent-Kennung; ETag | Spätere Ergänzung, ungeeignet als alleiniger MVP-Meldungsfeed |
| [v6.vbb.transport.rest](https://v6.vbb.transport.rest/) / [API](https://v6.vbb.transport.rest/api.html) | Durchgereichte Verspätungen/remarks; kein dokumentierter vollständiger HIM-Ereignisfeed in geprüfter Routenliste | Abfahrten, Fahrten, Haltestellen und Linien | Keine überprüfte belastbare Entwarnung | Community-Dienst; HAFAS-Client vor VBB-App-API; ohne Key, dokumentiert 100 Anfragen/Minute; keine hier belegte Verfügbarkeitsgarantie | Recherche/Prototyp-Option, keine stillschweigende Produktionsabhängigkeit |
| [S-Bahn Berlin: Bauen & Störung](https://sbahn.berlin/fahren/bauen-stoerung/) | Offizielle Fahrgastinformationen, Bauarbeiten/Ersatzverkehr | Website/Detailseiten für menschliche Prüfung | Kein unterstützter öffentlicher Maschinenvertrag oder Aufhebungsarchiv in dieser Prüfung belegt | Webseiten erreichbar; Publikationstakt und Weiterverwendungsrecht nicht als API-Vertrag verifiziert | Referenzlinks/Abgleich; kein Scraper |
| [BVG: Störungsmeldungen](https://www.bvg.de/de/verbindungen/stoerungsmeldungen) | Offizielle U-Bahn-/weitere Verkehrsmeldungen | Dynamische Website mit Linien-/Datumsfilter | Stabile maschinenlesbare Ereignis-/Aufhebungsschnittstelle nicht nachgewiesen | Website erreichbar; JavaScript nötig; keine offene Feed-Lizenz hier verifiziert | Referenzlinks/Abgleich; kein Scraper |

„Unbestätigt“ bedeutet: nicht ausreichend belegt, nicht „existiert nicht“. Eine öffentliche Website oder eine Open-Source-Clientlizenz ersetzt keine Nutzungsfreigabe für Verkehrsdaten.

## Aktuelle VBB-Migration – dokumentiert, noch nicht authentifiziert getestet

Die offizielle Dokumentation (Stand 03.11.2026) kündigt den Wechsel von 2.49 auf HAFAS ReST 2.52 zum **30.11.2026** an. Neue Basisadressen: `https://vbb.hafas.cloud/api/fahrinfo/latest` und Demo `https://vbb.demo.hafas.cloud/api/fahrinfo/latest`. Der alte Host lautet `fahrinfo.vbb.de/restproxy`. Der dokumentierte Dienst heißt `himsearch`; Authentifizierung erfolgt über `accessId`.

Wesentliche Einschränkungen: Im neuen System gibt es vor dem Wechsel laut Dokumentation nur eine HIM-Testmeldung. `location.nearbystops` hat einen gemeldeten Fehler bei der Radiusabdeckung. Öffentliche Apps sollen keine eingebettete `accessId` enthalten. Diese Punkte sind Gründe für eine Integrationssperre, nicht für erfundene Ersatzendpunkte.

Dokumentierte HIM-Beispielfelder sind `id`, `act`, `head`, `text`, `sDate`/`sTime`, `eDate`/`eTime`, `modDate`/`modTime`, `affectedProduct`, `affectedStops` und `event`. Filter umfassen `lines`, `globallineids`, `himIds` und einen Zeitraum. Die Semantik `act=false` = tatsächlich aufgehoben ist **nicht** belegt. Die API wird erst nach dem Vertragstest auf das interne Datenmodell abgebildet. [Quelle](https://opendatavbb.github.io/hafas-api-doku/)

## Tatsächlich ausgeführte öffentliche Prüfungen

Die folgenden GET-Abfragen liefen ohne Zugangsdaten am 17.11.2026. Eine erfolgreiche Antwort ist nur eine Stichprobe, kein SLA und kein Vollständigkeitsbeweis.

| Prüfung | Beobachtetes Ergebnis | Aussagegrenze |
| --- | --- | --- |
| `https://opendatavbb.github.io/hafas-api-doku/` | HTTP 200; offizielle Verlinkung vom VBB und Migrationshinweise im HTML bestätigt | Keine authentifizierte HIM-Abfrage |
| `https://production.gtfsrt.vbb.de/` | HTTP 200; Hinweis auf eingeschränkte Datenabdeckung seit 04.06.2026, ohne Wiederherstellungstermin | Keine Aussage zur genauen fehlenden Betreiber-/Linienmenge |
| `https://production.gtfsrt.vbb.de/data` | HTTP 200; 10.680.051 Bytes; Protobuf dekodiert: Version 2.0, FULL_DATASET, 10.540 Entitäten, ausschließlich TripUpdates; 0 Alerts, 0 VehiclePositions | Eine einzelne Momentaufnahme; nicht die Behauptung, dass Alerts grundsätzlich nie geliefert werden |
| GTFS-RT-Zeitstempel | Header 17.11.2026 12:05:58 MEZ; Abruf 12:06:22 MEZ | Frischer Feedheader bestätigt nicht jede einzelne Fahrtinformation |
| `https://v6.vbb.transport.rest/locations?query=Alexanderplatz&results=1` | HTTP 200; Station `900100003`, gültige Koordinaten, suburban/subway enthalten | Nur Ortssuche getestet; keine Vollständigkeit von Störungen geprüft |
| `https://sbahn.berlin/fahren/bauen-stoerung/` | HTTP 200 | Keine dokumentierte Maschinen-API abgeleitet |
| BVG-Linkprüfung | `/de/verbindungen/verkehrsmeldungen` ergab 404; aktueller offizieller Pfad ist `/de/verbindungen/stoerungsmeldungen` | Dynamischer Seiteninhalt nicht als garantierter Datenfeed interpretiert |

Die öffentliche GTFS-RT-Stichprobe bestätigt die Vorsicht gegenüber einer alleinigen RT-Strategie. Das offizielle [Feed-Projekt](https://github.com/OpenDataVBB/gtfs-rt-feed) beschreibt die Verarbeitung von VDV-454-Fahrtdaten zu TripUpdates. Die Feedgröße wäre zudem für einen 15-Minuten-Abruf auf jedem Telefon relevant: ohne Cache/Kompression rechnerisch etwa 1 GB pro Tag. Das ist eine Größenordnung aus dieser Stichprobe, keine behauptete tatsächliche tägliche Datenmenge.

## Warum GTFS-Realtime allein keine zuverlässige Entwarnung liefert

Die Prüfung der Betreiber-Webseiten und weiterer Alternativen vom 21.11.2026 steht in [WEB_FALLBACK.md](WEB_FALLBACK.md).

Die [GTFS-RT-Referenz](https://gtfs.org/documentation/realtime/reference/) unterscheidet Feed-Entitäten, Fahrtprognosen und Alerts. Ein entferntes Objekt ist keine fachliche Bestätigung des Regelbetriebs. Auch ein erwartetes Ende beschreibt keine beobachtete Aufhebung.

Die [Service-Alert-Empfehlungen](https://gtfs.org/resources/mobilitydata-recommendations/gtfs-realtime-service-alerts/guidelines/) sehen zwar optional eine `NO_EFFECT`-Meldung über ein beendetes Ereignis vor; verpflichtend ist sie nicht. Daher darf weder ein generisches `NO_EFFECT` ohne Ereigniszuordnung noch ein leerer oder verkleinerter Feed pauschal in Entwarnungen übersetzt werden.

## Vertragstest nach VBB-Antwort

Die bereits gestellte Anfrage muss nicht wiederholt werden. Sobald die Antwort vorliegt, sind diese Informationen zu prüfen:

1. Freigeschaltete Umgebung, Basisadresse, `himsearch`-Berechtigung, Limits und Mobilclient-Nutzungsmodell. Schlüssel weder hier einkopieren noch committen.
2. Reale Beispiele jeweils für neue, geänderte, geplante, begonnene und **explizit aufgehobene** Meldungen, einschließlich SEV.
3. Stabilität, Gültigkeitsbereich und Wiederverwendung von IDs; Revisionen, Zeitzone und Aufbewahrungsdauer beendeter Meldungen.
4. Semantik von `act`, Ereignis-/Publikationsende, Rücknahme und Löschen; liefern `himIds` auch nach Ausblendung belastbare Statusinformationen?
5. Vollständigkeit/Paginierung/Ergebnislimits; welche Filter decken sämtliche Abschnitte einer ausgewählten Linie ab?
6. Wirkung der Migration und tatsächlich behobener Umgebungssuche; Abgleich von Haltestelle, Haltepunkt und globaler Linien-ID.
7. Darf der private MVP direkt zugreifen und wie lässt sich eine spätere öffentliche serverlose App regelkonform betreiben? Ein Server wird ohne Produktentscheidung nicht eingeführt.

Nutzungsbedingungen: Das [VBB-Produktivformular](https://unternehmen.vbb.de/digitale-services/api/zugang-produktivsystem/) verlangt Quellenangabe, effiziente Abfragen und akzeptiert keine Gewähr für Vollständigkeit/Aktualität oder unterbrechungsfreie Nutzung. GTFS-Attribution und Änderungskennzeichnung müssen im späteren Datenpaket und in der App enthalten sein; eine CC-BY-Lizenz für Datensätze wird nicht pauschal auf alle API-/Webinhalte übertragen.

## Statische GTFS-Stichprobe und Importregeln

Der verlinkte offizielle Download `https://unternehmen.vbb.de/gtfs` wurde am 17.11.2026 tatsächlich als ZIP geladen und die CSV-Struktur geprüft: 84.358.537 Bytes; SHA-256 `af8eba8f3e285e762141e82df1fcafd9cd76fff6d06c1ff5cdeaad1da27a0ee1`. Die Datei ist ein Rechercheartefakt und wird nicht als riesige unveränderte App-Ressource eingebaut. Keine personenbezogenen Standortdaten wurden dafür übertragen.

Beobachtungen aus diesem konkreten Datenbestand:

- `route_type=109` kennzeichnet S-Bahnvarianten; die Berliner U-Bahn verwendet `400`. Das entspricht erweiterten [GTFS-Routentypen](https://developers.google.com/transit/gtfs/reference/extended-route-types); ein Import, der nur die Basistypen `1`/`2` akzeptiert, wäre falsch.
- `agency_id=1` ist laut `agency.txt` **S-Bahn Berlin GmbH**, `agency_id=796` **Berliner Verkehrsbetriebe**. Zusammen mit den genannten Bahntypen ergeben sich 57 Route-Datensätze und 25 Liniennamen (16 S-/9 U-Bahn, einschließlich S15). Diese Kennungen sind überprüfte Werte dieser Feedversion, kein dauerhaft garantierter Vertrag.
- Typ `109` existiert auch bei `agency_id=108` (DB Regio AG) mit weiteren Liniennamen. Deshalb dürfen weder Präfix `S` noch Routentyp allein den Berliner MVP-Geltungsbereich definieren.
- Busdatensätze können `U2`, `U6`, `S5`, `S7A` usw. heißen und trotzdem Typ `700` besitzen. Sie dürfen nicht als reguläre S-/U-Bahnstationen den Radiusfilter erweitern. Ihre Zuordnung als Ersatzangebot benötigt eine verifizierte Verbindung zum betroffenen Bahnereignis; kein bloßes Abschneiden von Buchstabensuffixen.
- `stops.txt` besitzt `parent_station`, aber dieses Feld ist nicht für jede Haltestelle befüllt. Original-ID und Elternbeziehung erhalten; keine pauschal angenommene Identität zwischen GTFS-Stop-ID und HIM-ID.
- `feed_info.txt` fehlt in dieser Stichprobe. `calendar.txt` reicht von 13.11.2026 bis 11.12.2027; konkrete Betriebstage ergeben sich zusätzlich aus `calendar_dates.txt`. Dieser globale Datumsbereich bedeutet nicht, dass jede einzelne Linie an jedem Tag fährt.
- `stop_times.txt` hat hier rund 406 MB unkomprimierte Größe. Import streamen, relevante `trip_id` vorher bestimmen, nicht den gesamten Text als String auf ein Telefon laden.

Vorgesehene Indexbildung: Betreiberauswahl gegen `agency.txt` prüfen; Bahnvarianten identifizieren; alle passenden `trip_id` und deren `stop_times` vereinigen; Station/Steig getrennt erhalten und für die Stationsauswahl deduplizieren. Kalender und Ausnahmen für den gewünschten Zeitraum berücksichtigen. Die Importauswahl prüft Betreiber und Produkt. Der aktuelle Domänenschlüssel besteht aus Region, Produkt und kanonischer Linienbezeichnung; innerhalb der festgelegten Berliner Betreiberzuordnung dürfen keine mehrdeutigen Namen zusammengeführt werden. Für die spätere HIM-Anbindung wird die offizielle Linienkennung verifiziert; eine Displaybezeichnung allein ist kein globaler Schlüssel. Für die ganze Linie die Vereinigungsmenge sämtlicher gültiger Varianten verwenden. Feed-Hash, Abrufdatum und Attribution speichern, damit der Index reproduzierbar aktualisiert werden kann.

Noch nicht durch diese CSV-Stichprobe belegt: dauerhafte Stabilität aller IDs, vollständige Erhaltung geschlossener Stationen in Bauphasen, sämtliche Stations-Linien-Kanten und deren Mapping zu HIM-GlobalIds. Ein künftig importierter Index muss diese Aspekte in eigenen Validierungen abdecken.

## Lizenz des abgeleiteten Stationsindex

Die [VBB-Datensatzseite](https://unternehmen.vbb.de/digitale-services/datensaetze/) nennt **CC BY 4.0**. Die [Lizenzübersicht von Creative Commons](https://creativecommons.org/licenses/by/4.0/) gestattet Teilen und Bearbeiten, auch kommerziell, bei Namensnennung, Lizenzverweis und Kennzeichnung von Änderungen. Daraus folgt für unser abgeleitetes GTFS-Indexpaket: Weitergabe ist unter diesen Bedingungen grundsätzlich vorgesehen. Das ist keine Freigabe für geschützte Logos oder für zusätzliche anders lizenzierte Quellen. Auch eine eigene Lizenz des Importprogramms hebt die Attribution der Daten nicht auf.

Vorgesehene Attribution im Index-Metadatensatz sowie in einer erreichbaren App-Quellenansicht:

> Fahrplandaten: Verkehrsverbund Berlin-Brandenburg GmbH (VBB), CC BY 4.0. Quelle: https://unternehmen.vbb.de/digitale-services/datensaetze/ . Bearbeitung: Filterung auf Berliner S-/U-Bahn, Bildung eines Stations-/Linienindex; keine unveränderte Originalveröffentlichung.

Zusätzlich enthalten: Lizenz-URL `https://creativecommons.org/licenses/by/4.0/`, Download-URL `https://unternehmen.vbb.de/gtfs`, Abrufdatum, Original-SHA256, Importerversion, konfigurierte Betreiberprüfung und Zeitfenster. Beim Aktualisieren ersetzen sich Feedversion und Hash. Weder Zustimmung noch Unterstützung der App durch den VBB suggerieren.
