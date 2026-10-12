#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/verify-ios-parity-stage06.sh
python3 - <<'PYIN'
from pathlib import Path
r=Path('app/src/main/java/com/iumrah/beta')
s=(r/'data/advisor/UmrahFlowService.kt').read_text()
u=(r/'ui/advisor/UmrahAdvisorScreen.kt').read_text()
t=Path('app/src/test/java/com/iumrah/beta/data/advisor/UmrahFlowServiceParityTest.kt').read_text()
assert 'getSharedPreferences("iumrah.umrahFlow"' in s
assert 'fun cached(language: String)' in s
assert 'async(Dispatchers.IO)' in s
assert 'CancellationException' in s
assert 'validAudioUrl' in s and 'https' in s
assert 'service.cached(selectedLanguage.code)' in u
assert 'audioActive = playingKey ==' in u
assert 'navigatorSelection = target' in u
assert 'stage = navigatorSelection' in u
assert 'showNavigator = true' in u
assert 'runBlocking' in t and t.count('@Test') >= 3
print('Stage07 Advisor iOS cache, audio, navigation and regression-test source checks passed.')
PYIN
