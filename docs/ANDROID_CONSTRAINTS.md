# Android: Machbarkeit, Grenzen und Geräteprüfung

Recherche: 17.11.2026. Dieses Dokument trennt dokumentierte Plattformgrenzen von
unserer vorgesehenen Strategie. Es ist **kein Nachweis eines Gerätetests**. Der
Implementierungsstand steht in `IMPLEMENTATION_PLAN.md` und im Testbericht.

## Hintergrundbetrieb

Periodische WorkManager-Aufträge erlauben mindestens 15 Minuten Abstand. Ihr
tatsächlicher Start hängt von Systemoptimierungen und Constraints ab; ein Lauf
kann sich verzögern oder ausfallen. Auch beschleunigte Einzelaufträge unterliegen
Quoten. Quelle: [Android: Define work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).
Doze kann periodische Arbeit zusätzlich verzögern:
[PeriodicWorkRequest](https://developer.android.com/reference/kotlin/androidx/work/PeriodicWorkRequest).

**Projektentscheidung für Phase 4:** Ein eindeutiger periodischer Auftrag mit
15-Minuten-Standard und Netzwerk-Constraint. Änderungen am Intervall ersetzen
nicht unkontrolliert bereits laufende Arbeit. Geofence-Austritt, erhebliche
Standortänderung und manuelle Aktualisierung bündeln sich zu einem eindeutigen
Einzelauftrag. Ein pro Prozess arbeitender Netzwerk-Callback kann zusätzliche
Prüfungen anstoßen; nach Prozessende verlässt sich die App auf WorkManagers
Netzwerk-Constraint. Keine Zusage einer sofortigen Ausführung. Retry mit Backoff,
begrenzte Laufzeiten, keine endlose Polling-Schleife, kein permanenter Foreground
Service und keine exakten Alarmrechte.

Innerhalb bestätigter Pausenorte werden keine neuen regulären Warnungen erzeugt.
Bereits benachrichtigte offene Ereignisse bleiben in einer getrennten Liste und
werden mit geringerem Rhythmus, zunächst 60 Minuten, weiterverfolgt. Dieser Wert
ist eine Produktentscheidung, keine Android-Garantie. Eine bei der Wiederaufnahme
fällige Prüfung läuft bestmöglich; Datenalter und letzter erfolgreicher Abruf
bleiben sichtbar. Ein erzwungenes Beenden durch den Nutzer oder herstellerspezifische
Beschränkungen dürfen nicht als normaler, zuverlässiger Hintergrundbetrieb gelten.

## Geofencing und 50 Meter

Die Android-Dokumentation nennt maximal 100 registrierte Geofences je App und
Gerätenutzer. Benötigt werden genaue Standortberechtigung sowie bei entsprechendem
Target ab Android 10 Hintergrundstandort. Google empfiehlt für zuverlässigere
Ergebnisse 100–150 Meter; kleine Radien und fehlende WLAN-Ortung können Übergänge
verhindern. Übergänge kommen im Hintergrund mit Verzögerung. Nach Geräte-Neustart
und `GEOFENCE_NOT_AVAILABLE` ist eine erneute Registrierung notwendig. Quelle:
[Create and monitor geofences](https://developer.android.com/develop/sensors-and-location/location/geofencing).

**Der Standardradius bleibt unverändert 50 Meter.** Es wird kein größerer Kreis
heimlich als Pausenort verwendet. Eine verlässliche scharfe 50-Meter-Grenze auf
allen Geräten ist mit diesen APIs nicht belegbar. Das ist ein Risiko für die
spätere Abnahme, kein Grund, die Produktanforderung stillschweigend zu ändern.

### Vorgesehene Hybridstrategie für Phase 3

1. Alle gespeicherten Orte bleiben in Room erhalten. Nur die nächstgelegenen
   aktivierten Orte, höchstens 100, werden beim System registriert. Aktuell
   bestätigte Aufenthaltsorte erhalten Vorrang. Nach einer relevanten Bewegung
   wird die Auswahl aktualisiert. Ohne aktuelle Position werden bereits
   registrierte Orte beibehalten und ihr Auswahlalter angezeigt. Nicht registrierte
   Orte haben keine garantierte Übergangserkennung.
2. `ENTER`, `DWELL` und `EXIT` liefern Hinweise an eine Zustandsmaschine, keine
   direkte endgültige Umschaltung. Überlappende Orte werden einzeln ausgewertet;
   erst außerhalb **aller** bestätigten Orte wird reguläre Überwachung aktiv.
3. Ergänzend werden aktuelle Fused-Location-Messungen zeitlich begrenzt und
   anlassbezogen angefordert. Zunächst ausgewogene Genauigkeit; eine begrenzte
   genauere Nachmessung nur bei einer offenen Entscheidung. Keine dauernde
   hochpräzise Ortung. Alter, horizontale Genauigkeit und monotone Messzeit sind
   Bestandteil jeder Beobachtung.
4. Die Domänenlogik erhält ausdrücklich unbekannte/ungenaue Beobachtungen. Ein
   Beispiel für eine konservative Bestätigungsregel ist: Eintritt nur, wenn der
   Unsicherheitskreis innerhalb des Radius liegt; Austritt nur, wenn er außerhalb
   liegt. Dazwischen bleibt die letzte bestätigte Entscheidung bestehen. Zwei
   konsistente, zeitlich getrennte Beobachtungen bzw. eine Aufenthaltsdauer
   verhindern Umschaltungen durch einzelne GPS-Sprünge. Der von Android gemeldete
   Genauigkeitswert ist keine mathematische Garantie.
5. Der derzeitige Domänenkern verwendet zwei unterschiedliche Messungen über
   mindestens 30 Sekunden sowie beim Austritt einen zusätzlichen Abstand von
   10 m außerhalb des Radius, jeweils unter Berücksichtigung der Messgenauigkeit.
   Der gespeicherte Standardradius bleibt 50 m; die konservative Austrittsentscheidung
   kann daher erst jenseits von 60 m plus Unsicherheit bestätigt werden. Werte für
   Mindestabstand der Messungen, maximale Messalter und Bestätigungsdauer müssen
   im Gerätetest kalibriert werden. Diagnose zeigt die tatsächlich angewandten
   Werte; fehlende Messungen dürfen keine definitive Aussage vortäuschen.
6. Fehlen Berechtigungen oder verwertbare Messungen, wird der letzte Zustand nur
   befristet weiterverwendet. Danach erscheint „Standort unbekannt / eingeschränkt“.
   Bereits gewarnte Ereignisse bleiben verfolgbar. Eine gesonderte Policy legt
   fest, ob neue Meldungen mit den zuletzt bekannten Linien noch erlaubt sind;
   weder eine erfundene Position noch „außerhalb“ wird als Fakt gesetzt.

Android beschränkt Hintergrund-Standortupdates seit API 26 auf wenige Lieferungen
pro Stunde. Die Hybridstrategie kann diese Grenze nicht aufheben.
[Background Location Limits](https://developer.android.com/about/versions/oreo/background-location-limits).
Genaue und häufige Ortung kostet mehr Energie; Fused Location verbindet mehrere
Sensorquellen. [Location and battery](https://developer.android.com/develop/sensors-and-location/location/battery).

## WLAN als ergänzendes Signal

WLAN-Bezug für Pausenorte ist Standortableitung. Deshalb darf die App kein
`neverForLocation` behaupten. `NEARBY_WIFI_DEVICES` ersetzt nicht pauschal
Standortrechte; beispielsweise benötigen Scan-APIs weiterhin genaue
Standortberechtigung. Fehlende Rechte können `SecurityException` auslösen.
[Android: Wi-Fi permissions](https://developer.android.com/develop/connectivity/wifi/wifi-permissions).

Ab API 31 müssen `NetworkCallback`-Beobachtungen mit
`FLAG_INCLUDE_LOCATION_INFO` ausdrücklich sensible Verbindungsinformationen
anfordern. Das System prüft Rechte und Standortschalter; ohne passende Freigabe
werden diese Informationen entfernt.
[NetworkCallback API](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback).
Scans können gedrosselt oder im Leerlauf unterbunden werden. Passive Beobachtung
vorhandener Scans ist möglich, beweist aber keinen jederzeitigen Hintergrundzugriff.
[Wi-Fi scanning](https://developer.android.com/develop/connectivity/wifi/wifi-scan).

**Projektentscheidung:** Optional eine vom Nutzer bestätigte WLAN-Zuordnung
lokal speichern, möglichst aus der verbundenen Basisstation; unbekannte und
ausgeblendete Kennungen strikt als fehlendes Signal behandeln. BSSID/SSID nicht
protokollieren oder hochladen. Eine lokal mit zufälligem Schlüssel erzeugte
Vergleichskennung kann Klartext in der Datenbank reduzieren, ist aber weiterhin
personenbezogen. Mesh-Wechsel, gleiche SSIDs an verschiedenen Orten und Signale
außerhalb des Radius machen WLAN allein ungeeignet, den geometrischen 50-m-Kreis
zu beweisen. Ein bekanntes WLAN unterstützt eine kürzlich geometrisch bestätigte
Pause; Verlust allein bestätigt keinen Austritt. Kopplung und Evidenzablauf sind
vor Integration mit synthetischen Sequenzen zu testen. Ohne WLAN-Rechte bleibt
der Standortpfad nutzbar; WLAN wird als nicht verfügbar angezeigt.

## Rechte, Neustart und Datenschutz

Vordergrundstandort zuerst anfordern; Hintergrundzugriff erst nach Erklärung der
automatischen Pausenfunktion. Ab Android 11 führt „immer erlauben“ über die
Systemeinstellungen. Nur ungefähr gewährter Standort wird auch im Hintergrund
nicht automatisch präzise.
[Request background location](https://developer.android.com/develop/sensors-and-location/location/permissions/background).

Benachrichtigungen benötigen ab Android 13 die Laufzeitberechtigung
`POST_NOTIFICATIONS`. Bei Ablehnung kann das Dashboard weiter arbeiten; die App
darf erfolgreiche Benachrichtigung nicht behaupten.
[Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

Geplante Wiederherstellung: gespeicherte Zonen/Registrierungszustand nach
`BOOT_COMPLETED` abgleichen, vor jedem API-Aufruf Rechte neu prüfen, Fehler lokal
diagnostizieren. Entzug von Hintergrundstandort darf weder Absturz noch erneute
stille Berechtigungsanfrage aus einem Worker auslösen. Beim nächsten sichtbaren
App-Start wird verständlich auf den eingeschränkten Betrieb hingewiesen.

Kein Nutzerkonto, keine Werbe-/Analytics-SDKs, kein eigener Server. Datenbank und
Einstellungen bleiben app-intern; automatische Cloud-Backups für Pausenorte und
WLAN-Kennungen deaktivieren. Diagnosen enthalten nur notwendige letzte Zeitpunkte
und Statusgründe, keinen Bewegungsverlauf. Google Play services führt die
Systemortung aus; deren Verwendung muss in der Datenschutzerklärung benannt
werden. Fehlende Play services sind ein eigener Diagnosezustand.

## Galaxy Watch 4

Normale Android-Benachrichtigungen können zur verbundenen Wear-OS-Uhr gespiegelt
werden; eine eigene Watch-App ist dafür nicht erforderlich. `NotificationCompat`
ist die passende Basis.
[Notifications on Wear OS](https://developer.android.com/training/wearables/notifications).

Für den Gerätetest: Galaxy Wearable öffnen → Uhreneinstellungen →
Benachrichtigungen → App-Benachrichtigungen → ÖPNV-Störungswächter aktivieren.
Die genauen Menünamen variieren nach Modell/Software. Zusätzlich die
Android-Berechtigung und die gewünschten Kanäle freigeben; Uhr koppeln und
Nicht-stören-/Schlafmodus berücksichtigen.
[Samsung: Manage notifications](https://www.samsung.com/uk/support/mobile-devices/manage-notifications-on-galaxy-watch/).

Vorgesehen sind vier Kanäle: akut, geplant, wesentliche Änderung, Entwarnung.
Kurzer Titel mit Linie, kompakter Abschnitt im Text, Details auf dem Telefon.
Eine persistente Ereigniskennung ordnet Updates derselben Meldung zu. Die Uhr
muss Warnung, Update, Entwarnung sowie Entfernen/Ersetzen auf beiden Geräten
praktisch bestehen; eine API-Dokumentation bestätigt dieses konkrete Verhalten
auf einer Galaxy Watch 4 noch nicht.

## Karte und Tile-Nutzungsbedingungen

MapLibre Native ist eine geeignete Android-Kartenbibliothek unter BSD-2-Clause;
diese Lizenz gewährt nicht automatisch Rechte an Kartenstilen oder Tiles.
[MapLibre Android](https://maplibre.org/maplibre-native/android/examples/).

OSM-Standardtiles haben keine Verfügbarkeitsgarantie. Erforderlich sind sichtbare
Attribution, identifizierbarer User-Agent und regelkonformes Caching. Bulk- und
Offline-Vorabladen sind untersagt. Die konkrete Policy gilt für den jeweiligen
Tile-Dienst; Raster- und Vektordienst haben getrennte Regeln.
[OSMF Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/).

**Entscheidung für Phase 1:** Kartenanbieter nicht als versteckten kostenpflichtigen
Dienst einschalten. Phase 3 bekommt einen austauschbaren Style/Tile-Endpunkt,
Attribution und Cache. Für einen begrenzten manuellen Prototyptest kommen
regelkonforme OSM-Rastertiles infrage; vor öffentlichem Betrieb werden Last,
Verfügbarkeit, Datenschutz und gegebenenfalls kostenloser Tarif gesondert
entschieden. Kartenabrufe verraten dem Tile-Anbieter IP-Adresse und betrachteten
Kartenausschnitt. Keine Kartenanfragen im Hintergrund. Eine Koordinaten-Eingabe
ersetzt nicht das geforderte Karten-Abnahmekriterium.

## Geprüfte Versionskandidaten

Die folgenden Angaben stammen aus den am 17.11.2026 gelesenen offiziellen
Dokumentationen. Sie ersetzen keine erfolgreiche Maven-Auflösung oder einen
Projektbuild. Die tatsächlich gewählten Pins stehen in den Gradle-Dateien;
abweichende Bootstrap-Versionen müssen im Buildbericht begründet werden.

| Baustein | Dokumentierter Kandidat | Nachweis / Einschränkung |
|---|---|---|
| AGP, konservative Reihe | 8.13.2 | [Release notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes): Gradle 8.13, JDK 17, höchstens API 36.1, Kotlin 2.3 in D8/R8 unterstützt |
| Kotlin für AGP 8.13 | 2.3.21 | [KGP-Matrix](https://kotlinlang.org/docs/gradle-configure-project.html): Gradle 7.6.3–9.3.0, AGP 8.2.2–9.0.0 vollständig unterstützt |
| Neuerer Kotlin-Kandidat | 2.4.21 | Dieselbe Matrix: Gradle bis 9.7.0, AGP bis 9.3.1; zusätzliche Android-D8/R8-Mindestversion beachten |
| Neuere AGP-Reihe | 9.3.x | [Release notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes): Gradle 9.5.0, JDK 17, API 37; 9.3.2 behebt einen Lintfehler auf JDK 17. AGP 9 hat eingebautes Kotlin: Plugin-Konfiguration nicht unverändert von AGP 8 übernehmen |
| Compose BOM | 2026.09.00 | [Compose BOM](https://developer.android.com/develop/ui/compose/bom); BOM koordiniert Bibliotheken, nicht Kotlin-Compiler |
| Compose-Compiler | exakt zur Kotlin-Version | [Compiler plugin](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler) |
| Room | 2.8.5 | [Room releases](https://developer.android.com/jetpack/androidx/releases/room); KSP für Kotlin, Schemaexport vorsehen |
| WorkManager | 2.12.0 | [WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work); minSdk 24 |
| DataStore | 1.3.0 | [DataStore releases](https://developer.android.com/jetpack/androidx/releases/datastore) |

Für das kleine JVM-Domänenmodul ist Android SDK nicht erforderlich. So bleiben
Regeltests auch bei fehlendem SDK ausführbar. Android-Runtime, Room-Codegen,
Compose und Hintergrundintegration müssen separat gebaut und getestet werden.

## Noch offene physische Prüfung

| Versuch | Zu protokollieren | Status |
|---|---|---|
| Je mindestens 20 Ein-/Austritte an zwei 50-m-Orten | tatsächliche Grenze, Empfangszeit, Messgenauigkeit, Fehlübergänge | nicht durchgeführt |
| Überlappende Zonen, kurzer Vorbeigang, GPS-Sprung | Pause stabil, kein vorzeitiger Austritt | nicht durchgeführt |
| Indoor, WLAN aus, Mesh-Wechsel, Standortschalter aus | Signalqualität und kontrollierter unbekannter Zustand | nicht durchgeführt |
| Display aus, Doze, Energiesparen, App geschlossen | Job-Latenz, Datenalter, Batterie in 24-h-Vergleich | nicht durchgeführt |
| Geräte-Neustart, Rechteentzug, Play-services-Ausfall | Wiederregistrierung, keine Abstürze, klarer Diagnosezustand | nicht durchgeführt |
| Über 100 gespeicherte Orte | Erhalt aller Orte, Auswahlwechsel und erkennbare Abdeckungslücken | nicht durchgeführt |
| Galaxy Watch 4 | Titel/Abschnitt lesbar, Updates, Entwarnung, Entfernen | nicht durchgeführt |

Ein synthetischer Test kann die Regel der Zustandsmaschine bestätigen. Er kann
weder Funkverfügbarkeit noch Android-Latenz oder Akkulaufzeit beweisen.
