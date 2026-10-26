#!/usr/bin/env python3
"""Fail fast on specific SwiftUI/Compose component regressions (source-level only).

This is intentionally *not* a pixel-perfect claim and does not need an iOS
checkout in the Android repo or any remote credentials.
"""
from pathlib import Path
import argparse
import re

ROOT = Path(__file__).resolve().parent.parent
CORE = ROOT / 'app/src/main/java/com/iumrah/beta'


def check(ok: bool, why: str):
    if not ok:
        raise AssertionError(why)
    print('PASS:', why)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--ios', type=Path, help='Optional ORIGINAL iOS checkout, read-only')
    args = parser.parse_args()
    theme = (CORE / 'core/design/IumrahTheme.kt').read_text()
    header = (CORE / 'ui/components/IumrahRootPageHeader.kt').read_text()
    filters = (CORE / 'ui/trip/FlightSearchFiltersCard.kt').read_text()
    check('"PAYMENT_PENDING" -> IumrahColors.SystemOrange' in theme and
          '"PAYMENT_PENDING", "AVAILABILITY_CONFIRMED"' not in theme,
          'booking status palette follows iOS source (no false payment-pending label)')
    check('val IumrahFontFamily = FontFamily(' in theme and
          'R.font.lato_regular' in theme and 'R.font.lato_bold' in theme,
          'Lato Regular/Bold remain globally wired')
    check('Box(modifier.width(88.dp).height(34.dp)' in header and
          'targetValue = if (expanded) 88.dp else 34.dp' in header,
          'Home connectivity indicator reserves iOS 88pt slot')
    check('rotationZ = rotation' not in header and 'Brush.sweepGradient(*rotatedStops.toTypedArray())' in header,
          'animated gradient orbits without rotating whole pill or globe')
    check('CupertinoSymbol.BellSignal else CupertinoSymbol.Bell' in header,
          'notification icon uses matching Cupertino filled-badge/plain-bell variants')
    check('FilterMenuTile(' in filters and
          'values = FlightTimeWindow.entries' in filters and
          'values = (0..2).toList()' in filters,
          'time and baggage selectors use two native menus, matching SwiftUI')
    check('Modifier.fillMaxWidth().height(54.dp)' in filters and
          'cornerRadius = 16.dp' in filters and '.height(48.dp)' in filters,
          'flight filter menu/price-control geometry follows iOS')
    check('IumrahIconBadge(' in filters and 'IumrahColors.SystemGray' in filters,
          'flight filter icon is a solid semantic Cupertino badge')
    check('onChange(FlightSearchFilters())' in filters and 'background = Color.Transparent' in filters,
          'reset leaves the data-model defaults intact; no filled reset capsule')
    if args.ios:
        ios = args.ios / 'Sources'
        design = (ios / 'Core/DesignSystem.swift').read_text()
        ios_filters = (ios / 'Views/Trip/FlightSearchFiltersCard.swift').read_text()
        ios_header = (ios / 'Views/Components/IumrahRootPageTitle.swift').read_text()
        ios_connectivity = (ios / 'Views/Components/ConnectivityStatusPill.swift').read_text()
        ios_model = (ios / 'FlightEngine/FlightSearchRequest.swift').read_text()
        check('private let expandedWidth: CGFloat = 88' in ios_connectivity and
              'private let compactWidth: CGFloat = 34' in ios_connectivity,
              'fixed header-width reference verified against original SwiftUI')
        check('"bell.badge.fill" : "bell"' in ios_header and
              'case "PAYMENT_PENDING": return .paymentPending' in design,
              'iOS symbol and booking-status sources checked')
        check('menuTile(title: copy(.departure)' in ios_filters and
              'menuTile(title: copy(.arrival)' in ios_filters and
              'countMenu(title: copy(.carryOn)' in ios_filters and
              'countMenu(title: copy(.checked)' in ios_filters,
              'iOS time/baggage menus still reference layout mirrored in Compose')
        check('static let `default` = FlightSearchFilters()' in ios_model,
              'flight filter reset targets same default model construction as iOS')
    print('NEXT STAGE: source-level parity checks passed; device screenshot/build checks remain required.')


if __name__ == '__main__':
    main()
