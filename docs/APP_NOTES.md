# Android-Prototyp: überprüfbarer Umfang

Stand: 21.11.2026. Die App bietet eine manuelle Abfahrtsvorschau und eine getrennte Offline-Diagnose.

## Implementiert

- Kotlin, Jetpack Compose, Material 3; vier Ansichten: Übersicht, Pausenorte, Einstellungen, Diagnose.
- Manuelle Verarbeitung eindeutig fiktiver Meldungen mit dem gemeinsamen JVM-Domänenkern.
- Fester Testpunkt in Berlin-Mitte mit eingebautem VBB-Stationsindex und sichtbarem Gültigkeitsfenster. Die App erfasst keinen Gerätestandort.
- Die Übersicht fragt auf Knopfdruck transport.rest nach Warnhinweisen zu Abfahrten am Alexanderplatz. Die Betreiberseiten lassen sich über die offiziellen Links öffnen.
- Auswahl ganzer S-/U-Bahnlinien über den 5-km-Filter; eine entfernte S5-Störung demonstriert den vollständigen Linienbezug.
- Szenarien: neue Meldungen, Wiederholung, ergänzter Ersatzbus, verschwundene Meldung, API-Ausfall, veraltete Daten und explizite Testaufhebung.
- Room speichert pro Provider/Ereignis einen versionierten Domänensnapshot, ein separates Vorschau-Ledger und den letzten Diagnosezustand in **derselben Transaktion**. Eine kleine Vorschauhistorie bleibt bei App-Neustart erhalten.
- Die Ereignisnutzlast verwendet den expliziten `TrackingStateCodec` als Base64. Keine reflektive Java-Deserialisierung. Provider und Ereignis-ID sind eigene indizierte Schlüsselspalten; spätere SQL-Abfragen können zusätzliche Felder normalisieren.
- Fehlerhafte oder unbekannt versionierte Zustände werden nicht stillschweigend gelöscht. Der Testlauf stoppt mit einem Fehlerhinweis; Zurücksetzen ist eine ausdrückliche Nutzeraktion.
- Room-Tabelle und DAO für Pausenorte, einschließlich Speichern/Bearbeiten/Löschen und 50 m Standardradius. **Noch keine nutzbare Kartenauswahl.**
- DataStore speichert die vier Kategorien für kommende Benachrichtigungsvorschauen.
- Das Manifest erlaubt HTTPS für die Abfahrtsvorschau. Standort- und Benachrichtigungsberechtigungen werden nicht angefordert. Es gibt keine Konten oder Werbe-/Analytics-Dienste. Das Manifest setzt `allowBackup=false`.

## Bewusst noch nicht aktiviert

- Produktiver VBB-Adapter und automatische Aktualisierung des eingebauten Stationsindex.
- Karten-/Tile-Anbieter und Pausenorte-Oberfläche mit Kartenbedienung.
- Fused Location Provider, GeofencingClient, WLAN-Erkennung und Hintergrundstandort.
- WorkManager, Boot-Wiederregistrierung und ereignisgesteuerte Hintergrundprüfungen.
- Android NotificationManager/Channels, Systembenachrichtigungen und Watch-Spiegelung.

Die Vorschaukategorien sind keine Android-Kanäle. Ausgeschaltete Kategorien werden bereits vor der Vorschauentscheidung im Kern berücksichtigt und nicht als gezeigt verbucht. Wiederaktivieren kann daher eine weiterhin offene, bisher nicht gezeigte Einschränkung melden. Eine Entwarnung setzt eine vorher gezeigte Warnung voraus. Vor echter Benachrichtigungszustellung ist eine transaktionale Outbox mit idempotenter Zustellung einzubauen; die aktuelle Vorschau ist kein Nachweis einer Systemzustellung.

## Manueller Ablauf nach Installation

1. App öffnen. Die Übersicht zeigt Alexanderplatz, Abrufschaltfläche und Betreiberlinks. Beim Start erscheint keine Berechtigungsabfrage.
2. Unter Diagnose „Testmeldungen laden“ drücken. S5-Störung, geplante U2-Maßnahme und eine zusammengefasste Demo-Vorschau erscheinen.
3. Dieselben Testmeldungen erneut prüfen: keine neue Vorschau.
4. App vollständig schließen und wieder öffnen. Meldungen und Verlauf müssen erhalten bleiben; erneute Abfrage erzeugt keine Wiederholung.
5. Diagnose → „Ersatzbus ergänzen“. Genau eine Änderungsentscheidung erscheint für die S5.
6. „Meldung fehlt“ und „API-Ausfall“: keine Entwarnung; vorhandene Ereignisse bleiben offen und werden als fehlend/veraltet ausgewiesen.
7. „Veraltete Daten“: keine neue Warnung oder Entwarnung aus dem alten Ergebnis.
8. „Explizite Aufhebung“: S5 bestätigt aufgehoben; U2 bleibt geplant. Dieselbe Aufhebung wiederholen: keine zusätzliche Vorschau.
9. Wieder „Testmeldungen“: dieselbe abgeschlossene S5-ID darf nicht neu aktiviert werden. Für einen frischen Durchlauf den Testzustand ausdrücklich zurücksetzen.
10. Kategorien ändern und App neu starten: Auswahl bleibt erhalten.

Die Fixtures behalten ihren beim ersten Durchlauf gesetzten Zeitanker über Neustarts. So verschieben sich Beginn/Prognoseende bei identischen Prüfungen nicht künstlich und erzeugen keine falschen Änderungen. „Testzustand zurücksetzen“ setzt auch diesen Anker zurück.

## Validierung

Build- und Lint-Ergebnisse werden im zentralen Projekt-Testbericht dokumentiert. Ein erfolgreicher APK-Build belegt die Kompilierbarkeit, nicht den Betrieb auf einem echten Android-Gerät. Die obigen Schritte sowie Samsung-/Watch-Tests bleiben bis zur tatsächlichen Durchführung offen.
