#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
kotlinc \
  "$ROOT/app/src/main/java/com/iumrah/beta/domain/journey/FamilyHotelJourneyFactory.kt" \
  "$ROOT/app/src/main/java/com/iumrah/beta/domain/journey/JourneyGuestAgePolicy.kt" \
  "$ROOT/scripts/qa/p02/FamilyStubs.kt" \
  "$ROOT/scripts/qa/p02/FamilyStoreStub.kt" \
  "$ROOT/scripts/qa/p02/FamilyPolicySmoke.kt" \
  -include-runtime -d "$OUT/family.jar"
java -jar "$OUT/family.jar"
