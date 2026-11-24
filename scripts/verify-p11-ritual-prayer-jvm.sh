#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
SRC=app/src/main/java/com/iumrah/beta
kotlinc scripts/qa/P11RitualPrayerRegression.kt "$SRC/domain/prayer/QiblaBearing.kt" "$SRC/domain/umrahflow/AdvisorCheckpoint.kt" -include-runtime -d "$TMP/p11.jar"
java -jar "$TMP/p11.jar"
