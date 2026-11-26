#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
python3 - "$ROOT" "$OUT" <<'PY'
from pathlib import Path
import sys,re
root,out=map(Path,sys.argv[1:]); base=root/'app/src/main/java/com/iumrah/beta'
# Compile the exact production rules and actual DTO declarations without Android or serialization runtime.
for rel,name in [('models/flight/CuratedFlightRecommendationModels.kt','Curated.kt'),('models/hotel/HotelStorefrontModels.kt','Storefront.kt')]:
 s=(base/rel).read_text()
 if name=='Storefront.kt': s=s.split('@Serializable\ndata class StorefrontPackageConfiguration')[0]
 else: s=s.split('@Serializable\ndata class CuratedFlightRecommendationsResponse')[0]
 s=re.sub(r'^import kotlinx.serialization[^\n]*\n','',s,flags=re.M)
 s=re.sub(r'@(?:SerialName|JsonNames|OptIn)\([^)]*\)\s*','',s).replace('@Serializable','')
 (out/name).write_text(s)
s=(base/'ui/home/HomeGlobalPublishedFlights.kt').read_text(); rules=s[s.index('internal data class GlobalPublishedLeg'):s.index('internal class HomeGlobalCatalogue')]
p=(base/'ui/home/HomePublishedFlights.kt').read_text(); leg=p[p.index('internal data class PublishedHomeLeg'):p.index('\n}',p.index('internal data class PublishedHomeLeg'))+2]
fd=(base/'data/flight/FlightDataParity.kt').read_text(); (out/'Instant.kt').write_text('package com.iumrah.beta.data.flight\nimport java.time.*\nobject FlightDataParity {\n'+fd[fd.index('    fun instant('):fd.index('    fun matchesInstant(')]+'}\n')
rank=s[s.index('internal fun GlobalPublishedLeg.direction()'):s.index('@Composable private fun GlobalFlightCard')]
compat=p[p.index('internal object PublishedHomeRules'):p.index('private fun ft(')]
(out/'Rules.kt').write_text('package com.iumrah.beta.ui.home\nimport com.iumrah.beta.models.flight.CuratedFlightRecommendation\nimport com.iumrah.beta.models.hotel.StorefrontFlightOption\n'+ 'import com.iumrah.beta.domain.trip.*\nimport com.iumrah.beta.data.flight.*\nimport java.time.*\n'+leg+'\n'+rules+'\n'+compat+'\n'+rank)
PY
kotlinc "$ROOT/app/src/main/java/com/iumrah/beta/data/flight/FlightReferenceCatalog.kt" "$OUT/Instant.kt" "$ROOT/scripts/qa/ios8/TripStub.kt" "$OUT/Curated.kt" "$OUT/Storefront.kt" "$OUT/Rules.kt" "$ROOT/scripts/qa/ios8/RankingSmoke.kt" -include-runtime -d "$OUT/test.jar"
java -jar "$OUT/test.jar"
