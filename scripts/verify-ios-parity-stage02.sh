#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/verify-ios-parity-stage01.sh
python3 - <<'PY'
from pathlib import Path
p = Path('app/src/main/java/com/iumrah/beta/ui/home/HomeScreen.kt').read_text()
f = Path('app/src/main/java/com/iumrah/beta/ui/home/HomeReadyPackageTime.kt').read_text()
t = Path('app/src/test/java/com/iumrah/beta/ui/home/HomeReadyPackageTimeTest.kt').read_text()
# iOS Home Apple Store sections parity and card stability invariants.
assert 'private val homeReadyCardWidth = 318.dp' in p
assert 'private val homeReadyCardHeight = 372.dp' in p
assert p.count('Modifier.width(homeReadyCardWidth).height(homeReadyCardHeight)') == 2
assert 'items(readyEntries, key = { it.first.id })' in p, 'Each option needs its unique key'
assert 'items(readyEntries, key = { it.second.id })' not in p
assert 'ReadyHomeFlightTime(option.outbound' in p
assert 'ReadyHomeFlightTime(inbound' in p
assert 'ReadyHomeAirlineLogo(option.outbound.airlineCode)' in p
assert 'ReadyHomeAirlineLogo(inboundCode)' in p
assert 'homeFlightTime(leg.departureAt)' in p
assert 'snapshot.totalDays?.takeIf { it > 0 }' in p
assert 'java.time.OffsetDateTime' in f and 'ZoneId.systemDefault()' in f
assert 'assertEquals("—", homeFlightTime("tomorrow"))' in t
assert 'app/build.gradle.kts' in Path('.github/workflows/android-ci.yml').read_text()
print('Stage02 Home Flight First UI and itinerary-time invariants passed.')
PY
