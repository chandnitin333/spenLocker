# Builds a double-click SpendLocker installer (.exe) for Windows via jpackage.
#
# Run this ON WINDOWS — jpackage cannot cross-compile a native installer for another OS.
# The end user experience: double-click the .exe, click Next a few times (a standard
# Windows installer wizard), done. No terminal, no manual steps.
#
# Prerequisites (one-time, on the build machine only — NOT needed by end users):
#   1. JDK 21            https://adoptium.net/temurin/releases/?version=21  (MSI installer)
#   2. Maven             https://maven.apache.org/download.cgi  (add \bin to PATH)
#   3. WiX Toolset v3.14 https://wixtoolset.org/releases/  (jpackage needs this for --type exe/msi)
#
# Output: packaging\dist\windows\SpendLocker-<version>.exe
#
# Usage (PowerShell):  .\packaging\build-windows.ps1

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

$AppName    = "SpendLocker"
$AppVersion = if ($env:APP_VERSION) { $env:APP_VERSION } else { "1.0.0" }
$Vendor     = "SpendLocker"
$MainJar    = "spendlocker-1.0.0-SNAPSHOT.jar"
$MainClass  = "com.spendlocker.Launcher"
$Icon       = "packaging\icon\icon.ico"
$OutDir     = "packaging\dist\windows"

Write-Host "== Building application jars with Maven =="
mvn -q clean package
if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }

Write-Host "== Packaging with jpackage (type=exe) =="
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
Get-ChildItem "$OutDir\*.exe" -ErrorAction SilentlyContinue | Remove-Item -Force

jpackage `
  --type exe `
  --name $AppName `
  --app-version $AppVersion `
  --vendor $Vendor `
  --input target\dist `
  --main-jar $MainJar `
  --main-class $MainClass `
  --icon $Icon `
  --win-menu `
  --win-menu-group $AppName `
  --win-shortcut `
  --win-dir-chooser `
  --copyright "Copyright (C) $(Get-Date -Format yyyy) SpendLocker" `
  --description "Personal finance & document vault" `
  --dest $OutDir

if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

# --- Optional: code signing (needs a paid Windows code-signing certificate) ---
# Once you have a .pfx certificate, sign the installer after jpackage runs:
#   signtool sign /f "cert.pfx" /p "<password>" /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 "$OutDir\$AppName-$AppVersion.exe"
# --------------------------------------------------------------------------------

Write-Host ""
Write-Host "Done: $OutDir\$AppName-$AppVersion.exe"
Write-Host ""
Write-Host "This build is NOT code-signed. Windows SmartScreen will warn on first run."
Write-Host "End users can proceed with: 'More info' -> 'Run anyway' (one-time per machine)."
