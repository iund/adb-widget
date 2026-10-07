#!/usr/bin/env bash
# Builds adbwifi.apk with the plain Android SDK tools (no Gradle).
# Needs: ANDROID_HOME (or ANDROID_SDK_ROOT), a JDK, and optionally
#   KEYSTORE / KS_PASS / KEY_ALIAS (defaults to a throwaway debug keystore).
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
JAR="$SDK/platforms/$(ls "$SDK/platforms" | grep -E '^android-[0-9]+$' | sort -t- -k2 -n | tail -1)/android.jar"
echo "build-tools: $BT"; echo "android.jar: $JAR"

KEYSTORE="${KEYSTORE:-$HOME/.android/debug.keystore}"
KS_PASS="${KS_PASS:-android}"
KEY_ALIAS="${KEY_ALIAS:-androiddebugkey}"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

rm -rf build && mkdir -p build/gen build/cls
# minSdk 11 (Android 3.x) .. targetSdk 28 (modern Android refuses to install < 24)
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$JAR" --manifest AndroidManifest.xml --java build/gen build/res.zip \
  --min-sdk-version 11 --target-sdk-version 28
javac --release 8 -Xlint:-options -cp "$JAR" -d build/cls build/gen/x/adbwifi/R.java src/x/*.java
"$BT/d8" --min-api 11 --lib "$JAR" --output build $(find build/cls -name '*.class')
cp build/base.apk build/app.apk
(cd build && zip -q app.apk classes.dex)
"$BT/zipalign" -f 4 build/app.apk build/aligned.apk
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KEY_ALIAS" \
  --min-sdk-version 11 --out build/adbwifi.apk build/aligned.apk
echo "Built: $(pwd)/build/adbwifi.apk"
