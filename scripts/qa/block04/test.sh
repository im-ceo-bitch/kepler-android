#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
A=app/src/main/java/com/iumrah/beta/domain
Q=scripts/qa/block04
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
kotlinc "$A/prayer/PrayerCalculator.kt" "$A/prayer/PrayerNotificationPolicy.kt" "$A/umrahflow/AdvisorCheckpoint.kt" "$A/umrahflow/AdvisorContentPolicy.kt" "$Q/Policy.kt" -include-runtime -d "$T/policy.jar"
java -jar "$T/policy.jar"
if [[ $# -eq 1 ]]; then
 swiftc "$1/Sources/PrayerTimes/IumrahPrayerCalculator.swift" "$Q/main.swift" -o "$T/ios"
 "$T/ios" > "$T/ios.txt"
 kotlinc "$A/prayer/PrayerCalculator.kt" "$Q/Main.kt" -include-runtime -d "$T/android.jar"
 java -jar "$T/android.jar" > "$T/android.txt"
 python3 - "$T" <<'PY'
import sys,pathlib
p=pathlib.Path(sys.argv[1]);load=lambda n:{tuple(x.split('|')[:-1]):float(x.split('|')[-1]) for x in (p/n).read_text().splitlines()}
a,b=load('ios.txt'),load('android.txt');assert len(a)==4480 and a.keys()==b.keys()
d=max(abs(a[k]-b[k]) for k in a);print(f'4480 Swift/Kotlin instants; max delta {d:.6f}s');assert d<.1
PY
fi
