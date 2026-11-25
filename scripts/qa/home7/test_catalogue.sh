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
(out/'Rules.kt').write_text('package com.iumrah.beta.ui.home\nimport com.iumrah.beta.models.flight.CuratedFlightRecommendation\nimport com.iumrah.beta.models.hotel.StorefrontFlightOption\n'+leg+'\n'+rules)
PY
kotlinc "$OUT/Curated.kt" "$OUT/Storefront.kt" "$OUT/Rules.kt" "$ROOT/scripts/qa/home7/CatalogueSmoke.kt" -include-runtime -d "$OUT/test.jar"
java -jar "$OUT/test.jar"
