#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Structural and Kotlin-runtime checks from all previous parity stages.
bash scripts/verify-ios-parity-stage08.sh
# Flight panel compilation failed in a verified GitHub debug build due to an
# extra '}' directly before airportCity(). Detect this regression explicitly.
python3 - <<'PYFLIGHT'
from pathlib import Path
import re
flight=Path('app/src/main/java/com/iumrah/beta/ui/flights/FlightDiscoveryPanel.kt').read_text()
needle='private fun airportCity('
assert needle in flight
prefix=flight.split(needle, 1)[0]
assert re.search(r'(?m)^\s*}\s*\Z', prefix)
assert not re.search(r'(?m)^\s*}\s*\n\s*}\s*\Z', prefix), 'stray top-level curly brace near flight helpers'
assert flight.count('fun FlightDiscoveryPanel(')==1
print('Stage 09 flight compile regression assertions: PASS')
PYFLIGHT
