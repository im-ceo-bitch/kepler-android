#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
kotlinc "$ROOT/app/src/main/java/com/iumrah/beta/domain/nuitee/NuiteeRules.kt" \
  "$ROOT/scripts/qa/p02/rules/HotelModelsStub.kt" \
  "$ROOT/scripts/qa/p02/rules/HotelRulesSmoke.kt" -include-runtime -d "$OUT/rules.jar"
java -jar "$OUT/rules.jar"
