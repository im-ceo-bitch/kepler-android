# iOS 7 — Home synchronization (2026-11-25)

Baseline: original `kepler-android-main 2.zip` plus all entries from `iumrah-android-update-20261125-zzzzz-ios7-parity-refined.zip`. Reference: `kepler-ios-main 7.zip`, especially `Sources/Views/Home/HomeDashboardView.swift` and its current dashboard and published-flight components. Home is now explicitly in scope. Earlier reports inside this cumulative archive retain their historical Home-exclusion context; that exclusion is superseded by this update.

## Changes

- Active Home composition now follows the current SwiftUI dashboard sequence: search, conditional active booking, conditional balance, published flights, recent activity, conditional notifications, saved journeys, planning, Friends, services, guides, Rewards, ready offers (no fabricated fallback), destinations, included services, benefits, Why iumrah, mission, Care, slogan. Legacy marketing helpers are not invoked by the root composition.
- Home uses the same updated navigation/design mode as other tabs instead of its previous compatibility exception. Added Home-owned catalogue, detail, saved-journey and hotel routes.
- Global published catalogue requests `all=1`, flattens both direct legs, deduplicates by stable identifier and sorts by departure. The full-page list has no client-side 300-row cap. Missing/invalid/non-USD fares are not advertised as USD or selectable. Incomplete or failed catalogue loads are disclosed. Five-minute shared cache, refresh and retry.
- Flight selection retains separate outbound/return state, validates cabin, direction, date and pair compatibility, and resolves the server selection into a Nuitee draft before opening hotels. No navigation if server resolution fails or the user leaves Home while resolving.
- Active-booking badge reflects actual status. Access is checked through BookingStore. Balance uses shared account-scoped rewards data and handles unknown/stale balances. Home pull refresh updates dashboard rewards, flights, bookings and notifications.
- Existing static assertions for the old marketing Home updated to reflect the actual current SwiftUI hierarchy rather than disabling them.

## Verification performed

- Kotlin PSI syntax: 280 files, 0 errors. This is NOT Gradle/Compose type checking.
- `bash scripts/verify-stage25.sh`: exit 0 after source updates.
- `bash scripts/qa/p00/test_chrome.sh`: exit 0; additional catalogue/detail/back, hotel and saved-journey route tests.
- `bash scripts/qa/home7/test_catalogue.sh`: exit 0; ten checks including 400 rows, deduplication, direct-only filtering, paired legs, no fabricated USD prices. Compiles the extracted exact production pure rules and DTO declarations without serialization annotations; does not test HTTP or Compose.
- Original Workflows preserved; archive is an overlay, no source deletions.

## Remaining limitations / required release gates

Android SDK, Gradle dependencies, Xcode/device renderers and authenticated backend are not available in this environment. No APK, end-to-end provider validation or pixel-diff result is claimed. System Liquid Glass, system typography, animation physics and platform wallet behavior remain approximations. Home photo cards and service UI use Compose equivalents and existing assets; some exact geometry, imagery, dark-mode behavior, localization and route semantics still require comparison on real devices. Historical parity gaps from preceding reports are not certified resolved by this Home update. Do not represent this as a verified 100% pixel or functional match; build and compare the current iOS and Android apps, including guest/authenticated/active-trip states and full flight-to-hotel flow.
