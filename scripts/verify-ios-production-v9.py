#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET
r = Path(__file__).resolve().parents[1]
s = r/'app/src/main/java/com/iumrah/beta'
read = lambda p: (s/p).read_text()
manifest = ET.parse(r/'app/src/main/AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
assert any(x.attrib.get(ns+'name')=='android.permission.ACCESS_NETWORK_STATE' for x in manifest.findall('uses-permission'))
a = read('core/network/APIClient.kt'); m = read('core/network/IumrahConnectivityMonitor.kt'); h = read('ui/hotels/HotelsScreen.kt'); b = read('data/booking/BookingStore.kt'); v = read('core/security/SecureJsonStore.kt')
assert 'call.awaitResult(consume)' in a and 'call.execute()' not in a and 'retryOnConnectionFailure(false)' in a
assert 'MAX_JSON_BYTES' in a and 'MAX_MEDIA_BYTES' in a and 'readBounded(' in a
assert 'invokeOnCancellation { cancel() }' in read('core/network/HttpCallAwait.kt')
assert 'probeJob?.cancel()' in m and 'generation == current' in m and '20_000L' in m
assert 'connectivityMonitor.refresh()' in read('IumrahApp.kt')
assert 'service.hotelDetail(hotel.id)' not in h and 'HotelCover(hotel.coverImageURL)' in h and 'remember(hotelPackages)' in h
f = read('ui/flights/FlightDiscoveryPanel.kt')
assert 'currentCoroutineContext().ensureActive()' in f and 'searchGeneration.get() == generation' in f and 'retrySeed += 1' in f
assert 'check(createMutex.tryLock())' in b and 'createMutex.unlock()' in b
assert 'secretKey(createIfMissing = false)' in v and 'prefs.edit().remove(key).apply()' not in v
print('V9 production source guards passed (not APK compilation).')
