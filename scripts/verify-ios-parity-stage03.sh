#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/verify-ios-parity-stage02.sh
python3 - <<'PY'
from pathlib import Path
p=Path('app/src/main/java/com/iumrah/beta')
h=(p/'ui/hotels/HotelsScreen.kt').read_text()
f=(p/'ui/flights/FlightDiscoveryPanel.kt').read_text()
a=(p/'ui/flights/FlightDiscoveryParityComponents.kt').read_text()
c=(p/'data/hotel/StorefrontCatalogCache.kt').read_text()
s=(p/'data/hotel/StorefrontCachePolicy.kt').read_text()
t=Path('app/src/test/java/com/iumrah/beta/data/hotel/StorefrontCachePolicyTest.kt').read_text()
assert 'statusBarsPadding()' in h and 'PullToRefreshBox(' in h
assert 'LaunchedEffect(origin, refreshSeed)' in h
assert all(x in h for x in ('async {', 'coroutineScope {', 'currentCoroutineContext().ensureActive()', 'catalogCache.restore()', 'catalogCache.save(makkah, madinah)'))
assert 'if (board != HotelsBoard.FLIGHTS) item {' in h
assert 'if (board == HotelsBoard.FLIGHTS) {' in h and 'FlightAssemblyCard(' in h
assert 'showEmbeddedAssembly = false' in h and 'if (showEmbeddedAssembly) FlightAssemblyCard' in f
assert 'expandedState = assemblyExpanded' in h and 'onExpandedChange = { assemblyExpanded = it }' in h
assert 'if (displayedOrigin != origin)' in h and 'hotelPackages = emptyList()' in h
assert 'if (selectionCount == 0) return' not in a
assert 'selectionCount > 0 &&' in a and 'onExpandedChange?.invoke(!expanded)' in a
assert 'AtomicFile(' in c and 'file.finishWrite(stream)' in c and 'file.failWrite(stream)' in c
assert 'storefront-hotels-v1' in c and '4 * 1024 * 1024' in c
assert 'pricePerPerson' not in c and 'totalPackagePrice' not in c
assert 'maxAgeMillis' in s and 'savedAt <= now' in s
assert 'assertFalse(catalogCacheIsFresh(now + 1L, now))' in t
assert 'app/build.gradle.kts' in Path('.github/workflows/android-ci.yml').read_text()
print('Stage03 storefront layout/flow/offline cache source invariants passed.')
PY
if command -v kotlinc >/dev/null 2>&1; then
  tempdir=$(mktemp -d)
  trap 'rm -rf "$tempdir"' EXIT
  cat > "$tempdir/check.kt" <<'KT'
import com.iumrah.beta.data.hotel.catalogCacheIsFresh
fun main() {
  val now = 1_800_000_000_000L
  check(catalogCacheIsFresh(now - 1_000L, now))
  check(catalogCacheIsFresh(now, now))
  check(!catalogCacheIsFresh(now + 1L, now))
  check(!catalogCacheIsFresh(0L, now))
  check(!catalogCacheIsFresh(now - 49L * 60 * 60 * 1_000, now))
  println("Stage03 cache validity Kotlin runtime tests passed.")
}
KT
  kotlinc app/src/main/java/com/iumrah/beta/data/hotel/StorefrontCachePolicy.kt "$tempdir/check.kt" -include-runtime -d "$tempdir/check.jar"
  java -jar "$tempdir/check.jar"
else
  echo 'kotlinc unavailable; run StorefrontCachePolicyTest in GitHub CI.'
fi
