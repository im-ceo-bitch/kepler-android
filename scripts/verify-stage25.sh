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
