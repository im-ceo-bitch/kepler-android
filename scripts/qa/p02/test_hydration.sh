#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
COROUTINES="$(dirname "$(command -v kotlinc)")/../lib/kotlinx-coroutines-core-jvm.jar"
kotlinc -cp "$COROUTINES" \
  "$ROOT/app/src/main/java/com/iumrah/beta/ui/hotels/NuiteePagedCatalog.kt" \
  "$ROOT/scripts/qa/p02/hydration/ComposeStubs.kt" \
  "$ROOT/scripts/qa/p02/hydration/HotelStubs.kt" \
  "$ROOT/scripts/qa/p02/hydration/RuleStubs.kt" \
  "$ROOT/scripts/qa/p02/hydration/ExceptionStubs.kt" \
  "$ROOT/scripts/qa/p02/hydration/ServiceStub.kt" \
  "$ROOT/scripts/qa/p02/hydration/HydrationSmoke.kt" \
  -include-runtime -d "$TMP/rates.jar"
java -cp "$TMP/rates.jar:$COROUTINES" com.iumrah.beta.ui.hotels.HydrationSmokeKt
