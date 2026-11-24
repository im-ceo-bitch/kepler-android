#!/usr/bin/env python3
"""Source checks for Android stage 09 Umrah Flow parity (not an APK compiler)."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
java = root / 'app/src/main/java/com/iumrah/beta'
checks = [
    ('core/navigation/AppChromeStore.kt', ['data object UmrahFlow', 'fun openUmrahFlow()']),
    ('ui/shell/AppShell.kt', ['AppRoute.UmrahFlow -> UmrahFlowScreen(language, chrome)']),
    ('ui/home/HomeScreen.kt', ['chrome.openUmrahFlow()']),
    ('ui/home/HomeScreen.kt', ['IumrahPressable(onClick = { chrome.openUmrahFlow() }']),
    ('domain/umrahflow/UmrahFlowProgress.kt', ['enum class UmrahPhase', 'fun completeTawafTurn()', 'fun completeSaiLeg()', 'tawafCompleted == 7', 'saiCompleted == 7']),
    ('domain/umrahflow/UmrahFlowAudioUrlPolicy.kt', ['AppConfig.API_BASE_URL', '/api/services/advisor/audio/']),
    ('domain/umrahflow/UmrahFlowCheckpointStore.kt', ['fun restore()', 'fun save(', 'fun clear()']),
    ('data/advisor/UmrahFlowService.kt', ['offlineCatalog', 'UmrahFlowAudioUrlPolicy::isAllowed', 'snapshot.translations.ifEmpty', 'cached("en").translations']),
    ('ui/umrahflow/UmrahFlowScreen.kt', ['UmrahAdvisorScreen(appLanguage = language, chrome = chrome)']),
    ('ui/advisor/UmrahAdvisorScreen.kt', ['TawafCompletionCard(', 'detectHorizontalDragGestures', 'service.load(', 'AdvisorCheckpointStore', 'showResume', 'tawafCounter.confirm()', 'tawafCounter.undo()']),
    ('domain/umrahflow/AdvisorCheckpoint.kt', ['fun confirm()', 'fun undo()', 'fun isFresh(', 'val progressFraction']),
]
for rel, patterns in checks:
    p = java / rel
    source = p.read_text(encoding='utf-8')
    for pattern in patterns:
        assert pattern in source, f'Missing {pattern} in {rel}'
    print('PASS',rel)
advisor = (java / 'ui/advisor/UmrahAdvisorScreen.kt').read_text()
assert 'tawafRound++' not in advisor and 'tawafRound--' not in advisor
assert 'else { tawafReadingStep = 0; tawafZikrCount = 0; tawafMode = RitualMode.LISTEN }' in advisor
asset = root / 'app/src/main/assets/umrah_flow_catalog.json'
catalog = json.loads(asset.read_text(encoding='utf-8'))
assert set(('audio','translations')) <= set(catalog)
for lang in ('ru','en','uz','ar','tr','id','ms','kk','fr'):
    assert len(catalog['translations'].get(lang,{})) > 30, f'No reviewed text for {lang}'
for lang in ('ru','en','uz'):
    audio = catalog['audio'][lang]
    assert all(f'tawaf_{n}' in audio and f'safa_{n}' in audio for n in range(1,8))
print('PASS reviewed multilingual offline asset',asset.stat().st_size,'bytes')
assert (root / 'app/src/test/java/com/iumrah/beta/domain/umrahflow/UmrahFlowProgressTest.kt').is_file()
print('Stage09 checks: PASS')
