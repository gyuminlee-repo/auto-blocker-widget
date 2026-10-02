#!/usr/bin/env bash
# Gradle 없이 aapt2, javac, d8, zipalign, apksigner 로 APK 를 만든다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}}"
BT_VER="${BT_VER:-35.0.0}"
PLAT="${PLAT:-android-34}"
BT="$SDK/build-tools/$BT_VER"
AJAR="$SDK/platforms/$PLAT/android.jar"
if [ -z "${JAVA_HOME:-}" ]; then JAVA_HOME="$(/usr/libexec/java_home -v 17)"; fi
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"

B="$ROOT/build"
KS_DIR="$ROOT/.keystore"
KS="$KS_DIR/debug.jks"
OUT="$B/shieldtap.apk"

for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$AJAR"; do
  [ -e "$f" ] || { echo "missing: $f" >&2; exit 1; }
done

# 이전 산출물 정리: 고정 디렉터리 내 파일만 삭제
mkdir -p "$B/compiled" "$B/gen" "$B/classes" "$B/dex"
find "$B" -type f -delete

# 서명: RELEASE_KS 가 있으면 릴리스 키, 없으면 디버그 키(저장소 비포함)
RELEASE_KS="${RELEASE_KS:-}"
if [ -n "$RELEASE_KS" ]; then
  [ -f "$RELEASE_KS" ] || { echo "RELEASE_KS not found: $RELEASE_KS" >&2; exit 1; }
  [ -n "${RELEASE_KS_ALIAS:-}" ] || { echo "RELEASE_KS_ALIAS is empty" >&2; exit 1; }
  [ -n "${RELEASE_KS_PASS:-}" ] || { echo "RELEASE_KS_PASS is empty" >&2; exit 1; }
  export RELEASE_KS_PASS
  SIGN_ARGS=(--ks "$RELEASE_KS" --ks-key-alias "$RELEASE_KS_ALIAS" \
    --ks-pass env:RELEASE_KS_PASS --key-pass env:RELEASE_KS_PASS)
else
  echo "warning: debug-signed (not for distribution); set RELEASE_KS to sign for release" >&2
  if [ ! -f "$KS" ]; then
    mkdir -p "$KS_DIR"
    keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
      -alias debug -keyalg RSA -keysize 2048 -validity 10000 \
      -dname "CN=Android Debug,O=Android,C=US" >/dev/null
  fi
  SIGN_ARGS=(--ks "$KS" --ks-pass pass:android --key-pass pass:android)
fi
VERSION_CODE="${VERSION_CODE:-7}"
VERSION_NAME="${VERSION_NAME:-0.7.0}"

"$BT/aapt2" compile --dir "$ROOT/res" -o "$B/compiled"
"$BT/aapt2" link -I "$AJAR" --manifest "$ROOT/AndroidManifest.xml" \
  --min-sdk-version 26 --target-sdk-version 34 --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$B/gen" -o "$B/base.apk" "$B"/compiled/*.flat

javac --release 8 -Xlint:-options -cp "$AJAR" -d "$B/classes" \
  $(find "$ROOT/src" "$B/gen" -name '*.java')

"$BT/d8" --lib "$AJAR" --min-api 26 --output "$B/dex" \
  $(find "$B/classes" -name '*.class')

cp "$B/base.apk" "$B/unaligned.apk"
( cd "$B/dex" && zip -q -j "$B/unaligned.apk" classes.dex )
"$BT/zipalign" -f 4 "$B/unaligned.apk" "$B/aligned.apk"
"$BT/apksigner" sign "${SIGN_ARGS[@]}" --out "$OUT" "$B/aligned.apk"
"$BT/apksigner" verify "$OUT"
echo "APK: $OUT ($VERSION_NAME, code $VERSION_CODE)"
"$BT/apksigner" verify --print-certs "$OUT" | grep -E 'certificate (DN|SHA-256 digest)'
echo "APK SHA-256: $(shasum -a 256 "$OUT" | cut -d' ' -f1)"
