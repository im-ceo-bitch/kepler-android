#!/usr/bin/env python3
"""Fail fast when the four Kotlin fixes from the debug CI log are missing.
This is a source application guard, not a substitute for Gradle compilation.
"""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parent.parent
src = root / 'app/src/main/java/com/iumrah/beta'
checks = {
    'ui/esim/ESIMScreen.kt': (
        (r'AppLanguage\.ENGLISH\s*->\s*en\[key\]', 'English dictionary lookup'),
        (r'en\[key\]\?\.let\s*\{\s*com\.iumrah\.beta\.core\.localization\.L10n\.phrase\(it,\s*language\)', 'translate string rather than dictionary'),
    ),
    'ui/onboarding/OnboardingFlow.kt': (
        (r'IumrahOrbitV5\(diameter\s*=', 'shared Orbit V5 implementation'),
        (r'(?m)^import androidx\.compose\.ui\.unit\.sp\s*$', 'sp import'),
        (r'val availableHeight\s*=\s*\(maxHeight - reserve\)\.coerceAtLeast\(0\.dp\)', 'capture BoxWithConstraints height minus footer reserve'),
        (r'height\(if\s*\(page\.scene\s*==\s*4\)\s*availableHeight\.coerceAtLeast\(490\.dp\)\s*else\s*sceneHeight\)', 'use captured height inside Column'),
    ),
    'ui/shell/AppShell.kt': (
        (r'(?m)^import com\.iumrah\.beta\.ui\.account\.AccountPasswordSecurityScreen\s*$', 'account security screen import'),
    ),
    'ui/trip/CuratedRoundTripFlights.kt': (
        (r'L10n\.phrase\(en,\s*l\)', 'pass actual language parameter'),
    ),
}
errors = []
for relative, rules in checks.items():
    path = src / relative
    if not path.is_file():
        errors.append(f'{relative}: file missing')
        continue
    contents = path.read_text(encoding='utf-8')
    for pattern, description in rules:
        if not re.search(pattern, contents):
            errors.append(f'{relative}: {description} missing')
# The previous debug APK compiled; its old Advisor test failed at unit-test compilation.
# Keep the existing source guard filename so installed workflows do not need an update.
advisor = root / 'app/src/test/java/com/iumrah/beta/data/advisor/UmrahFlowServiceParityTest.kt'
if not advisor.is_file():
    errors.append('Advisor unit-test file missing')
else:
    test_source = advisor.read_text(encoding='utf-8')
    for pattern, description in (
        (r'UmrahFlowService\(api\s*=\s*APIClient\(client\s*=', 'inject transport via APIClient'),
        (r'/api/services/advisor/translations', 'unified translations route'),
        (r'/api/services/advisor/audio', 'unified audio route'),
        (r'queryParameter\("lang"\)', 'unified language parameter'),
        (r'@Test fun rejectsLegacyExternalInsecureAudioAndUnsupportedLanguage', 'audio origin safety regression'),
    ):
        if not re.search(pattern, test_source):
            errors.append(f'Advisor test: {description} missing')
    if re.search(r'UmrahFlowService\(client\s*=', test_source):
        errors.append('Advisor test still uses removed client parameter')
if errors:
    for error in errors:
        print(f'STOP: {error}', file=sys.stderr)
    sys.exit(1)
print('V10 Hotfix 03: original 8 Kotlin source guards and Advisor unit-test contract passed.')
