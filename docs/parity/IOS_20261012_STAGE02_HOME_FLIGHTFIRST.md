# Android ⇄ iOS | Stage 02 | Home / Flight First

**Source of truth:** `kepler-ios-main 5.zip` → `Sources/Views/Home/HomeDashboardView.swift`, `Sources/Views/Home/HomeAppleStoreSections.swift` (2026-10-12).

## Fixed in Android

- Home **Ready-made packages** carousel: package cards and "all packages" CTA use the same 318 × 372 dp slot; consistent media height (118 dp), padding (16 dp), corner radius (28 dp); bottom stamp alignment stable across 1- and 2-leg products.
- Package card includes the fields displayed by the reference `HomeStorefrontFlightOptionCard`: actual leg day/time, airport codes, carrier flight numbers, airline logos on the photo, package type, individual price, duration and the generated-by-Configurator stamp.
- Leg day and clock use ISO-8601 timestamps with a verified timezone offset, converted to device-local time, consistent with iOS `DateFormatter`. Unparseable times explicitly show `—`, not a made-up hour; dates without an offset retain the explicit ISO calendar date, not a fake clock time.
- `LazyRow` uses the **flight option ID** as the item key, not the shared package snapshot ID. On the same return flight shared by two package cards, the previous key could collide and crash the Compose lazy list.
- Retains the existing server package snapshots and price logic; no changed supplier selection, money computation or payment routes.
- The hero photo resource was byte-compared with iOS `IumrahFlightsShowcaseHero` and found identical.
- Existing RU, EN, UZ (Latin), UZ Cyrillic remain untouched. No newly enabled locales.

## Local verification

`bash scripts/verify-ios-parity-stage02.sh` (includes stage01), plus isolated `kotlinc` compile and runtime checks of the timezone formatter. The JUnit test is under `app/src/test/.../ui/home/` and will run through `:app:testDebugUnitTest`.

## Not certified / remaining work

**Stage02 is not complete app parity, not a Play production build.** Android SDK/Gradle is not installed in the authoring environment. Run the existing `iumrah Android CI` workflow (`workflow_dispatch`) for `:app:testDebugUnitTest` + `:app:assembleDebug`, inspect build logs and test iPhone vs Galaxy screenshots for the carousel. Hotel First home card differs significantly from iOS, and other feature areas remain unverified.

## Application of the patch

The Stage02 ZIP is cumulative: it includes Stage01 and Stage02 root-relative files. Drop the ZIP into the root of the Android GitHub repository. GitHub Action `apply-update` will unpack and commit it; then manually trigger the build workflow. Do not overwrite `.github/`, secrets, signing files or locales.
