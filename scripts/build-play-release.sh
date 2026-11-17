#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION_CODE="${1:-${PLAY_VERSION_CODE:-}}"
VERSION_NAME="${2:-${PLAY_VERSION_NAME:-2.0.3}}"
# A failed run must never leave an earlier candidate looking current.
rm -rf artifacts/play-release
mkdir -p artifacts/play-release
REPORTS="$ROOT/artifacts/play-release"
# Validate before using version input in paths or shell arithmetic.
python3 scripts/release_input.py "$VERSION_CODE" "$VERSION_NAME"

required=(PLAY_KEYSTORE_PATH PLAY_STORE_PASSWORD PLAY_KEY_ALIAS PLAY_KEY_PASSWORD)
for name in "${required[@]}"; do
  [[ -n "${!name:-}" ]] || { echo "STOP: missing $name"; exit 1; }
done
[[ -f "$PLAY_KEYSTORE_PATH" ]] || { echo "STOP: upload keystore not found: $PLAY_KEYSTORE_PATH"; exit 1; }

ACTUAL_CERT="$(keytool -exportcert -alias "$PLAY_KEY_ALIAS" -keystore "$PLAY_KEYSTORE_PATH" -storepass:env PLAY_STORE_PASSWORD 2>/dev/null | sha256sum | awk '{print toupper($1)}')"
: "${PLAY_UPLOAD_CERT_SHA256:?Set the Google Play Console upload certificate SHA-256 (not the app signing certificate)}"
if [[ -n "${PLAY_UPLOAD_CERT_SHA256:-}" ]]; then
  EXPECTED_CERT="$(printf '%s' "$PLAY_UPLOAD_CERT_SHA256" | tr -d '[:space:]:' | tr '[:lower:]' '[:upper:]')"
  [[ "$ACTUAL_CERT" == "$EXPECTED_CERT" ]] || {
    echo "STOP: upload-key SHA-256 does not match PLAY_UPLOAD_CERT_SHA256."
    echo "Expected: $EXPECTED_CERT"
    echo "Actual:   $ACTUAL_CERT"
    exit 1
  }
fi
printf '%s\n' "$ACTUAL_CERT" > "$REPORTS/upload-key-sha256.txt"

if [[ -x ./gradlew ]]; then
  GRADLE="$ROOT/gradlew"
else
  GRADLE="$(command -v gradle || true)"
fi
[[ -n "$GRADLE" && -x "$GRADLE" ]] || { echo 'STOP: Gradle executable not found (Gradle 9.6 is expected by CI).'; exit 1; }

bash scripts/verify-stage25.sh
bash scripts/verify-build-env.sh

# The final decoded bundle manifest is a mandatory release gate.
: "${BUNDLETOOL_JAR:?Set BUNDLETOOL_JAR to the official bundletool executable JAR}"
[[ -s "$BUNDLETOOL_JAR" ]] || { echo 'STOP: bundletool JAR missing'; exit 1; }
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
TOOLS="$SDK_ROOT/build-tools/36.0.0"
for tool in apksigner zipalign aapt; do
  [[ -x "$TOOLS/$tool" ]] || { echo "STOP: missing Android build tool: $tool"; exit 1; }
done
# Firebase/FCM is optional. Gradle and IumrahPushManager already support
# unconfigured builds; do not block a signed release on remote push setup.
# The in-app notification feed is independent of FCM.
fcm_configured=0
for name in IUMRAH_FIREBASE_API_KEY IUMRAH_FIREBASE_APP_ID IUMRAH_FIREBASE_PROJECT_ID IUMRAH_FIREBASE_SENDER_ID; do
  if [[ "${!name:-}" =~ [^[:space:]] ]]; then
    fcm_configured=$((fcm_configured + 1))
  fi
done
if [[ "$fcm_configured" -eq 4 ]]; then
  echo 'INFO: optional Firebase configuration supplied.'
else
  echo 'INFO: Firebase configuration absent or incomplete; continuing release without requiring remote push.'
fi

# User-requested production dependency audit. Resolve the exact release graph before compiling.
"$GRADLE" --no-daemon --console=plain \
  app:dependencies --configuration releaseRuntimeClasspath \
  2>&1 | tee artifacts/play-release/releaseRuntimeClasspath.txt

"$GRADLE" --no-daemon --console=plain \
  app:dependencyInsight --dependency billing --configuration releaseRuntimeClasspath \
  2>&1 | tee artifacts/play-release/dependencyInsight-billing.txt

"$GRADLE" --no-daemon --console=plain \
  app:dependencyInsight --dependency billingclient --configuration releaseRuntimeClasspath \
  2>&1 | tee artifacts/play-release/dependencyInsight-billingclient.txt

if grep -R -nF 'com.android.billingclient' \
  artifacts/play-release/releaseRuntimeClasspath.txt \
  artifacts/play-release/dependencyInsight-billing.txt \
  artifacts/play-release/dependencyInsight-billingclient.txt; then
  echo 'STOP: com.android.billingclient resolved into releaseRuntimeClasspath.'
  exit 1
fi

# Resolve the graph through the Gradle API too, so a formatting change in reports cannot bypass the gate.
"$GRADLE" --no-daemon --console=plain :app:verifyNoGooglePlayBilling \
  2>&1 | tee artifacts/play-release/verify-no-google-play-billing.log

# Exact production build sequence requested for Play release validation.
"$GRADLE" --no-daemon --console=plain clean \
  2>&1 | tee artifacts/play-release/clean.log

# Fail fast on Kotlin source errors before lint/R8/bundling. Gradle reuses these outputs later.
"$GRADLE" --no-daemon --console=plain --stacktrace \
  -PPLAY_VERSION_CODE="$VERSION_CODE" \
  -PPLAY_VERSION_NAME="$VERSION_NAME" \
  :app:compileDebugKotlin \
  :app:compileReleaseKotlin \
  2>&1 | tee artifacts/play-release/compile-kotlin.log

# Keep the release lint gate strict. If lint fails, Gradle normally prints only
# the *first* of several errors. Expand the complete report into Actions logs.
if ! "$GRADLE" --no-daemon --console=plain --stacktrace \
  -PPLAY_VERSION_CODE="$VERSION_CODE" \
  -PPLAY_VERSION_NAME="$VERSION_NAME" \
  :app:testDebugUnitTest \
  :app:lintRelease \
  2>&1 | tee artifacts/play-release/tests-lint.log; then
  echo '===== FULL ANDROID LINT ERROR SUMMARY ====='
  python3 scripts/print-all-release-lint-errors.py || true
  echo '===== END ANDROID LINT ERROR SUMMARY ====='
  echo 'STOP: release lint failed; AAB not produced. All detected errors are listed above.'
  exit 1
fi

"$GRADLE" --no-daemon --console=plain --stacktrace \
  -PPLAY_VERSION_CODE="$VERSION_CODE" \
  -PPLAY_VERSION_NAME="$VERSION_NAME" \
  :app:assembleRelease \
  2>&1 | tee artifacts/play-release/assembleRelease.log

"$GRADLE" --no-daemon --console=plain --stacktrace \
  -PPLAY_VERSION_CODE="$VERSION_CODE" \
  -PPLAY_VERSION_NAME="$VERSION_NAME" \
  :app:bundleRelease \
  2>&1 | tee artifacts/play-release/bundleRelease.log

AAB="$(find app/build/outputs/bundle/release -maxdepth 1 -type f -name '*.aab' -print -quit)"
[[ -n "$AAB" && -s "$AAB" ]] || { echo 'STOP: release AAB was not produced.'; exit 1; }
APK="$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' -print -quit || true)"

unzip -t "$AAB" >/dev/null
java scripts/qa/VerifyAabSigner.java "$AAB" "$ACTUAL_CERT" | tee "$REPORTS/aab-signature.txt"
[[ -n "$APK" && -s "$APK" ]] || { echo 'STOP: release APK missing'; exit 1; }
"$TOOLS/apksigner" verify --verbose --print-certs "$APK" | tee "$REPORTS/apk-signature.txt"
"$TOOLS/zipalign" -c -P 16 -v 4 "$APK" > "$REPORTS/apk-zipalign.txt"
bash scripts/verify-16kb-native-libs.sh "$APK"
"$TOOLS/aapt" dump badging "$APK" > "$REPORTS/apk-badging.txt"
python3 scripts/verify_release_apk.py "$REPORTS/apk-signature.txt" "$REPORTS/apk-badging.txt" "$ACTUAL_CERT" "$VERSION_CODE" "$VERSION_NAME"
bash scripts/verify-16kb-native-libs.sh "$AAB"
python3 scripts/verify_no_play_billing.py --aab "$AAB" | tee artifacts/play-release/aab-no-billing.log

# Inspect the AGP merged release manifest, not only the source manifest.
MERGED_MANIFEST="$(find app/build/intermediates -type f -name AndroidManifest.xml \
  \( -path '*merged_manifest*release*' -o -path '*merged_manifests*release*' -o -path '*processReleaseManifest*' \) \
  -print | head -n 1 || true)"
if [[ -z "$MERGED_MANIFEST" ]]; then
  echo 'STOP: merged release AndroidManifest.xml not found.'
  exit 1
fi
cp "$MERGED_MANIFEST" artifacts/play-release/AndroidManifest.merged-release.xml
if grep -nEi 'com\.android\.vending\.BILLING|com\.android\.billingclient|billingclient' "$MERGED_MANIFEST"; then
  echo 'STOP: Billing marker found in merged release manifest.'
  exit 1
fi

# Validate the actual bundle, not just the source or merged manifest.
java -jar "$BUNDLETOOL_JAR" validate --bundle="$AAB" | tee "$REPORTS/bundletool-validate.txt"
MANIFEST_XML="$REPORTS/AndroidManifest.bundletool-release.xml"
java -jar "$BUNDLETOOL_JAR" dump manifest --bundle="$AAB" > "$MANIFEST_XML"
python3 scripts/verify_play_manifest.py "$MANIFEST_XML" "$VERSION_CODE" "$VERSION_NAME"
java -jar "$BUNDLETOOL_JAR" dump config --bundle="$AAB" > "$REPORTS/bundle-config.txt"
if ! grep -q 'PAGE_ALIGNMENT_16K' "$REPORTS/bundle-config.txt"; then
  echo 'STOP: AAB does not declare 16 KB page alignment.'; exit 1
fi

OUT="artifacts/play-release/iumrah-${VERSION_NAME}-vc${VERSION_CODE}-release.aab"
cp "$AAB" "$OUT"
sha256sum "$OUT" | tee "${OUT}.sha256"
APK_OUT="artifacts/play-release/iumrah-${VERSION_NAME}-vc${VERSION_CODE}-release.apk"
cp "$APK" "$APK_OUT"
sha256sum "$APK_OUT" | tee "${APK_OUT}.sha256"

cat > artifacts/play-release/RELEASE.txt <<TXT
iumrah Android Google Play release
applicationId: com.iumrah.app
namespace: com.iumrah.beta
versionName: $VERSION_NAME
versionCode: $VERSION_CODE
targetSdk: 36
Google Play Billing SDK: absent (source + resolved releaseRuntimeClasspath + merged manifest + AAB scan)
artifact: $(basename "$OUT")
TXT

# Only the last successful step marks these artifacts as eligible for upload.
printf '%s\n' "Verified signed AAB and APK" > "$REPORTS/RC_VERIFIED.txt"
echo "READY: $OUT"
