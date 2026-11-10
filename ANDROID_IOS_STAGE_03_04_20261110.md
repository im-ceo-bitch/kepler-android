# iumrah Android — incremental stages 03 + 04, 10 November 2026

**Reference:** attached `kepler-ios-main 7.zip` (read only).  **Android baseline:** `kepler-android-main.zip` + cumulative stages 01/02.

## Stage 03 — onboarding

- The **actual current SwiftUI onboarding is five cinematic screens**: welcome/video, trip builder, package, Care, closing. The previously discussed eight-screen interactive trip configurator is a future *product redesign*, not part of the provided iOS source. We did not introduce Android-only product divergence.
- Full-screen adaptive Compose carousel, source-matched copy in RU / EN / UZ / UZ Cyrillic, animated first-launch, language picker, *Skip*, native Media3 sound on/off, responsive illustrated booking, package and Care cards, closing orbit, progress indicators and finishing transition.
- Marketing mock card prices and mock dates are **illustrations only**, not booking quotes or search results.

## Stage 04 — flight-first guided selection

- New Android route `AppRoute.GuidedFlights`, opened from Home journey search. Existing `AppRoute.Flights` and hotel-first journey flow remain intact.
- Combines existing **published curated Business flights** (`CuratedFlightRecommendationService`) with existing **Data API fare observations** (`AviasalesFlightDiscoveryService`). No API, backend or CI updates.
- Explicit outbound → return steps, real details bottom sheet, non-stop/on-date, nearby-date and connecting groups, refresh, route/traveler/date editor, multilingual copy.
- Selection rules: 3-day flexibility window, route/cabin/date validation, flight deduplication, preferred verified published options, no mixing of providers, strict round-trip chronology and compatible published pair. Data API economy only (other cabins have no verified fare metadata).
- Published path saves the real published IDs. Data API path stages the actual 2 source offers for a server-side fare *re-quote*. There are **no fabricated fares, offers or availability promises**. Continue opens the existing Hotel Selection stage.
- Adds `GuidedFlightSelectionEngineTest.kt` for published-pair, source mismatch, one-way API matching and invalid fare checks.

## Verification

- `bash scripts/verify-stage12.sh`: PASS (legacy contract checks).
- `python3 scripts/verify-stage03-stage04.py`: PASS.
- Pure Kotlin selection engine compiled with locally installed `kotlinc` and passed a stubbed behavior test (published pair, Data API pairing, mixed-source rejection).
- Zip integrity and path safety checked separately by the packaging script.

**Not completed here:** real `:app:assembleDebug`, `testDebugUnitTest` under Gradle and Android SDK, API integration or Android/iOS device-by-device screenshot parity. This session lacks Android SDK and Gradle. Please run the unchanged `iumrah Android CI` workflow after applying the patch; address any compile/test failures before claiming production-complete 1:1 parity. Native video playback, date picker locale, back behavior and small-screen typography still require device QA.

## Apply

1. Upload only `iumrah-android-update-20261110-cumulative-stage01-stage04.zip` to the Android GitHub repository root; it includes all stage 01/02 changes and does not need the older ZIP.
2. Commit / push. Existing `.github/workflows/android-ci.yml` extracts ZIP patches automatically and removes the uploaded archive.
3. Dispatch the **existing** `iumrah Android CI` with `run_release_build=false`. No new workflows.
