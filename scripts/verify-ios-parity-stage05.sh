#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/verify-ios-parity-stage04.sh
python3 - <<'PYIN'
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path('app/src/main')
app = root/'java/com/iumrah/beta'
xml = ET.parse(root/'AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
receivers = [r.get(ns+'name') for r in xml.findall('.//receiver')]
assert receivers.count('com.iumrah.beta.data.prayer.PrayerAlertReceiver') == 1
assert receivers.count('com.iumrah.beta.data.prayer.PrayerBootReceiver') == 1
assert (app/'domain/prayer/PrayerCalculator.kt').is_file()
assert (app/'data/prayer/PrayerPreferences.kt').is_file()
ui = (app/'ui/prayer/PrayerTimesScreen.kt').read_text()
nav = (app/'core/navigation/AppChromeStore.kt').read_text()
shell = (app/'ui/shell/AppShell.kt').read_text()
assert 'fun PrayerTimesScreen(' in ui and 'PrayerCalculator.upcoming' in ui
assert 'openPrayerTimes()' in nav and 'AppRoute.PrayerTimes -> PrayerTimesScreen' in shell
for name in ('sea','rows','crowd','motion','kaaba_corner','touch','sujud'):
    assert (root/f'res/drawable-nodpi/prayer_wallpaper_{name}.jpg').stat().st_size>10000
assert 'PrayerAlarmScheduler.sync' in ui
assert 'openPrayerTimes()' in (app/'ui/shell/SidebarDrawer.kt').read_text()
print('Stage05 prayer navigation, receiver manifest, iOS wallpapers and UI invariants passed.')
PYIN
if command -v kotlinc >/dev/null 2>&1; then
  tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
  cat > "$tmp/main.kt" <<'KT'
import com.iumrah.beta.domain.prayer.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
fun main() {
    val day = LocalDate.of(2026,10,12)
    val times = listOf("04:59","05:00","04:56","05:07","05:38","04:57","04:20","05:25")
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    PrayerPlace.presets.forEachIndexed { idx, p ->
       val daySchedule = PrayerCalculator.calculate(day,p)
       check(daySchedule.at(PrayerKind.FAJR)!!.atZone(p.zone).format(fmt) == times[idx])
       check(daySchedule.at(PrayerKind.FAJR)!! < daySchedule.at(PrayerKind.SUNRISE)!!)
       check(daySchedule.at(PrayerKind.DHUHR)!! < daySchedule.at(PrayerKind.ASR)!!)
    }
    val makkah=PrayerPlace.presets[0]
    val regular=PrayerCalculator.calculate(day,makkah)
    val hanafi=PrayerCalculator.calculate(day,makkah,hanafi=true)
    check(hanafi.at(PrayerKind.ASR)!! > regular.at(PrayerKind.ASR)!!)
    check(PrayerCalculator.upcoming(regular.at(PrayerKind.FAJR)!!.plusSeconds(1),makkah,PrayerMethod.UMM_AL_QURA,false)?.first==PrayerKind.DHUHR)
    println("Stage05 Kotlin offline calculator cross-platform golden checks passed.")
}
KT
  kotlinc app/src/main/java/com/iumrah/beta/domain/prayer/PrayerCalculator.kt "$tmp/main.kt" -include-runtime -d "$tmp/prayer.jar" >/dev/null
  java -jar "$tmp/prayer.jar"
fi
