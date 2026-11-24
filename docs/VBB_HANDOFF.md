# Bereits gestellte VBB-Anfrage: technische Anschlussfragen

Status vom Nutzer: **API-Anfrage wurde bereits gestellt.** Es wurde keine zweite Anfrage versendet und kein Zugang als vorhanden angenommen. Keine Schlüssel in dieses Dokument oder ein Issue eintragen.

Vor einem produktiven Adapter benötigen wir aus der Antwort bzw. dem freigegebenen Vertrag:

1. Freigeschaltete Umgebung, dokumentierte Basis-URL, Authentifizierung, Rate-Limits und Bedingungen zur privaten bzw. später öffentlichen Android-Nutzung.
2. Zulässiges Modell für Zugangsdaten in einer App ohne eigenen Server. Die öffentliche Dokumentation untersagt offen verteilte `accessId`-Werte. Das ist nicht durch Obfuskation oder Speicherung in `local.properties` gelöst.
3. Vollständige HIM-Abfrage einschließlich Pagination, Zeitraum, Produkt-/Linienfilter, geplanten Maßnahmen, SEV sowie Änderungen. Keine Freigabe anhand einer einzelnen erfolgreichen Beispielabfrage.
4. Stabile Ereigniskennungen und deren Lebenszyklus: Änderungen, Löschung, Zusammenführung, Wiederverwendung, Archivierung sowie verfügbare Änderungszeitpunkte.
5. **Explizite Bestätigung, welches maschinenlesbare Feld den tatsächlich aufgehobenen Betriebseingriff bezeichnet.** Ein echtes Beispiel vor und nach der Aufhebung, einschließlich zeitlicher Reihenfolge, ist nötig. `act`, Gültigkeitsende oder Verschwinden aus dem Ergebnis werden nicht ohne diesen Nachweis als Entwarnung verwendet.
6. Verhalten bei unvollständigen Ergebnissen, Störungen der Quelle und abweichenden Filtern; Vollständigkeitsindikatoren und Aktualisierungsfrequenz.
7. Migrationsplan zum 30.11.2026, Umschaltzeitpunkt der produktiven Verkehrsmeldungen und mögliche Testkennzeichnung des neuen Backends.
8. Lizenz, Attribution, Cache-/Weitergaberechte und erlaubte Aktualisierung des Stations-/Linienindex.

Nach Eingang: Zugang lokal außerhalb des Repositories einrichten, redigierte reale Antwortbeispiele als Contract-Fixtures anlegen, Schema/Filter/Statusmapping implementieren und mit Wiederholungen, leeren/partiellen Antworten und expliziter Aufhebung testen. Ohne belegte Entwarnungssemantik bleibt `supportsExplicitResolution=false`; andere fachlich belegte Meldungen können unabhängig davon integriert werden.

Quellen und beobachtete Einschränkungen: `DATA_SOURCES.md`. Es wird keine zusätzliche kostenpflichtige Infrastruktur ohne ausdrückliche Entscheidung eingeführt.
