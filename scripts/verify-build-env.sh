#!/usr/bin/env bash
set -euo pipefail

test -s app/build.gradle.kts

grep -Eq 'compileSdk[[:space:]]*=[[:space:]]*36' app/build.gradle.kts || {
  echo 'STOP: compileSdk must be 36 (stable Android SDK platform).'
  exit 1
}

grep -Eq 'targetSdk[[:space:]]*=[[:space:]]*36' app/build.gradle.kts || {
  echo 'STOP: targetSdk must be 36 for the current stable Android target.'
  exit 1
}

# Do not pin a specific versionCode here. Release/update stages are allowed to
# increment it. We only require a valid positive integer.
version_code="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' app/build.gradle.kts | head -n1)"
if [[ -z "$version_code" || "$version_code" -lt 1 ]]; then
  echo 'STOP: app versionCode must be a positive integer.'
  exit 1
fi

echo "Android build environment checks passed: compileSdk=36 targetSdk=36 versionCode=$version_code."

# Preflight regression: the October iOS parity flight panel previously contained
# an extra top-level brace which made :app:compileDebugKotlin fail at line 608.
# Catch the exact source-shape failure before Gradle spends time compiling.
python3 - <<'PYFLIGHT'
from pathlib import Path
import re
source = Path('app/src/main/java/com/iumrah/beta/ui/flights/FlightDiscoveryPanel.kt').read_text()
helper = 'private fun airportCity('
if helper not in source:
    raise SystemExit('STOP: FlightDiscoveryPanel helper section is missing.')
head = source[:source.index(helper)]
if not re.search(r'(?m)^\s*}\s*\Z', head):
    raise SystemExit('STOP: FlightDiscoveryPanel helper boundary is malformed.')
if re.search(r'(?m)^\s*}\s*\n\s*}\s*\Z', head):
    raise SystemExit('STOP: redundant top-level brace in FlightDiscoveryPanel (previous line 608 regression).')
print('FlightDiscoveryPanel top-level brace regression check passed.')
PYFLIGHT
