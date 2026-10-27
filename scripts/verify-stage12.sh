#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
bash scripts/verify-stage11.sh
FRIENDS="app/src/main/java/com/iumrah/beta/ui/hotels/IumrahFriendsCalculator.kt"
HOTELS="app/src/main/java/com/iumrah/beta/ui/hotels/HotelsScreen.kt"
HOME="app/src/main/java/com/iumrah/beta/ui/home/HomeScreen.kt"
VIDEO="app/src/main/java/com/iumrah/beta/ui/media/LoopingVideo.kt"
LOCALIZE="app/src/main/java/com/iumrah/beta/ui/booking/BookingItineraryLocalization.kt"
SERVICE="app/src/main/java/com/iumrah/beta/data/hotel/HotelCatalogService.kt"
test -f "$FRIENDS"
test -f "app/src/main/res/drawable/iumrah_friends_hotels_cover.jpg"
grep -q 'iumrah Finance' "$FRIENDS"
grep -q 'total < 16' "$FRIENDS"
grep -q 'ceil(maxOf(1, total) / 4.0)' "$FRIENDS"
grep -q 'service.storefrontPackageQuote' "$FRIENDS"
grep -q 'makkahRoomIdOverride = null' "$FRIENDS"
# Updated October 26 iOS reference embeds the calculator directly in Hotels.
# The previous modal/showcase-only implementation is intentionally superseded.
grep -q 'IumrahFriendsCalculatorCard' "$HOTELS"
! grep -q 'IumrahFriendsCalculatorSheet' "$HOTELS"
grep -q 'note = null' "$HOTELS"
grep -q 'makkahRoomIdOverride: String? = snapshot.configuration?.makkahRoomId' "$SERVICE"
python3 - "$HOME" <<'PY'
import sys
s=open(sys.argv[1], encoding='utf-8').read()
aud=s.index('item { AudienceSection(language) }')
build=s.index('item { BuildMyUmrahSection(language, chrome) }')
services=s.index('item { ServicesSection(language, chrome) }')
assert aud < build < services, 'Build my Umrah must sit directly after audience and before services'
PY
grep -q 'isStoryPresented = storyStartIndex != null' "$HOME"
grep -q 'play = pager.currentPage == index && !isStoryPresented' "$HOME"
grep -q 'isClickable = false' "$VIDEO"
grep -q 'setOnTouchListener { _, _ -> false }' "$VIDEO"
test -f "$LOCALIZE"
grep -q 'localizeServerItinerary' "$LOCALIZE"
grep -q 'Паспортный контроль и багаж' "$LOCALIZE"
grep -q 'Arrival in Saudi Arabia' "$LOCALIZE"
grep -q 'localizeServerItinerary(it, language)' app/src/main/java/com/iumrah/beta/ui/booking/BookingsHomeScreen.kt
grep -q 'localizeServerItinerary(it, language)' app/src/main/java/com/iumrah/beta/ui/booking/BookingDetailScreen.kt
echo 'Stage 012 Friends / schedule / video parity checks passed.'
