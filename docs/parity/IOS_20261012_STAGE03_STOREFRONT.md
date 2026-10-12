# Android ⇄ iOS source parity | Stage 03: Hotels + Flights storefront

**Reference:** `kepler-ios-main 5.zip`: `Sources/Views/Tabs/HotelsHomeView.swift`, `Sources/State/HotelStorefrontStore.swift`.

**Applies on top of:** Stage 01 + Stage 02. The delivered ZIP is cumulative and root-relative. It does not change Gradle, CI workflows, signing, API keys, checkout pricing, or locale resources.

## Hotel storefront

- Align the scroll root to the native status-bar safe area. Keep 18 dp side insets and the original 204 dp Hotel First card geometry. Avoid rendering the title under the Android status bar on edge-to-edge Android 15/16.
- Add pull-to-refresh to Hotels/Flights/Sunday boards, matching iOS `refreshable`. Every refresh cancels the previous UI-bound load on new origin/refresh generation.
- Independent backend requests for Makkah, Madinah, published flight options, Hotel First snapshots, and Flight First snapshots start concurrently, as on iOS. After the first two return, hotel rows can render **before** Package Engine finishes. Partial network failure does not discard the other city's list.
- Public hotel metadata has an atomic, versioned 48-hour cache; this supports fast/reliable listing. No stale package amounts, payment records, personal data, traveler IDs or tokens are stored with it.
- On departure-airport change, clear the previous airport's flight/package results before loading the next origin. Ready Hotel First snapshots are preserved across a partial/empty refresh for the *same* airport; no made-up local quotes are introduced.
- Distinct loading/error state is preserved; existing card route/price/favorite/share implementations remain intact.

## Flights storefront

- The outer airport-selection card is absent on Flights board (iOS uses its own in-discovery origin selector). Other boards continue to show the selector.
- The Umrah package-assembly tray is a **fixed bottom overlay** on the Flights board, above the floating Android tab bar. It is no longer duplicated or lost inside the long list.
- The tray is shown even when nothing is selected. Expand/collapse, clear, published/Aviasales selection, enablement gating and continuation into the existing hotel selection route are retained.
- The list reserves 220 dp or 410 dp of clearance based on the tray expansion state. Only one tray is displayed.

## Verifications

- `bash scripts/verify-ios-parity-stage03.sh`: Stage 01, Stage 02 and new storefront invariants.
- Standalone Kotlin compiler + runtime assertions check cache expiry/future/invalid epoch policy.
- JUnit `StorefrontCachePolicyTest` executes when Android CI runs `:app:testDebugUnitTest`.
- Cumulative ZIP: archive integrity, safe root-relative extraction and byte-for-byte check over pristine source.

## Production readiness status

**Not certified.** A local Android SDK/Gradle wrapper is unavailable in this tool environment, and no real APK/AAB was built; GitHub Actions, emulator/physical Galaxy UI captures, network/quote scenarios and booking checkout regression remain mandatory. This stage addresses important gaps but **does not establish 50% whole-app parity** by a validated test matrix. No new languages were added.
