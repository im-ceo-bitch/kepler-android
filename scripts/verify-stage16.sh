#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Prevent the Stage 10 compatibility bridge from re-entering Stage 16 while
# Stage 16 walks the normal Stage 15 -> ... -> Stage 10 chain.
export IUMRAH_VERIFY_LATEST=1
bash scripts/verify-stage15.sh

GRADLE=app/build.gradle.kts
MANIFEST=app/src/main/AndroidManifest.xml
NET=app/src/main/res/xml/network_security_config.xml

# Production identity / SDK / release build hardening.
grep -q 'applicationId = "com.iumrah.app"' "$GRADLE" || { echo 'STOP: production applicationId must remain com.iumrah.app'; exit 1; }
grep -Eq 'minSdk[[:space:]]*=[[:space:]]*26' "$GRADLE" || { echo 'STOP: minSdk drifted from 26'; exit 1; }
grep -Eq 'compileSdk[[:space:]]*=[[:space:]]*36' "$GRADLE" || { echo 'STOP: compileSdk must be 36'; exit 1; }
grep -Eq 'targetSdk[[:space:]]*=[[:space:]]*36' "$GRADLE" || { echo 'STOP: targetSdk must be 36'; exit 1; }
grep -q 'isMinifyEnabled = true' "$GRADLE" || { echo 'STOP: release minification must be enabled'; exit 1; }
grep -q 'isShrinkResources = true' "$GRADLE" || { echo 'STOP: release resource shrinking must be enabled'; exit 1; }
grep -q 'signingConfig = signingConfigs.getByName("playRelease")' "$GRADLE" || { echo 'STOP: release signing config missing'; exit 1; }
grep -q 'useLegacyPackaging = false' "$GRADLE" || { echo 'STOP: modern native library packaging must be enabled'; exit 1; }
grep -q 'org.maplibre.gl:android-sdk-opengl:13.6.1' "$GRADLE" || { echo 'STOP: expected current MapLibre native SDK 13.6.1'; exit 1; }

# Pricing fallback and golden tests must not regress to the obsolete 20% Android beta exception.
grep -q 'packageMarkupRate: BigDecimal = BigDecimal("0.50")' app/src/main/java/com/iumrah/beta/domain/pricing/LocalPackagePricingEngine.kt || { echo 'STOP: fallback markup must stay at 50%'; exit 1; }
grep -q 'assertMoney("0.50", quote.pricingSnapshot!!.totals.markupRate)' app/src/test/java/com/iumrah/beta/domain/pricing/LocalPackagePricingEngineTest.kt || { echo 'STOP: pricing golden test is not synchronized to 50%'; exit 1; }
if grep -R -nE 'android-test-20pct|matches20Percent|assertMoney\("0\.20"' README.md parity app/src/test 2>/dev/null; then
  echo 'STOP: obsolete Android 20% pricing exception is still present in active docs/tests.'; exit 1
fi

# Google Play Billing was intentionally removed. A v35 guard may mention the
# forbidden group while checking it, so reject dependency coordinates/manifest
# declarations rather than the guard implementation itself.
if grep -R -nEi 'com\.android\.vending\.BILLING|com\.android\.billingclient:(billing|billing-ktx)|billing-ktx' \
  "$GRADLE" "$MANIFEST" app/src/main/res 2>/dev/null; then
  echo 'STOP: Google Play Billing dependency/manifest declaration found although purchases were removed.'
  exit 1
fi

# Release network / backup policy.
grep -q 'android:allowBackup="false"' "$MANIFEST" || { echo 'STOP: backup must stay disabled'; exit 1; }
grep -q 'android:usesCleartextTraffic="false"' "$MANIFEST" || { echo 'STOP: cleartext network traffic must be disabled'; exit 1; }
grep -q 'android:networkSecurityConfig="@xml/network_security_config"' "$MANIFEST" || { echo 'STOP: network security config missing'; exit 1; }
test -s "$NET" || { echo 'STOP: network_security_config.xml missing'; exit 1; }
grep -q 'cleartextTrafficPermitted="false"' "$NET" || { echo 'STOP: network policy must reject cleartext'; exit 1; }

# Fail on accidental debug/test flags or common high-risk permissions.
if grep -qE 'android:(debuggable|testOnly)="true"' "$MANIFEST"; then
  echo 'STOP: debug/testOnly flag found in production manifest.'; exit 1
fi
if grep -qE 'QUERY_ALL_PACKAGES|READ_SMS|RECEIVE_SMS|READ_CALL_LOG|WRITE_CALL_LOG|MANAGE_EXTERNAL_STORAGE|REQUEST_INSTALL_PACKAGES' "$MANIFEST"; then
  echo 'STOP: unexpected high-risk Android permission found.'; exit 1
fi

# Make sure repository endpoints cannot silently drift to emulator/local hosts.
if grep -R -nEi 'localhost|127\.0\.0\.1|10\.0\.2\.2' app/src/main/java app/src/main/res --exclude='*.mp4'; then
  echo 'STOP: local development endpoint found in production sources.'; exit 1
fi
grep -q 'API_BASE_URL = "https://iumrah-backend.helloiumrah.workers.dev"' app/src/main/java/com/iumrah/beta/core/config/AppConfig.kt || {
  echo 'STOP: production backend must be https://iumrah.app'; exit 1
}

# Final release tooling must be present and non-empty.
# Do not require Unix execute bits: mobile ZIP patchers/GitHub uploads may normalize
# file modes, while every release invocation intentionally uses `bash script.sh`.
test -s scripts/build-play-release.sh || { echo 'STOP: production AAB builder missing'; exit 1; }
test -s scripts/verify-16kb-native-libs.sh || { echo 'STOP: 16 KB native verifier missing'; exit 1; }
test -s scripts/verify_play_manifest.py || { echo 'STOP: Play manifest verifier missing'; exit 1; }
test -s scripts/install-play-release-workflow.sh || { echo 'STOP: Play workflow installer missing'; exit 1; }
test -s docs/release/iumrah-play-release.yml || { echo 'STOP: staged Play release workflow missing'; exit 1; }

echo 'Stage 016 Google Play release-readiness checks passed.'
