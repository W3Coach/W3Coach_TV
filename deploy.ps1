#Requires -Version 5.1
<#
.SYNOPSIS
    W3Coach TV - Interaktives Deployment-Script
.DESCRIPTION
    Fuehrt durch die vollstaendige Erstinstallation auf einem neuen
    Google TV Streamer. Automatisiert alle ADB-Befehle und gibt
    Hinweise fuer manuelle Schritte am Geraet.
.NOTES
    Voraussetzungen:
    - ADB im PATH oder im selben Verzeichnis wie dieses Script
    - w3coachtv-*.apk im selben Verzeichnis wie dieses Script
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step    { param($n, $text) Write-Host "`n[$n] $text" -ForegroundColor Cyan }
function Write-OK      { param($text) Write-Host "  [OK]  $text" -ForegroundColor Green }
function Write-Hint    { param($text) Write-Host "  [>>]  $text" -ForegroundColor Yellow }
function Write-Waiting { param($text) Write-Host "  [...] $text" -ForegroundColor DarkCyan }
function Write-Err     { param($text) Write-Host "  [!!]  $text" -ForegroundColor Red }

function Pause-ForUser {
    param([string]$message = "Druecke ENTER wenn fertig...")
    Write-Host ""
    Write-Host "  $message" -ForegroundColor Magenta -NoNewline
    Read-Host
}

function Confirm-Step {
    param([string]$question)
    Write-Host ""
    Write-Host "  $question [J/N]: " -ForegroundColor Magenta -NoNewline
    $answer = Read-Host
    return $answer -match '^[JjYy]'
}

function Wait-ForDevice {
    Write-Waiting "Warte auf ADB-Verbindung..."
    $timeout = 60
    $elapsed = 0
    while ($elapsed -lt $timeout) {
        $devices = & adb devices 2>&1 | Select-String "device$"
        if ($devices) {
            Write-OK "Geraet verbunden"
            return $true
        }
        Start-Sleep -Seconds 2
        $elapsed += 2
        Write-Host "." -NoNewline -ForegroundColor DarkGray
    }
    Write-Host ""
    Write-Err "Timeout: Kein Geraet gefunden nach $timeout Sekunden"
    return $false
}

function Test-PackageInstalled {
    param([string]$package)
    $result = & adb shell pm list packages 2>&1 | Select-String $package
    return $null -ne $result
}

function Test-DeviceOwner {
    $result = & adb shell dpm list-owners 2>&1
    return ($result -join "") -match "com.w3coach.w3coachtv"
}

function Find-APK {
    $scriptDir = Split-Path -Parent $MyInvocation.ScriptName
    $apks = @(Get-ChildItem -Path $scriptDir -Filter "w3coachtv-*.apk" -ErrorAction SilentlyContinue)
    if ($apks.Count -eq 0) {
        $apks = @(Get-ChildItem -Path $scriptDir -Filter "*.apk" -ErrorAction SilentlyContinue)
    }
    if ($apks.Count -eq 0) { return $null }
    if ($apks.Count -eq 1) { return $apks[0].FullName }
    Write-Host ""
    Write-Host "  Mehrere APKs gefunden:" -ForegroundColor Yellow
    for ($i = 0; $i -lt $apks.Count; $i++) {
        Write-Host "  [$i] $($apks[$i].Name)"
    }
    Write-Host "  Auswahl [0-$($apks.Count-1)]: " -NoNewline
    $choice = Read-Host
    return $apks[[int]$choice].FullName
}

Clear-Host
Write-Host ""
Write-Host "  ============================================================" -ForegroundColor Cyan
Write-Host "           W3Coach TV - Deployment Script" -ForegroundColor Cyan
Write-Host "           Google TV Streamer Erstinstallation" -ForegroundColor Cyan
Write-Host "  ============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Dieses Script fuehrt durch die komplette Erstinstallation." -ForegroundColor Gray
Write-Host "  Manuelle Schritte werden klar angezeigt." -ForegroundColor Gray
Write-Host ""

$apkPath = Find-APK
if (-not $apkPath) {
    Write-Err "Keine W3Coach TV APK gefunden!"
    Write-Hint "Bitte w3coachtv-*.apk in dasselbe Verzeichnis legen."
    exit 1
}
Write-OK "APK gefunden: $(Split-Path -Leaf $apkPath)"

$installWireGuard = Confirm-Step "WireGuard VPN konfigurieren? (optional - fuer Fernwartung)"
$wgEndpoint   = ""
$wgServerKey  = ""
$wgClientIp   = ""
$wgPrivateKey = ""

if ($installWireGuard) {
    Write-Host ""
    Write-Host "  WireGuard-Konfiguration:" -ForegroundColor Cyan
    Write-Host "  Server (IP:Port):              " -NoNewline; $wgEndpoint   = Read-Host
    Write-Host "  Server Public Key:             " -NoNewline; $wgServerKey  = Read-Host
    Write-Host "  Client IP (z.B. 10.0.0.3/32): " -NoNewline; $wgClientIp   = Read-Host
    Write-Host "  Client Private Key:            " -NoNewline; $wgPrivateKey = Read-Host
}

Write-Step "1/8" "Google TV Streamer einrichten"
Write-Host ""
Write-Hint "Fuehre den Setup-Wizard am Geraet vollstaendig durch:"
Write-Hint "  1. Sprache waehlen"
Write-Hint "  2. WLAN einrichten"
Write-Hint "  3. Google-Konto einrichten (zwingend fuer ADB-Freischaltung)"
Write-Hint "  4. Alle weiteren Setup-Schritte abschliessen"
Write-Host ""
Write-Hint "WICHTIG: Den Setup-Wizard NICHT abbrechen!"
Pause-ForUser "Setup-Wizard abgeschlossen? ENTER druecken..."

Write-Step "2/8" "Entwickleroptionen aktivieren"
Write-Host ""
Write-Hint "Am Geraet:"
Write-Hint "  Einstellungen -> System -> Info"
Write-Hint "  -> 7x auf 'Android TV OS Build' tippen"
Write-Hint "  -> Meldung 'Du bist jetzt Entwickler' erscheint"
Pause-ForUser "Entwickleroptionen aktiviert? ENTER druecken..."

Write-Step "3/8" "ADB-Debugging aktivieren und verbinden"
Write-Host ""
Write-Hint "Am Geraet:"
Write-Hint "  Einstellungen -> System -> Entwickleroptionen"
Write-Hint "  -> 'ADB-Debugging' aktivieren"
Write-Hint "  -> 'ADB ueber Netzwerk' aktivieren"
Write-Host ""
Write-Host "  IP-Adresse des Streamers eingeben: " -NoNewline
$deviceIp = Read-Host
Pause-ForUser "ADB aktiviert? ENTER druecken..."

Write-Waiting "Verbinde mit ${deviceIp}:5555 ..."
$connectResult = & adb connect "${deviceIp}:5555" 2>&1
if (($connectResult -join "") -match "connected") {
    Write-OK "ADB verbunden: $deviceIp"
} else {
    Write-Err "Verbindung fehlgeschlagen: $connectResult"
    Write-Hint "Stelle sicher dass PC und Streamer im gleichen WLAN sind."
    Pause-ForUser "Manuell verbunden? ENTER druecken..."
}
if (-not (Wait-ForDevice)) { exit 1 }

Write-Step "4/8" "Google-Konto entfernen"
Write-Host ""
$result = & adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith 2>&1
if (($result -join "") -match "disabled") {
    Write-OK "Setup-Wizard deaktiviert"
} else {
    Write-Hint "Hinweis: $result"
}
Write-Host ""
Write-Hint "Google-Konto am Geraet entfernen:"
Write-Hint "  Einstellungen -> Konten -> Google-Konto auswaehlen"
Write-Hint "  -> Konto entfernen -> bestaetigen"
Write-Host ""
Write-Hint "WICHTIG: Ohne diesen Schritt schlaegt 'Device Owner setzen' fehl!"
Pause-ForUser "Google-Konto entfernt? ENTER druecken..."

Write-Step "5/8" "W3Coach TV installieren"
if (Test-PackageInstalled "com.w3coach.w3coachtv") {
    Write-Hint "W3Coach TV ist bereits installiert - wird aktualisiert..."
    $installArgs = @("install", "-r", $apkPath)
} else {
    $installArgs = @("install", $apkPath)
}
Write-Waiting "Installiere APK..."
$installResult = & adb @installArgs 2>&1
if (($installResult -join "") -match "Success") {
    Write-OK "W3Coach TV erfolgreich installiert"
} else {
    Write-Err "Installation fehlgeschlagen:"
    Write-Host "  $installResult" -ForegroundColor Red
    exit 1
}

Write-Step "6/8" "Device Owner setzen"
if (Test-DeviceOwner) {
    Write-OK "W3Coach TV ist bereits Device Owner"
} else {
    Write-Waiting "Setze Device Owner..."
    $dpmResult = & adb shell dpm set-device-owner com.w3coach.w3coachtv/.KioskAdminReceiver 2>&1
    if (($dpmResult -join "") -match "Success") {
        Write-OK "Device Owner erfolgreich gesetzt"
    } else {
        Write-Err "Device Owner konnte nicht gesetzt werden:"
        Write-Host "  $dpmResult" -ForegroundColor Red
        Write-Host ""
        if (($dpmResult -join "") -match "account") {
            Write-Hint "Ursache: Google-Konto noch aktiv"
            Write-Hint "Bitte Schritt 4 wiederholen und Google-Konto entfernen."
        }
        exit 1
    }
}

Write-Step "7/8" "W3Coach TV starten"
Write-Waiting "Starte App..."
& adb shell am start -n com.w3coach.w3coachtv/.MainActivity 2>&1 | Out-Null
Start-Sleep -Seconds 3
$running = & adb shell dumpsys activity 2>&1 | Select-String "com.w3coach.w3coachtv"
if ($running) {
    Write-OK "W3Coach TV laeuft"
} else {
    Write-Hint "App moeglicherweise noch nicht gestartet - bitte am Geraet pruefen"
}

if ($installWireGuard) {
    Write-Step "8/8" "WireGuard VPN konfigurieren"
    Write-Host ""
    Write-Hint "WireGuard-Konfiguration im App-Menue eingeben:"
    Write-Hint "  OK-Taste lange halten -> System (PIN) -> VPN konfigurieren"
    Write-Host ""
    Write-Hint "Folgende Werte eingeben:"
    Write-Hint "  Server:      $wgEndpoint"
    Write-Hint "  Server Key:  $wgServerKey"
    Write-Hint "  Client IP:   $wgClientIp"
    Write-Hint "  Private Key: (wurde beim Setup eingegeben)"
    Write-Host ""
    Write-Hint "Verbindung testen:"
    Write-Hint "  OK-Taste lange halten -> 'Quicksupport verbinden'"
} else {
    Write-Step "8/8" "WireGuard uebersprungen"
    Write-OK "WireGuard kann spaeter ueber System-Menue -> VPN konfigurieren eingerichtet werden"
}

Write-Host ""
Write-Host ""
Write-Host "  ============================================================" -ForegroundColor Green
Write-Host "           Installation abgeschlossen!" -ForegroundColor Green
Write-Host "  ============================================================" -ForegroundColor Green
Write-Host ""
Write-OK "W3Coach TV installiert und als Device Owner gesetzt"
Write-OK "App startet automatisch nach jedem Neustart"
Write-OK "Auto-Update: im Menue konfigurieren"
Write-Host ""
Write-Hint "Naechste Schritte am Geraet:"
Write-Hint "  1. OK lange halten -> System (PIN) -> URL 1/2/3 konfigurieren"
Write-Hint "  2. OK lange halten -> Auto-Update aktivieren"
if ($installWireGuard) {
    Write-Hint "  3. OK lange halten -> System (PIN) -> VPN konfigurieren"
}
Write-Host ""
Write-Host "  Geraet ist einsatzbereit." -ForegroundColor Green
Write-Host ""
