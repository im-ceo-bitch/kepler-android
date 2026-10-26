#!/usr/bin/env python3
"""Static regression guard for source-verified SwiftUI Hotels -> Compose parity.

Only the Android tree is modified. Use --ios with the untouched original iOS ZIP
extraction for cross-platform source checks; without it the Android invariants
remain independently testable. Not a substitute for screenshot tests/APK builds.
"""
import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ANDROID = ROOT / 'app/src/main/java/com/iumrah/beta/ui/hotels/HotelsScreen.kt'


def verify(condition: bool, label: str):
    if not condition:
        raise AssertionError(label)
    print('PASS:', label)


def function_body(text: str, name: str):
    match = re.search(r'private fun ' + re.escape(name) + r'\(', text)
    assert match, name
    next_fun = re.search(r'\n(?:@Composable\n)?private fun \w+\(', text[match.end():])
    return text[match.start():match.end() + next_fun.start()] if next_fun else text[match.start():]


def main():
    arg = argparse.ArgumentParser()
    arg.add_argument('--ios', type=Path, help='Path to iOS source reference WITH header patch overlaid (read-only).')
    args = arg.parse_args()
    android = ANDROID.read_text(encoding='utf-8')
    tile = function_body(android, 'StorefrontSegmentedControl')
    airport = function_body(android, 'AirportSelector')
    header = function_body(android, 'StorefrontSectionHeader')
    verify('height(41.dp)' in tile and '.clip(CircleShape)' in tile and
           'Arrangement.spacedBy(7.dp)' in tile and 'Modifier.size(13.dp)' in tile,
           'Oct 26 iOS monochrome Hotels / Flights / Sunday: 41pt capsules, 7pt gaps, 13pt symbols.')
    verify('if (active) Color.White else Color.Transparent' in tile and
           'if (active) IumrahColors.SignatureBlack else Color.White' in tile and
           'if (active) 0f else .38f' in tile and
           not all(f'IumrahColors.{color}' in tile for color in ('SystemOrange', 'SystemBlue', 'SystemGreen')),
           'Monochrome board selection supersedes the older orange/blue/green 94pt tiles.')
    verify('IumrahSignatureWorldBackground(Modifier.matchParentSize())' in android and
           'drawsSignatureBackground = false' in android and
           android.index('StorefrontSegmentedControl(language, board)') < android.index('AirportSelector('),
           'One world-dots backdrop covers both header and navigation; airport stays below it.')
    verify('Triple(HotelsBoard.HOTELS' in tile and 'CupertinoSymbol.Building' in tile and
           'CupertinoSymbol.Airplane' in tile and 'CupertinoSymbol.Calendar' in tile,
           'Three board actions use licensed Cupertino glyphs, never Unicode substitutes.')
    verify('IumrahPressable(' in airport and 'onClick = onClick' in airport and
           'onClick = onLocate' in airport and 'IumrahIconBadge(' in airport and
           'CupertinoSymbol.AirplaneTakeoff' in airport and not re.search(r'^    IumrahPressable\(', airport, re.M),
           'Airport and geolocation are sibling press targets; shared container is not clickable.')
    verify('if (board != HotelsBoard.FLIGHTS) item' not in android and
           'item {\n            AirportSelector(' in android,
           'Shared airport selector remains visible on all three boards, including Flights.')
    verify('readyPackagesTitle(language)' in android and 'eyebrow = "FLIGHT FIRST"' in android and
           'readyPackagesSubtitle(language)' in android and 'else if (loading)' in android and
           'LoadingStorefront(language, readyPackagesLoadingText(language))' in android and
           'subtitle?.let' in header,
           'Ready-flight board matches iOS localized heading, subtitle and loading state.')
    verify(all(label in android for label in ('Готовые пакеты', 'Ready packages', 'Tayyor paketlar', 'Тайёр пакетлар')),
           'Ready package labels cover all four Android-supported locales.')
    verify('val initial = coroutineScope' in android and 'onClick = { chrome.openFlightPackage(preview.id) }' in android,
           'Original concurrent service loading and Flight First navigation remain unchanged.')
    if args.ios:
        ios_path = args.ios / 'Sources/Views/Tabs/HotelsHomeView.swift'
        ios = ios_path.read_text(encoding='utf-8')
        airport_ios = (args.ios / 'Sources/Views/Airports/AirportSelectorButton.swift').read_text(encoding='utf-8')
        verify('.frame(maxWidth: .infinity, minHeight: 41)' in ios and
               'HStack(spacing: 7)' in ios and
               'Color.iumrahSignatureBlack' in ios and
               'IumrahWorldDotsHeaderBackground()' in ios,
               'October 26 iOS patched monochrome board tile geometry/palette independently confirmed.')
        verify('AirportSelectorButton(' in ios and ios.index('AirportSelectorButton(') < ios.index('switch board') and
               'Button {\n                isPresented = true' in airport_ios and 'Button {\n                locate()' in airport_ios,
               'iOS airport selector is outside board switch and has two separate buttons.')
        verify('readyPackagesTitle' in ios and 'readyPackagesEyebrow' in ios and 'readyPackagesSubtitle' in ios and
               'else if storefront.isLoading {' in ios,
               'iOS ready packages header/loading semantics confirmed against untouched source.')
    print('Source parity checked. Device screenshots, density, rasterization and E2E remain unverified.')


if __name__ == '__main__':
    main()
