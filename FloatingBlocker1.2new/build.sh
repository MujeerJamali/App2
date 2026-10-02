#!/usr/bin/env bash
# Builds a signed, installable APK from this AIDE/Ant-style source tree
# without Gradle or AIDE itself - just the raw Android SDK command-line
# tools. Written for a fresh environment with nothing but a JDK and
# internet access to dl.google.com.
#
# IMPORTANT - signing key: this generates a NEW, random debug keystore
# on first run (keystore/debug.keystore) if one doesn't already exist,
# and reuses it on every later run. The resulting APK will NOT be
# signed the same way as a build produced by AIDE on the phone, so
# Android will refuse to install it as an "update" over an
# AIDE-installed copy - it has to go on as a fresh install instead
# (see README.txt for what that means for a Device Owner app already
# in place, i.e. removing Device Owner / uninstalling first).
#
# Usage: ./build.sh
# Output: build/apk/app-signed.apk

set -euo pipefail
cd "$(dirname "$0")"

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/.android-sdk-floatingblocker}"
BUILD_TOOLS_VERSION="35.0.0"
PLATFORM_VERSION="android-29"
CMDLINE_TOOLS_ZIP_URL_XML="https://dl.google.com/android/repository/repository2-3.xml"

echo "== Using SDK root: $SDK_ROOT"

if [ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "== Downloading Android command-line tools..."
    mkdir -p "$SDK_ROOT"
    LATEST_ZIP=$(curl -sS "$CMDLINE_TOOLS_ZIP_URL_XML" | grep -o 'commandlinetools-linux-[0-9]*_latest\.zip' | sort -u | tail -1)
    curl -sS -o /tmp/cmdline-tools.zip "https://dl.google.com/android/repository/$LATEST_ZIP"
    rm -rf /tmp/cmdline-tools-extract
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extract
    mkdir -p "$SDK_ROOT/cmdline-tools"
    rm -rf "$SDK_ROOT/cmdline-tools/latest"
    mv /tmp/cmdline-tools-extract/cmdline-tools "$SDK_ROOT/cmdline-tools/latest"
fi

if [ ! -d "$SDK_ROOT/platforms/$PLATFORM_VERSION" ] || [ ! -d "$SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION" ]; then
    echo "== Installing platform + build-tools (this downloads a few hundred MB)..."
    yes | "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_ROOT" \
        "platforms;$PLATFORM_VERSION" "build-tools;$BUILD_TOOLS_VERSION" >/tmp/sdkmanager.log 2>&1 || {
        echo "sdkmanager failed - see /tmp/sdkmanager.log"; exit 1;
    }
fi

BT="$SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION"
PLATFORM="$SDK_ROOT/platforms/$PLATFORM_VERSION"

echo "== Cleaning build/ ..."
rm -rf build
mkdir -p build/gen build/obj build/dex build/apk

echo "== Compiling resources (aapt) ..."
"$BT/aapt" package -f -m -J build/gen -M AndroidManifest.xml -S res -I "$PLATFORM/android.jar"

echo "== Compiling Java sources (javac) ..."
# -source/-target 8 is required here (not just a style choice) - it's
# the only cross-compilation level modern javac still allows combining
# with -bootclasspath against android.jar instead of the JDK's own
# module system. Don't "clean this up" by removing it.
javac -encoding UTF-8 -source 8 -target 8 -nowarn \
    -bootclasspath "$PLATFORM/android.jar" \
    -classpath "$(find libs -name '*.jar' | tr '\n' ':')" \
    -d build/obj \
    $(find src -name "*.java") $(find build/gen -name "*.java")

echo "== Dexing (d8) ..."
# Needs build-tools 35+ specifically - build-tools 33.0.2's d8 has an
# internal NPE bug on at least one of this project's anonymous inner
# classes (LockScheduleActivity's), unrelated to anything in our code.
"$BT/d8" --release --min-api 24 --output build/dex \
    --lib "$PLATFORM/android.jar" \
    $(find build/obj -name "*.class") $(find libs -name '*.jar')

echo "== Packaging APK (aapt) ..."
"$BT/aapt" package -f -M AndroidManifest.xml -S res -I "$PLATFORM/android.jar" -F build/apk/app-unsigned.apk
(cd build/dex && "$BT/aapt" add ../apk/app-unsigned.apk classes.dex)

echo "== Aligning (zipalign) ..."
"$BT/zipalign" -f -p 4 build/apk/app-unsigned.apk build/apk/app-aligned.apk

echo "== Signing (apksigner) ..."
mkdir -p keystore
if [ ! -f keystore/debug.keystore ]; then
    keytool -genkeypair -v -keystore keystore/debug.keystore \
        -storepass android123 -keypass android123 -alias floatingblocker \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Floating Blocker,O=Self-Control,C=US"
fi
"$BT/apksigner" sign --ks keystore/debug.keystore \
    --ks-pass pass:android123 --key-pass pass:android123 --ks-key-alias floatingblocker \
    --out build/apk/app-signed.apk build/apk/app-aligned.apk

echo "== Verifying ..."
"$BT/apksigner" verify build/apk/app-signed.apk

echo ""
echo "Done: build/apk/app-signed.apk"
"$BT/aapt" dump badging build/apk/app-signed.apk | head -1
