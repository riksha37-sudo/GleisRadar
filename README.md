# Linienwache Berlin – Android-MVP in Entwicklung

Native Android-App für Warnungen zu relevanten Berliner S- und U-Bahnlinien. Version 0.2 bietet eine manuelle Abfahrtsvorschau für Alexanderplatz, offizielle Betreiberlinks und einen getrennten Demomodus. Die VBB-API-Anfrage für die automatische Überwachung steht aus.

## Vorhanden

- Compose/Material 3 mit hellem und dunklem Design, Abfahrtsvorschau, Pausenorte-Ausblick, Einstellungen und Diagnose.
- Lokale Room-Persistenz für Ereigniszustand und Benachrichtigungsvorschau; DataStore für Kategorien.
- Kotlin-Kern: 5-km-Linienfilter, Standortcache, fachliche 50-m-Hybrid-Pausenlogik und Auswahl von maximal 100 Geofences.
- Austauschbare Provider und deutlich synthetische Testfälle für neue, geänderte, fehlende, veraltete und ausdrücklich aufgehobene Ereignisse.
- Ereignisdeduplizierung, 14-Tage-Planungsfenster und konservative Entwarnungsregeln.
- Automatisierte Kerntests und reproduzierbarer GTFS-Indeximport als Entwicklungswerkzeug.

Für die 5-km-Linienauswahl verwendet die App einen festen Punkt in Berlin-Mitte und den eingebauten VBB-GTFS-Stationsindex. Die Karte, der Gerätestandort, Android-Geofencing, WorkManager und Systembenachrichtigungen sind folgende Arbeitspakete.

## Bauen und prüfen

Für den Umzug auf einen Laptop und den Test per USB siehe [lokale Einrichtung](docs/LOCAL_SETUP.md).

Voraussetzungen: JDK 21, Android SDK Platform 36 und Build Tools 36.0.0. Android Studio kann das Projekt als Gradle-Projekt öffnen. Den eigenen SDK-Pfad in der nicht versionierten `local.properties` hinterlegen oder `ANDROID_HOME` setzen. Keine Zugangsdaten eintragen.

```bash
./gradlew -PcoreOnly=true :core:test
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
python3 -m unittest discover -s scripts/tests -v
```

`coreOnly` benötigt kein Android-SDK. Beim ersten Build lädt Gradle die Abhängigkeiten. Mit vollständigem Cache ist der Build offline möglich. Der Wrapper fixiert Gradle 8.13 samt SHA-256. Compose BOM 2026.06.01 liefert Compose 1.11.4 für den Build mit Android SDK 36 und AGP 8.13.2.

Die Debug-APK liegt unter `app/build/outputs/apk/debug/app-debug.apk`. Die Berichte stehen unter `app/build/reports/lint-results-debug.html` und `core/build/reports/tests/test/index.html`.

In der bereits vorbereiteten Cloud-Arbeitsumgebung vor dem Build:

```bash
source /workspace/work/toolchain/env.sh
```

Diese Datei ist nur eine lokale Umgebungshilfe, keine Voraussetzung für andere Rechner. SDK, JDK und Caches sind nicht Teil des Projekts.

## Demo ausprobieren

1. In der **Übersicht** optional **Meldungen abrufen** wählen. Die App fragt die Abfahrten am festen Bahnhof Alexanderplatz bei transport.rest ab. Der Abruf zeigt eine Stationsstichprobe.
2. Unter **Diagnose** **Testmeldungen laden** wählen. Es erscheinen zwei fiktive Meldungen und eine zusammengefasste Vorschau. Erneutes Laden erzeugt keine weitere Vorschau.
3. In **Diagnose** Ersatzbus-Ergänzung, fehlende Meldung, API-Ausfall und veraltete Daten testen. Keiner der letzten drei Fälle erzeugt eine Entwarnung.
4. **Explizite Aufhebung simulieren** wählen. Nur der ausdrücklich bestätigte Testvorgang endet. Erneutes Laden öffnet dieselbe abgeschlossene Ereignis-ID nicht wieder.
5. App schließen und erneut starten. Der gespeicherte Zustand bleibt erhalten; für einen neuen Durchlauf **Testzustand zurücksetzen** wählen.

Die Einstellungen steuern die lokale Benachrichtigungsvorschau im Demomodus. Die App erfasst keinen Gerätestandort. Die Abfahrtsvorschau wird nur auf Knopfdruck abgerufen.

## Dokumentation

- [Machbarkeit](docs/FEASIBILITY.md) und [Datenquellen-Matrix](docs/DATA_SOURCES.md)
- [Architektur](docs/ARCHITECTURE.md), [Umsetzungsplan](docs/IMPLEMENTATION_PLAN.md), [Risiken](docs/RISKS.md)
- [Bedienkonzept](docs/UI_DESIGN.md) und [Datenquellen ohne VBB-Schlüssel](docs/WEB_FALLBACK.md)
- [Android-Grenzen und Watch-Einstellungen](docs/ANDROID_CONSTRAINTS.md)
- [Bereits gestellte VBB-Anfrage: technische Anschlussfragen](docs/VBB_HANDOFF.md)
- [Tatsächlicher Prüfstand](docs/VALIDATION.md)
- [Übergabe und nächste Arbeitspakete für Sol](docs/HANDOFF_SOL.md)

## Nächster Integrationsschritt

Die offizielle VBB-Dokumentation kündigt eine Migration zum 30.11.2026 an. Eine verschwundene Meldung oder ein Prognoseende ist keine bestätigte Aufhebung. Vor Livebetrieb müssen deshalb echte Antwortbeispiele, vollständige Filter, ID-Lebenszyklen und Aufhebungssemantik geprüft werden. Zusätzlich muss ein erlaubtes Zugangsmodell ohne offen in der APK enthaltenen gemeinsamen API-Schlüssel feststehen. Es wird kein eigener Server ohne Produktentscheidung eingeführt.

Quellenangaben und Datenlizenzen stehen in der Datenquellen-Matrix. Synthetische Testereignisse stammen nicht von VBB, BVG oder S-Bahn Berlin.
