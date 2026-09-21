# SpendLocker — building installers

Produces a real, double-click installer for end users — not a jar they have to run from
a terminal. Each platform's installer must be built **on that platform** (jpackage cannot
cross-compile — you can't build the Windows .exe from this Mac).

## macOS — `build-mac.sh`

```
brew install openjdk@21 maven   # one-time, build machine only
./packaging/build-mac.sh
```

Produces `packaging/dist/mac/SpendLocker-1.0.0.pkg`. End users double-click it and click
through a normal macOS install wizard (Introduction → License → Install → Done).

## Windows — `build-windows.ps1`

Run on a Windows machine with:
1. [Temurin JDK 21](https://adoptium.net/temurin/releases/?version=21) (MSI installer)
2. [Maven](https://maven.apache.org/download.cgi) (add `\bin` to PATH)
3. [WiX Toolset v3.14](https://wixtoolset.org/releases/) — jpackage requires this to build `.exe`/`.msi`

```powershell
.\packaging\build-windows.ps1
```

Produces `packaging\dist\windows\SpendLocker-1.0.0.exe`. End users double-click it and
click through a normal Windows install wizard (Next → Next → Install → Finish).

## Unsigned builds — what end users will see

Neither build is code-signed (no Apple Developer ID / Windows code-signing certificate
was set up). This is a one-time speed bump per machine, not a broken installer:

- **macOS (Gatekeeper):** first launch says "SpendLocker can't be opened because it is
  from an unidentified developer." Right-click the app → **Open** → **Open** in the
  dialog. After that first time, it opens normally.
- **Windows (SmartScreen):** the installer shows "Windows protected your PC." Click
  **More info** → **Run anyway**.

Mention this to whoever you send the installer to, or add it to your download page.

## Adding code signing later

Both scripts have the signing commands written in as comments (`--mac-sign` /
`notarytool` for macOS, `signtool` for Windows) — uncomment and fill in your credentials
once you have:
- **macOS:** an Apple Developer Program membership ($99/yr) → "Developer ID Installer"
  certificate, plus notarization credentials.
- **Windows:** a code-signing certificate (`.pfx`) from a CA (or a cheaper "OV" cert —
  note Microsoft's SmartScreen reputation system still flags brand-new certs for a
  while regardless of signing; only EV certs get instant trust).

## Bumping the version

Both scripts default to `1.0.0`. Override per-run: `APP_VERSION=1.1.0 ./packaging/build-mac.sh`.

## App icon

`packaging/icon/icon.png` is the 1024×1024 source; `icon.icns` (macOS) and `icon.ico`
(Windows) are generated from it. To change the icon, replace `icon.png` (or edit
`generate_icon.py`) and re-run `python3 packaging/icon/generate_icon.py` — the `.icns`
step only works when run on a Mac (uses `iconutil`).
