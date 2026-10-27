#!/usr/bin/env python3
"""Block 4 read-only iOS->Android source contract. Not a visual/device build test."""
from pathlib import Path
import argparse
import re

ROOT=Path(__file__).resolve().parent.parent
HOTELS=(ROOT/'app/src/main/java/com/iumrah/beta/ui/hotels/HotelsScreen.kt').read_text()
FRIENDS=(ROOT/'app/src/main/java/com/iumrah/beta/ui/hotels/IumrahFriendsCalculator.kt').read_text()
DETAIL=(ROOT/'app/src/main/java/com/iumrah/beta/ui/hotels/HotelDetailScreen.kt').read_text()
FLIGHTS=(ROOT/'app/src/main/java/com/iumrah/beta/ui/flights/FlightDiscoveryPanel.kt').read_text()

def need(test, message):
    assert test, message
    print('PASS:',message)

need('IumrahFriendsCalculatorCard(' in HOTELS and
     'IumrahFriendsCalculatorSheet(' not in HOTELS and
     'IumrahFriendsShowcaseCard(' not in HOTELS,
     'Live Friends calculator is embedded in Hotels list, not a modal')
need('service.storefrontPackageQuote(' in FRIENDS and
     'makkahHotelIdOverride = makkah.id' in FRIENDS and
     'madinahHotelIdOverride = madinah.id' in FRIENDS and
     'isEstimated' not in FRIENDS,
     'Inline calculator retains server quote with explicit hotels; no fake supplier price')
need('delay(420)' in FRIENDS and 'total < 16' in FRIENDS and
     'ceil(maxOf(1, total) / 4.0)' in FRIENDS and
     'if (failure is CancellationException) throw failure' in FRIENDS,
     'Calculator limits and cancellation semantics remain intact')
need('height(124.dp)' in FRIENDS and 'RoundedCornerShape(20.dp)' in FRIENDS and
     'fontSize = 22.sp' in FRIENDS and 'RoundedCornerShape(34.dp)' in FRIENDS and
     '"$" + java.math.BigDecimal(raw)' in FRIENDS and
     "IumrahColors.SystemGreen" in FRIENDS and "$value★" in FRIENDS,
     'Friends card layout and currency prefix follow iOS source')
need('Modifier.fillMaxWidth().height(204.dp)' in HOTELS and
     '.coerceIn(108.dp, 122.dp)' in HOTELS and
     'RoundedCornerShape(28.dp)' in HOTELS and
     '"★".repeat(stars.coerceIn(1, 5))' in HOTELS and
     'IumrahIconRole.RATING.color' in HOTELS,
     'Hotel card footprint / media width and star color match iOS')
need('Arrangement.spacedBy(3.dp)' in HOTELS and
     '.weight(.63f)' in HOTELS and '.weight(.37f)' in HOTELS and
     'MediaCircle(onFavorite)' in HOTELS and 'MediaCircle(onShare)' in HOTELS,
     'Hotel collage 1+2 layout and overlaid actions retained')
need('IumrahIconRole.UMRAH.color' in HOTELS and
     'CupertinoSymbol.PackageBox' in HOTELS and
     'inboundCode.equals(option.outbound.airlineCode' in HOTELS and
     '.height(118.dp)' in HOTELS and
     'shadow(14.dp, shape' in HOTELS,
     'Ready-flight card uses real outbound/return airline logos and iOS body geometry')
need('FlightDiscoveryPanel(' in HOTELS and
     'showEmbeddedAssembly = false' in HOTELS and
     'FlightAssemblyCard(' in HOTELS and
     'ready.isNotEmpty()' in HOTELS,
     'Live flight search and single fixed booking assembly preserved')
need('rememberPagerState(pageCount' in DETAIL and
     'Modifier.fillMaxWidth().height(340.dp)' in DETAIL and
     'HotelGalleryDialog(' in DETAIL,
     'Hotel detail hero 340dp, image pager, gallery navigation preserved')
need('service.offers(' in FLIGHTS and 'service.calendar(' in FLIGHTS and
     'service.recommendations(' in FLIGHTS and 'FlightFavoritesStore' in FLIGHTS,
     'Flight search continues to use current live data and favorites')

ap=argparse.ArgumentParser()
ap.add_argument('--ios', type=Path, help='Original iOS source folder (read-only)')
ap.add_argument('--ios-headers', type=Path, help='iOS signature patch source folder (read-only)')
args=ap.parse_args()
if args.ios:
    iosh=(args.ios/'Sources/Views/Tabs/HotelsHomeView.swift').read_text()
    iosd=(args.ios/'Sources/Views/Hotels/HotelDetailView.swift').read_text()
    iosf=(args.ios/'Sources/Views/Flights/IumrahFlightDiscoveryView.swift').read_text()
    need('IumrahFriendsCalculatorCard()' in iosh and
         'private let cardHeight: CGFloat = 204' in iosh and
         'let mediaWidth = min(max(proxy.size.width * 0.31, 108), 122)' in iosh and
         'let heroHeight = ((proxy.size.height - gap) * 0.63)' in iosh,
         'Verified card / calculator source geometry against original iOS')
    need('private var heroCarousel' in iosd and '.frame(height: 340)' in iosd and
         'HotelGalleryView(' in iosd,
         'Verified hotel detail photo controls against original iOS')
    need('IumrahFlightDiscoveryView' in iosf and 'flightSearchConfigurationCard' in iosf and
         'searchResultsSection' in iosf,
         'Verified flight search composition against original iOS')
if args.ios_headers:
    latest=(args.ios_headers/'Sources/Views/Tabs/HotelsHomeView.swift').read_text()
    need('IumrahWorldDotsHeaderBackground()' in latest and
         'Color.iumrahSignatureBlack' in latest and
         'minHeight: 41' in latest,
         'Current black signature header / 41pt segment reference is used')
print('BLOCK 4 SOURCE CONTRACT PASSED — not a compiled APK or device screenshot sign-off.')
