#!/usr/bin/env bash
set -euo pipefail

# Builds a double-click SpendLocker installer (.dmg) for macOS via jpackage.
#
# Run this ON A MAC — jpackage cannot cross-compile a native installer for another OS.
# The end user experience: double-click the .dmg, drag SpendLocker into the Applications
# folder shortcut shown in the window, done. No terminal, no manual steps.
#
# Prerequisites (one-time, on the build machine only — NOT needed by end users):
#   - JDK 21:  brew install openjdk@21
#   - Maven:   brew install maven
#
# Produces one .dmg matching the architecture of the JDK used to build it (Apple
# Silicon JDK -> arm64 .dmg, Intel JDK -> x64 .dmg — jpackage cannot cross-build the
# other architecture). To cover both kinds of Mac, run this script once per JDK and
# set JAVA_HOME to each before running (see packaging/build-mac-universal.sh).
#
# Output: packaging/dist/mac/SpendLocker-<version>-<arch>.dmg

cd "$(dirname "$0")/.."

APP_NAME="SpendLocker"
APP_VERSION="${APP_VERSION:-1.0.0}"
VENDOR="SpendLocker"
MAIN_JAR="spendlocker-1.0.0-SNAPSHOT.jar"
MAIN_CLASS="com.spendlocker.Launcher"
ICON="packaging/icon/icon.icns"
OUT_DIR="packaging/dist/mac"

if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME_21="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  if { [ -z "$JAVA_HOME_21" ] || [ ! -x "$JAVA_HOME_21/bin/javac" ]; } \
      && command -v brew >/dev/null 2>&1 && brew --prefix openjdk@21 >/dev/null 2>&1; then
    # java_home can match a JRE-only plugin (no javac) as "21", and Homebrew's openjdk@21
    # isn't linked into /Library/Java/JavaVirtualMachines by default — ask brew directly.
    JAVA_HOME_21="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
  fi
  if [ -z "$JAVA_HOME_21" ] || [ ! -x "$JAVA_HOME_21/bin/javac" ]; then
    echo "JDK 21 not found. Install it with: brew install openjdk@21"
    exit 1
  fi
  export JAVA_HOME="$JAVA_HOME_21"
fi
export PATH="$JAVA_HOME/bin:$PATH"

ARCH="$("$JAVA_HOME/bin/java" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/os.arch/{print $2}')"
case "$ARCH" in
  aarch64) ARCH_LABEL="arm64" ;;
  x86_64)  ARCH_LABEL="x64" ;;
  *)       ARCH_LABEL="$ARCH" ;;
esac

echo "== Building application jars with Maven (JDK: $JAVA_HOME, arch: $ARCH_LABEL) =="
mvn -q clean package

echo "== Packaging with jpackage (type=dmg, arch=$ARCH_LABEL) =="
mkdir -p "$OUT_DIR"
DMG_NAME="$APP_NAME-$APP_VERSION-$ARCH_LABEL"
rm -f "$OUT_DIR/$DMG_NAME.dmg"

jpackage \
  --type dmg \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --vendor "$VENDOR" \
  --input target/dist \
  --main-jar "$MAIN_JAR" \
  --main-class "$MAIN_CLASS" \
  --icon "$ICON" \
  --mac-package-identifier "com.spendlocker.app" \
  --mac-package-name "$APP_NAME" \
  --copyright "Copyright (C) $(date +%Y) SpendLocker" \
  --description "Personal finance & document vault" \
  --dest "$OUT_DIR"

# jpackage always names the output "<name>-<version>.dmg" — rename so arm64/x64 builds
# from running this script twice don't clobber each other.
if [ -f "$OUT_DIR/$APP_NAME-$APP_VERSION.dmg" ]; then
  mv "$OUT_DIR/$APP_NAME-$APP_VERSION.dmg" "$OUT_DIR/$DMG_NAME.dmg"
fi

# --- Optional: code signing + notarization (needs a paid Apple Developer ID) ---
# Once you have a "Developer ID Application/Installer" certificate in Keychain, add
# before jpackage: --mac-sign --mac-signing-key-user-name "Your Name (TEAMID)"
# Then notarize the resulting .dmg:
#   xcrun notarytool submit "$OUT_DIR/$DMG_NAME.dmg" --keychain-profile "AC_PASSWORD" --wait
#   xcrun stapler staple "$OUT_DIR/$DMG_NAME.dmg"
# --------------------------------------------------------------------------------

echo ""
echo "Done: $OUT_DIR/$DMG_NAME.dmg"
echo ""
echo "This build is NOT code-signed. On first run, macOS Gatekeeper will block it."
echo "End users can allow it with EITHER:"
echo "  - Right-click the installed app -> Open -> Open (in the warning dialog), or"
echo "  - System Settings -> Privacy & Security -> scroll down -> 'Open Anyway'"
echo "This is a one-time step per machine."
