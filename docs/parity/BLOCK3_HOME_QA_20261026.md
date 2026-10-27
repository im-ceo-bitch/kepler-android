# iumrah Android · Block 3 / Home · 26 October 2026

## Baseline & scope

- iOS source: `kepler-ios-main 3.zip`, read-only; separate headers/world-dots patch is an iOS design reference only and was **not** applied to the user’s iOS repository.
- Android baseline: the installed Block 1 release `iumrah-android-update-20261026-v5.zip`. Block 2 (`v6`) has **not** been installed. The new combined update includes **only the delta from v5 to Block 2 + Block 3**, without repeating previously installed content.
- Android implementation: Jetpack Compose with approved Lato Regular/Bold. Apple SF Symbols remain on iOS, official licensed Cupertino font on Android. Neither font design nor Liquid Glass rasterization is claimed pixel-identical.

## Home source-level reconciliation

| Feature | iOS reference | Android result |
|---|---|---|
| Section order | Header → signal notifications → pilgrim tools → emotional journey → video → audience → configurator → services → flight packages → hotels → integrations → products → confidence → philosophy → connected trip → FAQ → about | Aligned; missing pilgrim tools inserted after notifications |
| Pilgrim tools | Three controls; 98pt height / 22pt radius / 9pt row spacing / 28pt icon viewport / 10pt icon-text spacing | Three original Cupertino icons, same nominal dimensions and localized labels |
| Prayer Times | Navigate to dedicated prayer screen | Existing `PrayerTimesScreen` |
| Weather | Navigate to weather full screen | **Existing live weather provider reused**, `HomeWeatherScreen` opens on weather page of `JourneyTravelInfoPanel`. This is not a guaranteed layout match with the standalone iOS forecast view. |
| Umrah Flow | Language chooser followed by `UmrahFlowRootView` | Existing `UmrahAdvisorScreen` ritual guide (language selection available in the guide). **Intro flow and precise navigation not confirmed 1:1.** |
| Flight carousel | 318 × 398pt | Corrected 318 × 398dp on both flight cards; earlier 372dp was an out-of-date Android expectation |
| Integration carousel | 448pt height; radius 34; media 210pt, fit; CTA 48pt; disabled third CTA still visible | Corrected from 366dp and radius 28; third CTA uses disabled Clock Cupertino icon |
| Notification open | Marks message read and sends authenticated server event | Existing `ClientNotificationStore.markOpened` called with account session token, then destination navigation maintained |
| Carousel scrolling | iOS `.viewAligned` | Android `rememberSnapFlingBehavior` on seven home strips; final pixels/physics need comparison |
| Generated-package semantic accent | iOS `IumrahIconRole.umrah` / systemTeal | Android shared `IumrahIconRole.UMRAH.color` instead of hard-coded cyan |

## Verification performed

- `bash scripts/verify-stage25.sh` passed, including existing block 1 & 2 design/system verifiers.
- `python3 scripts/verify-block3-home-parity.py --ios <read-only-original-ios-root>` passed against the actual original SwiftUI sources.
- All changes are made in Android. No server API implementations or booking/pricing rules were modified; an **existing** notification read API is now called from Home as on iOS.
- Combined ZIP will be checked by CRC integrity plus reconstruct-and-compare from the original v5 Android tree. It uses paths relative to repository root for the existing GitHub Actions ZIP importer.

## Known gaps / acceptance not complete

1. **Build not executed**: this Linux work environment does not include Android SDK / Gradle. Source checks cannot prove Kotlin/Compose compilation, and GitHub Actions workflow_dispatch must run before release.
2. **No screenshot comparison yet**: no iOS/Android captures on matched devices, font scale, safe areas or dark/light mode. Therefore 100% pixel parity is **not claimed**.
3. Weather and Umrah Flow currently reuse existing Android features; these are functional destinations, but screen-by-screen 1:1 UI and onboarding parity remain to be certified.
4. Eight iOS languages versus four Android languages, as identified in earlier audits, is an outstanding global feature gap.
5. Different font rendering and original SF Symbol glyph outlines remain accepted/known platform differences subject to the user's agreed exceptions and available Cupertino font glyph coverage.
6. All other Home sections still require device-level visual capture and interaction tests. A passing source-level contract is not final sign-off.

## GitHub installation

Only after deciding to update: upload the single `iumrah-android-update-20261026-v7.zip` into Android repo root (`main`) **without unpacking**. Existing GitHub Actions `apply-update` workflow will unpack it and commit changed files. Do **not** upload v6 separately or reapply v5. Then manually dispatch `iumrah Android CI` for actual unit tests and APK build.
