#!/usr/bin/env python3
"""Stage 10 reproducible repository source/resource checks, NOT an APK or pixel test.

Exit non-zero for structural regressions. Avoids internet, secrets and Android SDK.
The actual `:app:testDebugUnitTest :app:assembleDebug` MUST also pass separately.
"""
from __future__ import annotations

import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'app/src/main/java/com/iumrah/beta'
RES = ROOT / 'app/src/main/res'
errors: list[str] = []


def assert_condition(ok: bool, desc: str):
    if not ok:
        errors.append(desc)


def read(path: str) -> str:
    file = BASE / path
    if not file.is_file():
        errors.append(f'Missing source: {path}')
        return ''
    return file.read_text(encoding='utf-8')


# Every route in the navigation model must be handled by the Compose shell.
nav = read('core/navigation/AppChromeStore.kt')
shell = read('ui/shell/AppShell.kt')
routes = set(re.findall(r'\bdata\s+(?:object|class)\s+(\w+)[^\n]*:\s*AppRoute', nav))
assert_condition(len(routes) > 45, f'Too few declared routes: {len(routes)}')
for route in sorted(routes):
    assert_condition(f'AppRoute.{route}' in shell, f'Route missing in UI shell: {route}')

# Required functional routes from November iOS baseline.
feature_sources = {
    'onboarding': ['ui/onboarding/OnboardingFlow.kt'],
    'home': ['ui/home/HomeScreen.kt', 'ui/components/IumrahRootPageHeader.kt'],
    'aviation': ['ui/flights/IumrahGuidedFlightsScreen.kt', 'ui/flights/GuidedFlightSelectionEngine.kt'],
    'Nuitee hotels': ['ui/hotels/NuiteeHotelsCatalogScreen.kt', 'ui/trip/NuiteeJourneyHotelsScreen.kt'],
    'package review': ['ui/packageflow/NuiteeJourneyReviewScreen.kt', 'domain/nuitee/NuiteeJourneyPricing.kt'],
    'booking': ['ui/booking/BookingsHomeScreen.kt', 'data/booking/BookingStore.kt'],
    'account security': ['ui/account/AccountSecurityParity.kt', 'ui/account/AccountPasswordSecurityScreen.kt'],
    'services': ['ui/home/IumrahServicesScreen.kt'],
    'Umrah Flow': ['ui/umrahflow/UmrahFlowScreen.kt', 'domain/umrahflow/UmrahFlowProgress.kt'],
    'notifications': ['data/notification/ClientNotificationStore.kt'],
    'analytics': ['core/analytics/IumrahIntelligence.kt'],
}
for feature, sources in feature_sources.items():
    for src in sources:
        assert_condition((BASE / src).is_file(), f'{feature}: missing {src}')

# All source-managed UI resource IDs must resolve; Android aapt can't build otherwise.
resource_names: dict[str, set[str]] = defaultdict(set)
for directory in RES.iterdir():
    if not directory.is_dir():
        continue
    kind = directory.name.split('-')[0]
    if kind not in ('drawable', 'mipmap', 'font', 'raw', 'xml'):
        continue
    for file in directory.iterdir():
        if file.is_file():
            resource_names[kind].add(file.name.split('.')[0])

# Font assets are intentionally generated at Gradle preBuild by a checked-in installer.
# Their absence in the ZIP is correct; don't mistake generated resources for missing files.
gradle = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
bootstrap = ROOT / 'scripts/install-cupertino-assets.py'
if bootstrap.is_file() and 'dependsOn(bootstrapCupertinoIcons)' in gradle:
    if 'outputs.file(file("src/main/res/font/cupertino_icons.ttf"))' in gradle:
        resource_names['font'].add('cupertino_icons')
    if 'outputs.file(file("src/main/res/font/lato_$weight.ttf"))' in gradle:
        resource_names['font'].update({'lato_light', 'lato_regular', 'lato_medium',
                                      'lato_semibold', 'lato_bold', 'lato_heavy'})

kt_files = list(BASE.rglob('*.kt'))
assert_condition(len(kt_files) >= 160, f'Kotlin module unexpectedly shrank: {len(kt_files)}')
for file in kt_files:
    source = file.read_text(encoding='utf-8')
    for kind, key in re.findall(r'\bR\.(drawable|mipmap|font|raw|xml)\.(\w+)', source):
        assert_condition(key in resource_names[kind], f'Missing R.{kind}.{key} from {file.relative_to(ROOT)}')

# Parse all Android XML resources and manifest; never assume well-formed UI XML.
xml_files = list(RES.rglob('*.xml'))
manifest = ROOT / 'app/src/main/AndroidManifest.xml'
for file in [manifest, *xml_files]:
    try:
        ET.parse(file)
    except ET.ParseError as exc:
        errors.append(f'Bad XML {file.relative_to(ROOT)}: {exc}')
assert_condition(manifest.is_file(), 'Missing AndroidManifest.xml')
if manifest.is_file():
    contents = manifest.read_text(encoding='utf-8')
    assert_condition('android:usesCleartextTraffic="false"' in contents, 'Cleartext HTTP allowed unexpectedly')
    assert_condition('android:allowBackup="false"' in contents, 'User backup unexpectedly enabled')

# Check bundled ritual content, not network-fetched ritual text.
asset = ROOT / 'app/src/main/assets/umrah_flow_catalog.json'
try:
    catalog = json.loads(asset.read_text(encoding='utf-8'))
    for language in ('ru', 'en', 'uz'):
        assert_condition(len(catalog['translations'].get(language, {})) > 30,
                         f'Missing reviewed {language} ritual instructions')
        for i in range(1, 8):
            for segment in ('tawaf', 'safa'):
                assert_condition(f'{segment}_{i}' in catalog['audio'].get(language, {}),
                                 f'Missing ritual audio reference {language}/{segment}_{i}')
except (OSError, KeyError, ValueError) as exc:
    errors.append(f'Invalid bundled ritual catalog: {exc}')

# Guard critical data paths from accidental rewiring / unintended booking completion.
flight = read('ui/flights/GuidedFlightSelectionEngine.kt')
assert_condition('outbound.source != inbound.source' in flight, 'Cross-source airline pairing no longer guarded')
pricing = read('domain/nuitee/NuiteeJourneyPricing.kt')
assert_condition('trip.children > 0 || trip.infants > 0' in pricing, 'Unknown child/infant fare guard removed')
assert_condition('!NuiteeRules.validRoomRate(selection.rate)' in pricing, 'Invalid Nuitee rates no longer guarded')
checkout = read('ui/booking/BookingCheckoutScreen.kt')
assert_condition('"booking_completed"' not in checkout, 'Checkout is not a paid/completed booking')
assert_condition('"booking_submitted"' in checkout, 'Server-confirmed booking telemetry missing')

# The existing GitHub workflow has TWO distinct jobs. A green push only proves applying the ZIP.
workflow = ROOT / '.github/workflows/android-ci.yml'
if workflow.is_file():
    wf = workflow.read_text(encoding='utf-8')
    assert_condition("github.event_name == 'workflow_dispatch'" in wf,
                     'Build trigger changed; reassess acceptance steps')
    assert_condition('gradle --stacktrace :app:assembleDebug' in wf,
                     'Manual CI build command is missing')
    assert_condition('gradle --stacktrace :app:testDebugUnitTest' in wf,
                     'Manual CI unit-test command is missing')

if errors:
    for message in errors:
        print('FAIL:', message, file=sys.stderr)
    raise SystemExit(f'STAGE10 offline checks FAILED ({len(errors)} issue(s))')
print(f'STAGE10 source/resource checks PASS: {len(kt_files)} Kotlin files, '
      f'{len(routes)} UI routes, {len(xml_files) + 1} XML documents, '
      f'{len(feature_sources)} feature groups, bundled ritual catalog')
print('NOT VERIFIED: Android Gradle/Compose compilation, API E2E, device screenshots or pixel parity.')
