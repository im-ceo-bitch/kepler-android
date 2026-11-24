#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
a="{http://schemas.android.com/apk/res/android}"
m=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot();app=m.find('application')
assert app.get(a+'usesCleartextTraffic')=='false'
assert app.get(a+'allowBackup')=='false'
assert app.get(a+'networkSecurityConfig')=='@xml/network_security_config'
n=ET.parse(root/'app/src/main/res/xml/network_security_config.xml').getroot()
assert n.find('base-config').get('cleartextTrafficPermitted')=='false'
assert not any(e.get('cleartextTrafficPermitted')=='true' for e in n.iter())
assert any(e.get(a+'name')=='android.permission.RECEIVE_BOOT_COMPLETED' for e in m.findall('uses-permission'))
activity=next(e for e in app.findall('activity') if e.get(a+'name')=='com.iumrah.beta.MainActivity')
links=[e.attrib for e in activity.iter('data')]
assert any(e.get(a+'scheme')=='https' and e.get(a+'host')=='iumrah.app' and e.get(a+'pathPrefix')=='/s/' for e in links)
assert any(e.get(a+'pathPrefix')=='/offer/' for e in links)
assert any(e.get(a+'host')=='widget' for e in links)
assert any(e.get(a+'name')=='com.iumrah.beta.data.prayer.PrayerBootReceiver' for e in app.findall('receiver'))
b=root/'app/src/main/java/com/iumrah/beta'
assert 'chrome.openPublicTrip(parts[1])' in (b/'MainActivity.kt').read_text()
assert 'data class PublicTrip' in (b/'core/navigation/AppChromeStore.kt').read_text()
assert 'is AppRoute.PublicTrip ->' in (b/'ui/shell/AppShell.kt').read_text()
v=(b/'ui/booking/PublicTripShareScreen.kt').read_text()
assert 'MIXED_CONTENT_NEVER_ALLOW' in v and 'allowFileAccess = false' in v and 'allowContentAccess = false' in v
assert 'addJavascriptInterface' not in v and 'loadUrl("https://iumrah.app/s/$token")' in v
print('Public trip merge: HTTPS-only, current manifest components, existing links, read-only route PASS')
