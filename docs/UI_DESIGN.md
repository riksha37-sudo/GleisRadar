# Bedienkonzept für Linienwache Berlin

Die Übersicht beantwortet drei Fragen in dieser Reihenfolge: Welche Station wird geprüft, wie alt ist das Ergebnis und wo steht die offizielle Meldung? Der feste Bahnhof Alexanderplatz und die Quelle stehen direkt beim Abruf. Der Stationsindex beantwortet eine andere Frage. Er zeigt darunter, welche Linien im 5-km-Radius um Berlin-Mitte liegen.

## In Version 0.2 umgesetzt

- Material 3 mit hellen und dunklen Farben nach Geräteeinstellung, klaren Überschriften, ausreichend großen Schaltflächen und eigenen Symbolen für die Navigation. Linien unterscheiden sich durch Bezeichnungen und Farbe.
- Feste Leiste für Übersicht, Pausen, Optionen und Diagnose. Die Scrollposition bleibt beim Wechsel erhalten. Die Übersicht hat eine Hauptaktion und getrennte Links zu den Betreibern.
- Ergebnisse mit Abrufzeit, S-/U-Bahn-Filtern und aufklappbaren Details. Alte oder fehlende Warnhinweise tragen einen sichtbaren Unsicherheitsstatus. Bei Verbindungsfehler bleibt der letzte erfolgreiche Abruf sichtbar.
- Die Diagnose enthält die simulierten Fälle und ihren Verlauf. Einstellungen für Test-Benachrichtigungen bleiben dort fachlich getrennt von der manuellen Vorschau.

## Nächste Ansichten

1. **Pausenorte.** Eine Liste zeigt Namen, Radius und den Status je Ort. „Ort hinzufügen“ öffnet die Karte. Das Verschieben des Markers und der Radius sind vor dem Speichern sichtbar. Die Vorschau startet in Berlin. „Mein Standort“ ist eine separate Aktion mit kontextbezogener Freigabe. Bearbeiten und Löschen erfolgen direkt am betreffenden Ort.
2. **Überwachungsstatus.** Sobald die Android-Adapter verfügbar sind, zeigt die Übersicht aktive Überwachung, eine bestätigte Pause oder unbekannten Standort als Text. Der letzte erfolgreiche Abruf und die Datenquelle bleiben neben dem Status sichtbar.
3. **Meldungsdetails.** Eine echte, verifizierte Quelle liefert die betroffene Linie, Abschnitt, Zeitraum und gegebenenfalls Ersatzverkehr. Eine bestätigte Aufhebung erhält ihren eigenen Status. Für Benachrichtigungen auf dem Pixel und auf der Uhr stehen Linie und Kernaussage zuerst.

## Abnahme auf dem Pixel

- Übersicht bei hellem und dunklem Systemdesign und mit großer Schrift prüfen. Hauptaktion und Navigation müssen ohne abgeschnittene Beschriftung bedienbar bleiben.
- Mit TalkBack die Reihenfolge von Überschrift, Abruf, Ergebnis und Betreiberlinks prüfen. Statusmeldungen müssen auch ohne Farberkennung verständlich sein.
- Einen Abruf durchführen, beide Filter umschalten und Details öffnen. Mit Flugmodus erneut abrufen. Der Fehler muss sichtbar sein und der letzte erfolgreiche Stand erhalten bleiben.
- In Diagnose „Testmeldungen laden“, „Meldung fehlt“ und „Explizite Aufhebung simulieren“ prüfen. Allein die explizite Simulation kann die Test-Entwarnung auslösen.

Der automatisierte Android-Test und die Grenzen der Prüfung stehen in [VALIDATION.md](VALIDATION.md).
