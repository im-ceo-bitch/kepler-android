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
        (r'(?m)^import androidx\.compose\.animation\.core\.animateFloat\s*$', 'animateFloat import'),
        (r'(?m)^import androidx\.compose\.ui\.unit\.sp\s*$', 'sp import'),
        (r'val availableHeight\s*=\s*maxHeight', 'capture BoxWithConstraints height'),
        (r'height\(if\s*\(page\.scene\s*==\s*4\)\s*availableHeight\s*else\s*sceneHeight\)', 'use captured height inside Column'),
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
if errors:
    for error in errors:
        print(f'STOP: {error}', file=sys.stderr)
    sys.exit(1)
print('V10 Hotfix 02: all 8 source guards passed (4 Kotlin files).')
