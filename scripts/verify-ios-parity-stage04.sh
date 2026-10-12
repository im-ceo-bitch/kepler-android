#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/verify-ios-parity-stage03.sh
python3 - <<'PY'
from pathlib import Path
r = Path('app/src/main/java/com/iumrah/beta')
d = (r/'ui/hotels/HotelDetailScreen.kt').read_text()
c = (r/'data/hotel/StorefrontHotelDetailCache.kt').read_text()
p = (r/'data/hotel/StorefrontCachePolicy.kt').read_text()
t = Path('app/src/test/java/com/iumrah/beta/data/hotel/StorefrontCachePolicyTest.kt').read_text()
assert 'hotelCache.restore(hotelId)' in d and 'hotelCache.save(responseDetail)' in d
assert 'if (responseDetail != null && responseDetail.id == hotelId)' in d
assert 'if (ready.isNotEmpty()) {' in d and 'packages = ready' in d
assert d.count('currentCoroutineContext().ensureActive()') >= 5
assert 'navigationBarsPadding()' in d
assert 'AtomicFile(' in c and 'SHA-256' in c and 'file.failWrite(stream)' in c
assert 'cached.detail.takeIf { it.id == id }' in c
assert '2 * 1024 * 1024' in c and 'packageQuote' not in c
assert '7L * 24L * 60L * 60L * 1000L' in p
assert 'assertFalse(hotelDetailCacheIsFresh(now + 1L, now))' in t
print('Stage04 hotel detail and cache source invariants passed.')
PY
if command -v kotlinc >/dev/null 2>&1; then
  tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
  cat > "$tmp/cachecheck.kt" <<'KT'
import com.iumrah.beta.data.hotel.catalogCacheIsFresh
import com.iumrah.beta.data.hotel.hotelDetailCacheIsFresh
fun main() {
  val now = 1_800_000_000_000L
  check(catalogCacheIsFresh(now - 24L * 60L * 60L * 1_000L, now))
  check(!catalogCacheIsFresh(now - 49L * 60L * 60L * 1_000L, now))
  check(hotelDetailCacheIsFresh(now - 6L * 24L * 60L * 60L * 1_000L, now))
  check(!hotelDetailCacheIsFresh(now - 8L * 24L * 60L * 60L * 1_000L, now))
  check(!hotelDetailCacheIsFresh(now + 1L, now))
  println("Stage04 Kotlin cache policy runtime checks passed.")
}
KT
  kotlinc app/src/main/java/com/iumrah/beta/data/hotel/StorefrontCachePolicy.kt "$tmp/cachecheck.kt" -include-runtime -d "$tmp/cachecheck.jar"
  java -jar "$tmp/cachecheck.jar"
fi
