#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PYTEST'
from pathlib import Path
import xml.etree.ElementTree as ET
base=Path('app/src/main')
manifest=ET.parse(base/'AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
app=manifest.find('application')
assert app is not None
names={'ActiveTripWidget':'active_trip','BookingStatusWidget':'booking_status','PlannedUmrahWidget':'planned_umrah','IdentityWidget':'identity','UmrahCountdownWidget':'umrah_countdown'}
for cls, name in names.items():
    receiver=[r for r in app.findall('receiver') if (r.get(ns+'name') or '').endswith('.'+cls)]
    assert len(receiver)==1, cls
    meta=receiver[0].find('meta-data')
    assert meta is not None and meta.get(ns+'resource')=='@xml/iumrah_widget_'+name
    provider=ET.parse(base/'res/xml'/('iumrah_widget_'+name+'.xml')).getroot()
    assert provider.get(ns+'initialLayout')=='@layout/iumrah_widget_card'
ET.parse(base/'res/layout/iumrah_widget_card.xml')
ET.parse(base/'res/drawable/iumrah_widget_background.xml')
strings=ET.parse(base/'res/values/strings.xml').getroot()
keys={e.get('name') for e in strings.findall('string')}
assert all('widget_'+name+'_description' in keys for name in names.values())
code=Path('app/src/main/java/com/iumrah/beta')
widgets=(code/'core/widget/IumrahWidgets.kt').read_text()
assert 'accessToken' not in widgets and 'quoteProof' not in widgets
assert 'setOnClickPendingIntent' in widgets and 'getSharedPreferences(STORE, Context.MODE_PRIVATE)' in widgets
assert 'IumrahWidgetSync.sync(' in (code/'IumrahApp.kt').read_text()
assert 'IumrahWidgetSync.refreshAll(context)' in (code/'ui/planning/UmrahPlanScreen.kt').read_text()
assert 'uri.host.equals("widget"' in (code/'MainActivity.kt').read_text()
journey=(code/'domain/journey/JourneyStore.kt').read_text()
assert 'quoteRequestGeneration' in journey and 'quoteInputs' in journey
assert 'Package selection changed while fetching the quote.' in journey
assert 'AdvisorFluidOrb(' in (code/'ui/advisor/UmrahAdvisorScreen.kt').read_text()
test=Path('app/src/test/java/com/iumrah/beta/domain/booking/BookingFlowContractParityTest.kt')
assert test.exists() and test.read_text().count('@Test')>=3
print('Stage 08 manifest/widgets/Advisor/quote race and booking-contract structural assertions: PASS')
PYTEST
bash scripts/verify-ios-parity-stage07.sh
