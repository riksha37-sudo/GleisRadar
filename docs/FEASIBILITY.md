# Technische Machbarkeit – Phase 0

Stand: **17.11.2026**. Entscheidung: **Phase 1 darf mit Provider-Abstraktion, lokalen Fixtures, Persistenz und reproduzierbaren Kerntests beginnen. Ein produktiv funktionierender Live-MVP ist noch nicht nachgewiesen.** Die VBB-API-Anfrage ist bereits gestellt. Die aktuelle Datenquellenprüfung steht in [DATA_SOURCES.md](DATA_SOURCES.md).

## Was machbar ist und was noch blockiert

| Teilziel | Bewertung | Konsequenz |
| --- | --- | --- |
| Native, lokal arbeitende Android-App ohne Konten | Architektur machbar | Kotlin/Compose, Room und getrennte Provider; keine eigenen Standortserver |
| Stationen in 5 km und anschließend komplette S-/U-Bahnlinien | Deterministisch lokal berechenbar | Vollständigen Stations-/Linienindex verwenden; Haversine-Luftlinie; Varianten vereinigen |
| Akute Störungen, Baustellen, SEV | Offizielle HIM-Schnittstelle vorgesehen; aktuelle Produktionsabdeckung ungeprüft | Live-Adapter erst nach authentifiziertem Vertragstest aktivieren |
| Automatische bestätigte Entwarnung | **Offener Abnahmeblocker** | Ohne belegte Aufhebungsnachricht bleibt das Ereignis offen oder veraltet |
| 50-m-Pausenort | Als Standard implementierbar, Zuverlässigkeit nicht garantiert | Hybridlogik und praktische Messreihe erforderlich; Radius bleibt 50 m |
| 15-Minuten-Hintergrundprüfung | Als WorkManager-Wunschintervall machbar | Keine Echtzeitgarantie; letzte erfolgreiche Prüfung und Datenalter sichtbar |
| Serverlose öffentliche API-Verwendung | **Offene Vertrags-/Verteilungsfrage** | Keine gemeinsame API-Kennung in öffentliche APK einbauen; keinen Server eigenmächtig hinzufügen |
| Galaxy Watch 4 | Normale Android-Benachrichtigungen vorgesehen | Keine Wear-App; tatsächliche Spiegelung auf Nutzergerät prüfen |

## Entwarnung als fachlich gesicherter Zustandswechsel

Das nachfolgende Regelwerk ist eine bewusste Produkt-/Sicherheitsentscheidung, keine zugesagte Fähigkeit einer Datenquelle.

Eine Entwarnung benötigt mindestens:

- dieselbe eindeutig zuordenbare Quelle und Ereignisidentität;
- eine explizite, für diesen Provider dokumentierte Bestätigung, dass die Einschränkung aufgehoben wurde;
- eine nicht ältere Revision beziehungsweise verlässliche Änderungszeit als der zuletzt angenommene Stand;
- gespeicherte Evidenz (Quelle, Referenz, Bestätigungszeit), damit die Entscheidung nachvollziehbar bleibt.

Kein Entwarnungsgrund sind ein fehlender Listeneintrag, abgelaufene Prognose, leere API-Antwort, HTTP-Fehler, geänderter Suchfilter, verspätete Wiederholung, Ablauf des Anzeigezeitraums oder bloß wieder pünktliche Einzelfahrten. Ein technisches Löschsignal kann auch eine Korrektur/Datenbereinigung bezeichnen und reicht ohne Vertrag nicht aus.

| Eingang | Gewünschte Verarbeitung |
| --- | --- |
| Neue aktuelle Meldung | ACTIVE; einmalige Warnung bei Relevanz |
| Neue zukünftige Maßnahme im 14-Tage-Fenster | PLANNED; einmalige Planungskategorie |
| Identische Wiederholung | Kein neuer Benachrichtigungsvorgang |
| Relevanter neuer Inhalt, etwa SEV/Abschnitt/Zeitraum | UPDATE-Vorgang für dasselbe Ereignis |
| Geplanter Beginn erreicht | Beginn gesondert behandeln; kein Schluss über das spätere Ende |
| Prognostiziertes Ende erreicht, keine Aufhebung | UNKNOWN/STALE mit sichtbarer Unsicherheit; keine Entwarnung |
| API-Fehler/unvollständige Antwort/verschollenes Ereignis | Letzten Stand behalten, Aktualität verschlechtern |
| Belastbare explizite Aufhebung | RESOLVED; offene Warnung ersetzen und einmalige Entwarnung |
| Ältere Meldung nach Aufhebung | Nicht erneut öffnen; Diagnoseeintrag |
| Andere Quelle widerspricht | Quellen getrennt halten; keine textbasierte automatische Zusammenführung |

Ein Provider, der keine bestätigten Aufhebungen liefert, kann weiterhin Warnungen erzeugen. Die App muss dann offen „Ende nicht bestätigt“ anzeigen. Damit ist die Entwarnungsanforderung jedoch **nicht abgenommen**. Eine eventuelle Lockerung dieser Anforderung wäre eine spätere bewusste Produktentscheidung.

## Vollständige Linienrelevanz

Die 5-km-Grenze gilt ausschließlich für die Stationsauswahl. Hat irgendeine Station einer S-/U-Bahnlinie diese Bedingung erfüllt, sind sämtliche Ereignisse dieser Linie relevant, auch in weit entfernten Abschnitten. Ein räumlicher Meldungsfilter wäre hierfür falsch.

Für einen lokalen GTFS-Index ist `routes → trips → stop_times → stops` über sämtliche relevanten Varianten zu verbinden. Stations-/Bahnsteighierarchie und gültiger Fahrplanzeitraum müssen berücksichtigt werden. Ein einzelner Abfahrtsmonitor oder eine begrenzte Nearby-Ergebnisliste kann Vollständigkeit nicht beweisen. Die dokumentierte VBB-Umgebungssuche ist aktuell zusätzlich migrationsbedingt eingeschränkt; deshalb ist sie vorläufig kein Fundament für die verbindliche Radiusregel.

Für das Grundgerüst reichen klar synthetische kleine Stationsdaten zur Prüfung der Rechenregeln. Diese dürfen niemals als vollständiger Berliner Stationsbestand dargestellt werden. Ein späterer produktiver Index benötigt dokumentierte Herkunft, Lizenz, Feedversion, Zeitbereich und Prüfungen gegen bekannte Linienäste. Gültige Zuordnungen zwischenspeichern; bei Bewegungen nur jenseits einer definierten Distanz oder bei Ablauf des Caches neu berechnen. Zu alte Standorte nicht stillschweigend als aktuell behandeln.

## Android-Grenzen und Hybridansatz

[Android Geofencing](https://developer.android.com/develop/sensors-and-location/location/geofencing) begrenzt registrierte Zonen auf 100 je App und Gerätenutzer. Standort-/Hintergrundberechtigungen sind erforderlich. Ereignisse können verzögert eintreffen; die Plattform empfiehlt für robuste reine Geofences größere Radien. Das ändert unsere ausdrückliche Vorgabe von **50 m** nicht.

Vorgesehener Ansatz: Geofence-Ereignis als Kandidat, anschließende sparsame Standortvalidierung, Genauigkeitsprüfung und zeitliche Stabilität vor Zustandswechsel. WLAN darf – nur bei zugänglichen, zulässig erhobenen Informationen – ein ergänzendes Indiz sein, kein alleiniger Beweis für Verlassen oder Aufenthalt. Überlappende Orte gemeinsam auswerten; alle Orte speichern, bei mehr als 100 nur einen dynamischen Ausschnitt registrieren. Der letzte gültige Zustand erhält ein Alter und kann unsicher werden. Dauerhaftes GPS ist nicht vorgesehen.

[WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) erlaubt periodische Arbeit ab 15 Minuten; Ausführung hängt von Systemoptimierung und Constraints ab. Geofence-Austritt/manueller Abruf erzeugen zusätzliche eindeutige Aufträge. Doze, App Standby, fehlendes Netz, entzogene Berechtigungen und Herstellerrestriktionen bleiben messbare Grenzen. Bereits gemeldete offene Ereignisse brauchen auch während einer Pause einen reduzierten Nachverfolgungsweg. Detaillierte Plattformrisiken und Gerätetestplanung werden separat dokumentiert.

## Freigabekriterien für die nächsten Phasen

**Jetzt:** Architektur und Kernlogik implementieren, simulierte API-Ausfälle und Revisionen testen, Datenquellenzustand sichtbar machen. Keine Live-Abdeckung suggerieren.

**Vor produktiver VBB-Anbindung:** Zugang und Nutzungsmodell klären; nach Migration reale HIM-Antworten prüfen; Ereignisidentität, Filtervollständigkeit und Aufhebungssemantik belegen; bereinigte echte Antworten als Vertragstest-Fixtures sichern.

**Vor MVP-Abnahme:** Karten-CRUD, Android-Geofences/WLAN-Indizien, WorkManager, Benachrichtigungsdeduplizierung und Wiederanlauf integrieren. Android-Gerät und Galaxy Watch 4 praktisch testen. Getrennt berichten: implementiert, automatisiert getestet, auf echtem Gerät bestätigt. Diese Prüfung ist weder ein abgeschlossener Akkutest noch eine Feldmessung.
