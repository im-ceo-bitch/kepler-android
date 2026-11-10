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
    ('ui/shell/SidebarDrawer.kt', ['chrome.openUmrahFlow()']),
    ('domain/umrahflow/UmrahFlowProgress.kt', ['enum class UmrahPhase', 'fun completeTawafTurn()', 'fun completeSaiLeg()', 'tawafCompleted == 7', 'saiCompleted == 7']),
    ('domain/umrahflow/UmrahFlowAudioUrlPolicy.kt', ['coaqrsapnpyutsxflsru.supabase.co', '/storage/v1/object/public/']),
    ('domain/umrahflow/UmrahFlowCheckpointStore.kt', ['fun restore()', 'fun save(', 'fun clear()']),
    ('data/advisor/UmrahFlowService.kt', ['offlineCatalog', 'UmrahFlowAudioUrlPolicy::isAllowed', 'snapshot.translations + requestedText', 'snapshot.audio + requestedAudio']),
    ('ui/umrahflow/UmrahFlowScreen.kt', ['FlowSwipeControl(', 'FlowOrbit(', 'service.load(', 'UmrahFlowCheckpointStore', 'playAudio(', 'showResume']),
]
for rel, patterns in checks:
    p = java / rel
    source = p.read_text(encoding='utf-8')
    for pattern in patterns:
        assert pattern in source, f'Missing {pattern} in {rel}'
    print('PASS',rel)
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
