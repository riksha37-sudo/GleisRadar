# Prüfstand

Stand: 21.11.2026. Version 0.2 zeigt eine manuell abgerufene Stationsstichprobe, den lokalen Fahrplanindex und getrennte synthetische Meldungen in der Diagnose.

## Reproduzierbare Befehle

```bash
./gradlew --no-daemon :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
python3 -m unittest discover -s scripts/tests -v
```

In der vorbereiteten Cloud-Umgebung vorher `source /workspace/work/toolchain/env.sh`. Auf anderen Rechnern JDK 21 und Android-SDK 36 einrichten. Keine Schlüssel erforderlich.

## Automatisierte Abdeckung

| Bereich | Geprüfte Regeln | Aussagegrenze |
| --- | --- | --- |
| Linienfilter | innerhalb/exakt auf/außerhalb 5 km, mehrere Linien, S/U statt Bus, ganze Linie trotz entferntem Abschnitt, Bewegung und Katalogwechsel | synthetische Stationen; keine Vollständigkeitsgarantie für Live-Netz |
| Standortcache | begrenztes Alter, fehlende/ungenaue/alte/zukünftige Fixes, kontrollierte Wiederverwendung | keine Fused-Location-Integration |
| Pausenlogik | 50-m-Standard, stabiler Eintritt/Austritt, Überlappung, GPS-Sprung, WLAN allein unzureichend, fehlende Rechte, ungenaue Messungen | fachliche Zustandsmaschine; keine Android-Funkmessung |
| Geofence-Auswahl | höchstens 100, größere umschließende Zonen berücksichtigt, gespeicherte Liste unverändert | keine tatsächliche Systemregistrierung |
| Ereignisse | Wiederholung, relevante Änderung/SEV, 14-Tage-Planung, abgeleiteter Beginn, Ausfall, veraltete/fremde/widersprüchliche Antworten | ausschließlich Fixture-Verträge |
| Entwarnung | nur explizite passende Evidenz eines hierfür freigegebenen Providers; kein Ende durch Fehlen/Prognose; keine Wiedereröffnung terminaler Identität | keine echte VBB-Aufhebung bestätigt |
| Meldungsstand | Inhaltsfingerabdruck, initiale Zusammenfassung, Codec-Roundtrip/Neustartsimulation, Kategorien vor Verbuchung, keine Entwarnung ohne vorherige Warnung | Vorschauentscheidungen; keine Android-Zustellung |
| GTFS-Import | Varianten, Richtung, Betreiber-/Produktfilter, Kalender und Ausnahmen, Elternreferenz, Koordinaten, defekte Referenzen, atomare Ausgabe | separat erzeugter Index, noch nicht App-Ressource |

## Noch nicht durchgeführt

- App-Start, Compose-Bedienung und Room-Roundtrip auf Emulator oder physischem Android-Gerät.
- Karten-CRUD, reale Standortberechtigungen, Google-Geofencing und WLAN-Adapter.
- WorkManager unter Doze, App Standby, Prozessende, Geräte-Neustart und Internetverlust.
- Echte Notification Channels, Android-Zustellung und Spiegelung auf einer Galaxy Watch 4.
- Akkumessungen und reale 50-m-Ein-/Austrittsmessungen.
- Authentifizierte VBB-/HIM-Vertragstests und produktive Entwarnung.

Die öffentlichen Datenquellen-Stichproben sind in `DATA_SOURCES.md` mit Herkunft, Zeit und Grenzen dokumentiert. Die CI-Datei ist vorbereitet; ein entfernter CI-Lauf wurde nicht gestartet.

## Ausführungsprotokoll für Version 0.1

| Prüfung | Tatsächliches Ergebnis am 17.11.2026 |
| --- | --- |
| Kotlin/JVM-Kern | 45 Tests neu ausgeführt, davon 20 Geography und 25 TrackingEngine. 0 Fehler, 0 übersprungen. XML unter `core/build/test-results/test/`. |
| Python-GTFS-Importer | 15 Tests erfolgreich. |
| Vollständiger offizieller GTFS-Import | 5.785.831 Haltezeilen, 57 Routenvarianten, 17.223 Fahrten im gewählten Zeitfenster, 736 bediente Haltepunkte, 25 Linien. |
| Android Debug-Build | `:app:assembleDebug` erfolgreich mit Compose BOM 2026.06.01, Compose 1.11.4, AGP 8.13.2, Gradle 8.13, Kotlin 2.3.21 und SDK 36. Room-Schema v1 wurde exportiert. |
| Android-Lint | `:app:lintDebug` erfolgreich mit **0 Fehlern und 8 Warnungen**. HTML-Bericht unter `app/build/reports/lint-results-debug.html`. |
| Debug-APK | `app/build/outputs/apk/debug/app-debug.apk`, 13.296.687 Byte, Version 0.1.0-demo, Paket `de.berlin.transitwatch`. APK-Signatur v2 mit `apksigner` verifiziert. |
| Gerät / Emulator / Watch | Nicht getestet. |

Arbeitspaket 1 ist abgeschlossen. Compose 1.12 verlangt laut AAR-Metadaten SDK 37 und AGP ab 9.1. Compose 1.11.4 lässt sich mit der bestehenden Toolchain bauen. Das Theme setzt `android:windowLightNavigationBar` über `values-v27` ab API 27. Die App unterstützt weiterhin Geräte ab API 26.

Die acht Lint-Warnungen betreffen neuere Bibliotheksversionen (fünf Hinweise), KSP als Alternative zu Kapt, fehlende `dataExtractionRules` für Android 12 und höher sowie ein fehlendes eigenes App-Symbol. Der erfolgreiche Gradle-Lauf steht in `/workspace/work/android-package1-final.log`.

SHA-256 der geprüften APK

```text
2a7794f5916def06db8dd280f935c8feaa7245f5ae7bda4265b3b713032d9c7f
```

Der erzeugte GTFS-Index liegt separat unter `/workspace/work/source-audit/station-index.json`, mit Quellenhash, CC-BY-4.0-Attribution und Auswahlzeitraum 17.11.–01.12.2026. Er ist nicht in die App eingebunden. Ein späterer Arbeitsstand muss den Zeitraum aktualisieren; 736 bezeichnet bediente Plattformen/Haltestellen, nicht 736 unterschiedliche Stationskomplexe.

Konkrete Fortsetzung: `HANDOFF_SOL.md`.

## Laptop-Übergabe am 21.11.2026

Das Archiv `Linienwache-Berlin-Laptop.zip` enthält Quellcode, Wrapper, Dokumentation, die signierte Debug-APK und den abgeleiteten Stationsindex. In einem frisch entpackten Verzeichnis lief `./gradlew --offline --no-daemon :core:test :app:assembleDebug :app:lintDebug` mit der vorbereiteten Cloud-Toolchain erfolgreich. Der Gradle-Cache lieferte das Ergebnis der 45 Kerntests. Die 15 Importer-Tests wurden neu ausgeführt. Offline-Lint meldete 0 Fehler und 4 Warnungen. Die Signaturen der mitgelieferten und der neu gebauten APKs wurden geprüft.

Der Build direkt auf dem Laptop und der USB-Test auf dem Pixel 9 stehen aus. Einrichtungs- und Installationsschritte stehen in [LOCAL_SETUP.md](LOCAL_SETUP.md).

## Version 0.2.0-preview am 21.11.2026

| Prüfung | Ergebnis |
| --- | --- |
| Gradle auf JDK 21 und SDK 36 | `:core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` erfolgreich. |
| App- und Kerntests | 45 Kerntests, 6 Datentests für Index und Vorschau, 1 Compose-Start-/Navigationstest unter Robolectric mit API 35. Keine Fehler oder übersprungenen Fälle. |
| GTFS-Importer und Asset | 15 Importer-Tests bestanden. Die SHA-256 im Index stimmt mit dem Rohfeed überein. Das APK-Asset stimmt bytegenau mit dem Index im Projekt überein. |
| Aktueller API-Abruf | Der kompilierte HTTPS-Client lieferte am Alexanderplatz um 01:22 MEZ 27 Bahnabfahrten und 1 eindeutigen Warnhinweis. Das ist eine einzelne Stichprobe. |
| Bedienung | Der Compose-Test prüfte den Start, die Abrufschaltfläche, zwei sichtbare Betreiberlinks und den Wechsel Übersicht → Diagnose → Übersicht. Fünf ausgewählte Text-/Hintergrundpaare je Theme erreichten mindestens 6,46:1 im hellen bzw. 7,43:1 im dunklen Design. |
| Lint | 0 Fehler, 12 Warnungen. Sie betreffen Bibliotheksupdates, Kapt/KSP, Backup-Regeln und das Launcher-Icon. Bericht unter `app/build/reports/lint-results-debug.html`. |
| Debug-APK | `app/build/outputs/apk/debug/app-debug.apk`, 13.357.069 Byte. Signaturschema v2 verifiziert. Geprüfte Version `0.2.0-preview`, minSdk 26. |

Der finale Gradle-Log liegt unter `/workspace/work/android-preview-check.log`. SHA-256 der Debug-APK `d48a055a794767315ca15d999d6203e0b2b2fe64901617300a58fbf4250d6ee1`.

Die Bedienung auf dem echten Pixel 9, TalkBack, große Schrift und das Verhalten unter Flugmodus stehen noch zur Abnahme aus. Die Schritte stehen in [UI_DESIGN.md](UI_DESIGN.md).
