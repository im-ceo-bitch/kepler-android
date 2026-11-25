#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
bash scripts/verify-stage10.sh
FILE="app/src/main/java/com/iumrah/beta/ui/booking/JourneyTravelInfoPanel.kt"
HOME="app/src/main/java/com/iumrah/beta/ui/booking/BookingsHomeScreen.kt"
test -f "$FILE"
grep -q 'fun JourneyTravelInfoPanel' "$FILE"
grep -q 'api.aladhan.com/v1/timings' "$FILE"
grep -q 'method=4&school=0' "$FILE"
grep -q 'api.met.no/weatherapi/locationforecast/2.0/compact' "$FILE"
grep -q 'Weather data · MET Norway' "$FILE"
grep -q 'AnalogTravelClock' "$FILE"
grep -q 'HorizontalPager' "$FILE"
grep -q 'JourneyTravelInfoPanel(language, initialPage = 1)' app/src/main/java/com/iumrah/beta/ui/home/HomeWeatherScreen.kt
grep -q 'showsMakkahTime = false' "$HOME"
echo 'Stage 011 Booking Travel Info parity checks passed.'
