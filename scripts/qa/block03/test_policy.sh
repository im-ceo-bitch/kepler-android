#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
S=app/src/main/java/com/iumrah/beta
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
kotlinc "$S/data/notification/NotificationScopeGate.kt" "$S/domain/booking/BookingIdentityPolicy.kt" "$S/data/booking/PublicTripLinkPolicy.kt" scripts/qa/block03/PolicySmoke.kt -include-runtime -d "$TMP/policy.jar"
java -jar "$TMP/policy.jar"
