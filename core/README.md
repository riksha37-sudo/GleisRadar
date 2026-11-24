# Fachlicher Kern

Dieses JVM-Modul enthält keine Android-Berechtigungs-, Sensor-, Netzwerk- oder
NotificationManager-Implementierung. Es lässt sich ohne Android SDK prüfen:

```sh
./gradlew -PcoreOnly=true :core:test
```

## Verträge

- `LineSelector` bildet Stationen innerhalb von 5.000 m auf vollständige, kanonische
  S-/U-Bahnlinien ab. Der Provider normalisiert Linienvarianten vorab. `CachedLineSelector`
  rechnet ab 250 m Bewegung, nach 15 Minuten oder bei einer neuen Katalogversion neu.
  Ohne neuen brauchbaren Standort wird der letzte Zustand höchstens 30 Minuten verwendet.
  Diese Grenzwerte sind Implementierungsentscheidungen, keine gemessenen Gerätezusagen.
- `HybridPauseEngine` belässt den Standardradius bei 50 m. Für einen Wechsel sind
  zwei verschiedene, hinreichend genaue Messungen über mindestens 30 Sekunden nötig.
  Eintritt verlangt eine vollständig innerhalb liegende Genauigkeitsfläche; Austritt
  zusätzlich 10 m Hysterese. WLAN unterstützt eine bereits räumlich bestätigte Messung,
  ersetzt sie aber nicht. Unsicherheit, fehlende Berechtigung oder alte Positionen
  ergeben `UNKNOWN` beziehungsweise einen auf fünf Minuten begrenzten letzten Zustand.
  `UNKNOWN` darf die App nicht als sicher außerhalb eines Pausenorts darstellen.
- `selectGeofences` wählt höchstens 100 aktivierte Zonen. Alle übrigen Orte bleiben
  im Repository gespeichert. Diese Funktion registriert selbst keine Android-Geofences.
- `TrackingEngine` ist ein reiner Zustandsübergang. Der Aufrufer muss Zustand und
  erzeugte Meldungsaufträge atomar speichern. Erst danach dürfen Aufträge zugestellt
  werden. Die Demo-App protokolliert Vorschauen; dies beweist keine Systemzustellung.
- Kategorien werden über `TrackingContext.enabledNotificationKinds` **vor** dem
  Setzen des Benachrichtigungsmerkers gefiltert. `SUMMARY` erlaubt die Gruppierung
  bereits erlaubter neuer Warnungen; es macht deaktivierte Kategorien nicht sichtbar.
- Provider-IDs sind namensraumgebunden. Identische Kennungen verschiedener Quellen
  werden nicht zusammengeführt. Gleich datierte widersprüchliche Versionen einschließlich
  widersprüchlicher Aufhebungsbelege werden verworfen beziehungsweise als unklar markiert.
- Ein bestätigtes Ende verlangt `RESOLVED`, `ResolutionEvidence` derselben Quelle,
  zulässige Zeitstempel und eine explizit verifizierte Aufhebungsfähigkeit des Adapters.
  Verschwindende Meldungen, Fehler, prognostizierte Enden und veraltete Antworten lösen
  niemals eine Entwarnung aus. Eine geschlossene Ereignisidentität wird nicht wieder geöffnet;
  ein tatsächlich neues Vorkommnis benötigt eine neue normalisierte Identität.
- Ein planmäßiger Beginn darf zeitbasiert als erreicht gelten. Dieser Übergang trägt
  `activationDerivedFromSchedule=true` und den Meldungstitel „Beginn laut Planung“;
  er bestätigt keinen vom Provider beobachteten Betriebszustand.
- `TrackingStateCodec` ist ein versioniertes, explizites Binärformat ohne Java-
  Objektdeserialisierung. Unbekannte Versionen und beschädigte Daten müssen als Diagnose
  sichtbar werden. Das Ledger darf dann nicht stillschweigend gelöscht werden.

## Entwicklungsdaten

`VbbProvider` liefert ausschließlich einen ausdrücklich nicht konfigurierten Zustand.
Es gibt keinen erfundenen Live-Endpunkt und keinen API-Schlüssel im Modul.
`FixtureProvider` verwendet synthetische Ereignisse mit einem expliziten, unveränderten
`anchor`. Die Demo-App muss diesen Anker speichern und wiederverwenden. Ein späteres
Abrufdatum ändert dadurch nicht die behaupteten Bau- oder Störungszeiträume.

Die ausführbaren Tests decken Fachlogik, Grenzen und einen Codec-Restart ab. Echte
Android-Neustarts, Doze, Sensorwerte, Akkulaufzeit und Galaxy-Watch-Spiegelung benötigen
separate Integrations- und Gerätetests.
