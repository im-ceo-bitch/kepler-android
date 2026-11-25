#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
S=app/src/main/java/com/iumrah/beta
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
kotlinc "$S/data/booking/PublicTripLinkPolicy.kt" scripts/qa/nov25/PublicTripLinkTest.kt -include-runtime -d "$TMP/public.jar"
java -jar "$TMP/public.jar"
bash scripts/qa/p00/test_chrome.sh
python3 scripts/verify-public-trip-merge.py
