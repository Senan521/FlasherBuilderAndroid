#!/usr/bin/env bash
# Android (arm64) için otaripper ve lpmake'i indirip SHA-256 ile doğrular ve jniLibs'e yerleştirir.
# Gerekenler: curl, unzip, sha256sum.   Kullanım: bash tools/fetch_arm64_tools.sh
set -euo pipefail
cd "$(dirname "$0")/.."

OTARIPPER_URL="https://github.com/syedinsaf/otaripper/releases/download/v3.5.0/otaripper-3.5.0-android-arm64.zip"
OTARIPPER_SHA256="5687136a4cbbf9930176397628381136f0fedfc58e0c405fceb881aee807587f"   # zip içindeki 'otaripper'
LPMAKE_URL="https://raw.githubusercontent.com/ColdWindScholar/MIO-KITCHEN-SOURCE/1c358da192c2ee71a4034f9a3525333cf1e4989b/bin/Android/aarch64/lpmake"
LPMAKE_SHA256="76e499bbd7c5048e1bf04a4f6d89b89f86162d278ba31ca003b00ba269c2782b"

JNI=app/src/main/jniLibs/arm64-v8a
LIC=app/src/main/assets/licenses
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
mkdir -p "$JNI" "$LIC"

check() { echo "$2  $1" | sha256sum -c - >/dev/null || { echo "SHA-256 uyuşmuyor: $1" >&2; exit 1; }; }

curl -fsSL --retry 3 -o "$TMP/otaripper.zip" "$OTARIPPER_URL"
unzip -q -o "$TMP/otaripper.zip" -d "$TMP/ota"
check "$TMP/ota/otaripper" "$OTARIPPER_SHA256"
# Android uygulamaları yalnızca nativeLibraryDir'deki lib*.so dosyalarını çalıştırabilir.
install -m 755 "$TMP/ota/otaripper" "$JNI/libotaripper.so"
cp "$TMP/ota/LICENSE" "$LIC/otaripper-LICENSE.txt"

curl -fsSL --retry 3 -o "$TMP/lpmake" "$LPMAKE_URL"
check "$TMP/lpmake" "$LPMAKE_SHA256"
install -m 755 "$TMP/lpmake" "$JNI/liblpmake.so"

echo "Tamam: $(ls "$JNI" | tr '\n' ' ')"
