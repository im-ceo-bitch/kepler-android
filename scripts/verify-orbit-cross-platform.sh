#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
IOS=${1:?Usage: bash scripts/verify-orbit-cross-platform.sh /path/to/kepler-ios-main}
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
SWIFT="$IOS/Sources/Views/Components/IumrahOrbitV5Geometry.swift"
KOTLIN=app/src/main/java/com/iumrah/beta/domain/orbit/OrbitV5Geometry.kt
swiftc "$SWIFT" scripts/qa/OrbitCrossPlatform.swift -o "$TMP/ios"
kotlinc "$KOTLIN" scripts/qa/OrbitCrossPlatform.kt -d "$TMP/android.jar"
"$TMP/ios" > "$TMP/ios.csv"
kotlin -cp "$TMP/android.jar" OrbitCrossPlatformKt > "$TMP/android.csv"
python3 - "$TMP/ios.csv" "$TMP/android.csv" "$IOS/Sources/Views/Components/IumrahOrbitV5.swift" "$KOTLIN" <<'PY'
import csv, math, re, sys
from pathlib import Path
with open(sys.argv[1]) as a, open(sys.argv[2]) as b:
    ios, android = list(csv.reader(a)), list(csv.reader(b))
assert len(ios) == len(android) == 101 * (2+96+12)
n = 0; maximum = 0.0
for i, (a,b) in enumerate(zip(ios,android)):
    assert len(a)==len(b), f'column count row {i}'
    for j,(x,y) in enumerate(zip(a,b)):
        x,y=float(x),float(y)
        assert math.isfinite(x) and math.isfinite(y), (i,j,x,y)
        delta=abs(x-y); maximum=max(delta,maximum); n+=1
        assert delta <= 1e-8, f'row {i}, column {j}: Swift {x}, Kotlin {y}'
# Read palette literals from actual renderer source (Swift) and geometry (Kotlin).
a=Path(sys.argv[3]).read_text().split('private static let palette: [UInt32] = [',1)[1].split(']',1)[0]
b=Path(sys.argv[4]).read_text().split('val palette = listOf(',1)[1].split(')',1)[0]
assert re.findall(r'0x[0-9a-fA-F]+',a)==re.findall(r'0x[0-9a-fA-F]+',b)
print(f'PASS: {n} Swift/Kotlin numerical values, {len(ios)} rows, max abs error {maximum:.3g}; 22 palette colors match.')
print('Geometry/source palette only. Does not certify actual rendering or animation frame timing.')
PY
