#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export IUMRAH_VERIFY_LATEST=1
bash scripts/verify-stage24.sh
python3 - <<'PY'
from pathlib import Path
p=Path('scripts/verify-16kb-native-libs.sh').read_text()
assert "required_16kb_abis = {'arm64-v8a', 'x86_64'}" in p
assert "abi in {'armeabi-v7a', 'x86'}" in p
assert '64-bit native library is not 16 KB ELF-aligned' in p
assert '32-bit ARM native code exists without arm64-v8a counterpart' in p
assert '32-bit x86 native code exists without x86_64 counterpart' in p
b=Path('scripts/build-play-release.sh').read_text()
assert 'bash scripts/verify-stage25.sh' in b
print('Stage 025: Google Play 16 KB verifier correctly scopes ELF alignment to 64-bit ABIs.')
PY

# Incremental iOS source-driven UI parity guard (Android-only checkout).
python3 scripts/verify-next-stage-parity.py

# Hotels storefront parity: geometry, iOS semantics, independent airport actions.
python3 scripts/verify-hotels-storefront-parity.py

# Latest signature header/navigation follows the supplied 2026-10-26 iOS design patch.
python3 scripts/verify-signature-brand-parity.py

# Header block 1 contract: iOS source geometry + Metal/AGSL fidelity guards.
python3 scripts/verify-brand-block1-final.py

# Shared iOS-origin geometry, semantics and design-system regression guard.
python3 scripts/verify-block2-design-parity.py

# Home source-driven parity: geometry, navigation, read-state and snapping.
python3 scripts/verify-block3-home-parity.py

# Hotel/Flight source parity after inline Friends and flight card geometry corrections.
python3 scripts/verify-block4-hotels-flights-parity.py

# Configurator stage 5: flight selection state + MapKit/MapLibre visual parity.
python3 scripts/verify-block5-configurator-parity.py

# Booking stage 6: identity copy, iOS payment states, itinerary and refresh parity.
python3 scripts/verify-block6-booking-parity.py

# Block 7 Care/Account entry actions, identity flows, original brand assets.
python3 scripts/verify-block7-care-account-parity.py

# Block 8 additional screens and functional safety regression.
python3 scripts/verify-block8-integrations-parity.py

# Supplemental Block 8 guard; user explicitly excluded ritual Umrah Flow.
python3 scripts/verify-block8-reminder-center-parity.py

# Global v14 internationalization and design-regression checks.
python3 scripts/verify-global-parity-v14.py
