# Linienwache Berlin auf dem Laptop

Klone das Repository mit `git clone https://github.com/riksha37-sudo/GleisRadar.git` und öffne den Ordner `GleisRadar` in Android Studio. Falls du das Projektarchiv verwendest, öffne stattdessen dessen Unterordner `berlin-stoerungswaechter`. Wähle für Gradle ein JDK 21. Installiere im SDK Manager Android SDK Platform 36, Build Tools 36.0.0 und die aktuellen Platform Tools. Android Studio legt `local.properties` mit dem lokalen SDK-Pfad an. Für einen Build im Terminal kannst du stattdessen `ANDROID_HOME` verwenden.

Der Gradle-Wrapper lädt Gradle 8.13 und prüft dessen SHA-256. Der erste Build benötigt Zugriff auf Google Maven, Maven Central und das Gradle Plugin Portal. Die Cloud-Toolchain unter `/workspace/work/toolchain` wird lokal nicht benötigt.

## Bauen und prüfen

macOS und Linux

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
python3 -m unittest discover -s scripts/tests -v
```

Windows PowerShell

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
py -3 -m unittest discover -s scripts/tests -v
```

## Pixel 9

Entsperre das Pixel und bestätige den Dialog für USB-Debugging. Wähle in Android Studio das Pixel 9 als Ziel und starte die Konfiguration `app`. Alternativ kannst du ADB aus den Platform Tools aufrufen.

```text
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n de.berlin.transitwatch/.MainActivity
```

Nur im Übergabearchiv liegt zusätzlich die bereits geprüfte Cloud-APK unter `transfer/app-debug.apk`. Für diesen Build verwende `adb install -r transfer/app-debug.apk`. Cloud und Laptop können unterschiedliche Debug-Schlüssel verwenden. Falls ADB beim Wechsel zwischen den Builds einen Signaturkonflikt meldet, behalte zunächst die vorhandene Installation. Eine Deinstallation würde deren lokale Testdaten löschen.

## Arbeit fortsetzen

Die Übergabe steht in [HANDOFF_SOL.md](HANDOFF_SOL.md) und der bisherige Prüfstand in [VALIDATION.md](VALIDATION.md). Für den Test auf dem Pixel verwende den Ablauf in [APP_NOTES.md](APP_NOTES.md).

`app/src/main/assets/station-index.json` ist der statische VBB-Index für die Testregion um Berlin-Mitte. Sein Auswahlfenster ist 17.11.–01.12.2026. Er enthält Quellenangabe, Bearbeitungshinweis, Lizenz-URL und Prüfsumme des Ursprungsfeeds. Die Erzeugung mit einer aktuellen GTFS-Datei ist in [scripts/README.md](../scripts/README.md) beschrieben.

Fahrplandaten von der Verkehrsverbund Berlin-Brandenburg GmbH (VBB). Bearbeitung durch Filterung auf Berliner S- und U-Bahn und Bildung eines Stations-/Linienindex. [Quelle](https://unternehmen.vbb.de/digitale-services/datensaetze/) · [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)
