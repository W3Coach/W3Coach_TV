# W3Coach TV – Installationsanleitung
## Google TV Streamer (Kirkwood)

**Version:** 1.x.x  
**Paket:** `com.w3coach.w3coachtv`  
**Voraussetzungen:** Windows-PC mit ADB, signierte APK

---

## 1. Google TV Streamer vorbereiten

### 1.1 Ersteinrichtung durchführen
Den Setup-Wizard **vollständig** durchlaufen:
- Sprache wählen
- WLAN einrichten
- Google-Konto einrichten (zwingend erforderlich um ADB freizuschalten)
- Alle weiteren Setup-Schritte abschließen

⚠️ Den Setup-Wizard **nicht** abbrechen – ADB ist erst nach vollständiger Einrichtung verfügbar.

---

### 1.2 Entwickleroptionen aktivieren

1. `Einstellungen` öffnen
2. `System` → `Info`
3. Auf **„Android TV OS Build"** **7x** drücken
4. Meldung „Du bist jetzt Entwickler" erscheint

---

### 1.3 ADB aktivieren

1. `Einstellungen` → `System` → `Entwickleroptionen`
2. **„ADB-Debugging"** aktivieren
3. **„ADB über Netzwerk"** aktivieren (optional, für WLAN-ADB)

---

### 1.4 Google-Konto entfernen

⚠️ Dieser Schritt ist zwingend erforderlich, da `set-device-owner` mit aktivem Google-Konto fehlschlägt.

1. `Einstellungen` → `Konten & Anmelden`
2. Google-Konto auswählen
3. **„Konto entfernen"** → bestätigen

---

### 1.5 Setup-Wizard deaktivieren

Am PC in der Eingabeaufforderung:
```
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

> Verhindert dass der Setup-Wizard nach dem Konto-Entfernen erneut startet.

---

## 2. W3Coach TV installieren

### 2.1 APK installieren
```
adb install w3coachtv-1.x.x-release.apk
```

### 2.2 Device Owner setzen
```
adb shell dpm set-device-owner com.w3coach.w3coachtv/.KioskAdminReceiver
```

Erwartete Ausgabe:
```
Success: Device owner set to package com.w3coach.w3coachtv/.KioskAdminReceiver
Active admin set to component com.w3coach.w3coachtv/.KioskAdminReceiver
```

### 2.3 App starten
```
adb shell am start -n com.w3coach.w3coachtv/.MainActivity
```

---

## 3. Ersteinrichtung in der App

Nach dem ersten Start führt die App automatisch durch folgende Schritte:

### 3.1 Berechtigungen (einmalig)

Die App fordert nacheinander folgende Berechtigungen an:

**1. Einblendung über anderen Apps (Overlay)**
- Dialog erscheint → **„Erteilen"** drücken
- System-Einstellungen öffnen sich
- W3Coach TV aktivieren
- **Back** drücken → App prüft automatisch weiter

**2. Installation unbekannter Apps**
- Dialog erscheint → **„Erteilen"** drücken
- System-Einstellungen öffnen sich
- **„Apps aus dieser Quelle erlauben"** aktivieren
- **Back** drücken → App prüft automatisch weiter

**3. Bedienungshilfen**
- Dialog erscheint → **„Erteilen"** drücken
- Bedienungshilfen-Einstellungen öffnen sich
- **„W3Coach TV"** auswählen und aktivieren
- **Back** drücken → App prüft automatisch weiter

### 3.2 URLs konfigurieren

Nach den Berechtigungen öffnet sich das Kontextmenü automatisch (da noch keine URL konfiguriert ist).

**Kontextmenü öffnen:** Long-Press auf **OK/Enter** (ca. 0,6 Sekunden)

**URL 1 – 3 konfigurieren** (PIN erforderlich – Standard: `0000`):
- Menüpunkt **„URL 1"** wählen
- PIN eingeben
- Nur den Suffix nach `https://w3coach.de/` eingeben, z.B. `w3c`
- **„Speichern"** drücken
- URL 2 und URL 3 analog konfigurieren

### 3.3 Auto-Update konfigurieren (empfohlen)

Im Kontextmenü → **„Auto-Update"** (kein PIN):
- **Auto-Update** aktivieren
- **Intervall** in Stunden eintragen (empfohlen: `4`)
- **„Speichern"** drücken

---

## 4. Fernbedienung – Bedienung

| Taste | Funktion |
|---|---|
| **OK** (kurz) | Klick in der WebApp |
| **OK** (lang, ~0,6s) | Kontextmenü öffnen |
| **Back** | URL wechseln (URL1 → URL2 → URL3 → URL1) |
| **D-Pad** | Scrollen in der WebApp |

---

## 5. Kontextmenü – Übersicht

| Menüpunkt | PIN | Funktion |
|---|---|---|
| URL 1 / 2 / 3 | ✅ | URL bearbeiten |
| Zoom | ❌ | Darstellungsgröße 75%–125% |
| Auto-Update | ❌ | Update-Intervall konfigurieren |
| Systemeinstellungen | ✅ | Android-Einstellungen öffnen |
| Neustart | ✅ | Gerät neu starten |
| Info | ❌ | Version, Gerät, IP, TeamViewer |

> Standard-PIN: `0000` – wird in `gradle.properties` als `MENU_PIN` konfiguriert.

---

## 6. TeamViewer QuickSupport (Fernwartung)

Über **Kontextmenü → Info → „QuickSupport"**:

1. Beim ersten Aufruf wird **TeamViewer QuickSupport** aus den App-Assets installiert
2. Danach wird das **TeamViewer Addon** installiert
3. Das Addon muss einmalig in den **Bedienungshilfen** aktiviert werden:
   - Dialog erscheint → **„Erteilen"** drücken
   - Addon in den Bedienungshilfen aktivieren
   - **Back** drücken
4. QuickSupport startet automatisch

---

## 7. Neuinstallation / Factory Reset

Bei einem Factory Reset muss der gesamte Ablauf ab **Abschnitt 1** wiederholt werden.

⚠️ Nach einem Factory Reset ist der Device Owner nicht mehr gesetzt. `set-device-owner` muss erneut ausgeführt werden.

**Factory Reset per Hardware:**
1. Netzkabel ziehen
2. Reset-Knopf auf der Rückseite gedrückt halten
3. Netzkabel einstecken – Knopf weiter halten
4. Warten bis LED **weiß blinkt** (~10-15 Sekunden)
5. Loslassen

---

## 8. Troubleshooting

### `set-device-owner` schlägt fehl
**Fehler:** `Not allowed to set the device owner`  
**Ursache:** Google-Konto noch aktiv  
**Lösung:** Konto entfernen (Abschnitt 1.4) und erneut versuchen

### Setup-Wizard erscheint nach Neustart
**Ursache:** `pm disable-user` überlebt Neustart nicht immer  
**Lösung:** Ab Version 1.x deaktiviert die App den Setup-Wizard automatisch beim Boot über `DevicePolicyManager`

### WLAN deaktiviert nach Setup-Schließen
**Lösung:**
```
adb shell svc wifi enable
```

### App startet nicht nach Installation
**Lösung:**
```
adb shell am start -n com.w3coach.w3coachtv/.MainActivity
```

### Device Owner entfernen (für Neuinstallation)
Nur möglich per Factory Reset – siehe Abschnitt 7.

---

## 9. ADB-Referenz

```
# App installieren
adb install w3coachtv-1.x.x-release.apk

# Device Owner setzen
adb shell dpm set-device-owner com.w3coach.w3coachtv/.KioskAdminReceiver

# Device Owner prüfen
adb shell dpm list-owners

# App starten
adb shell am start -n com.w3coach.w3coachtv/.MainActivity

# Setup-Wizard deaktivieren
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith

# WLAN aktivieren
adb shell svc wifi enable

# Auto-Update Job manuell auslösen (Test)
adb shell cmd jobscheduler run -f com.w3coach.w3coachtv 42

# Logs beobachten
adb logcat -s AutoUpdateJob GithubUpdateChecker SilentInstaller InstallResultReceiver
```
