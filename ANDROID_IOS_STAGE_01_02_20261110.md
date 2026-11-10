# iumrah Android — cumulative stages 01 + 02, 2026-11-10

## Inputs and scope

Reference: `kepler-ios-main 7.zip` (read-only). Android baseline: `kepler-android-main.zip`. The archive `iumrah-android-update-20261110-cumulative-stage01-stage02.zip` contains this stage plus all stage 00 / intelligence files previously supplied in `iumrah-android-update-20261110-parity-intelligence-v1.zip`. This is an **overlay patch**, not a full 79-MB Android repository and not a replacement of `.github/workflows`.

## Stage 01 — design system and common chrome

- Reused the native Jetpack Compose implementation, Cupertino semantic icons and Lato Regular/Bold typography. No SDK-specific SF Symbols copying.
- Central `IumrahDesign`: 18dp page gutter, 28dp card, 34dp hero, 19dp compact control, 56dp standard CTA, 46dp icon size. Capsule CTAs use radius 28 at 56 height.
- Mirrored the **actual supplied iOS revision**, which uses black #090909 root headers, white center wordmark, monochrome animated world map, 48dp menu/notification controls, 187.2 x 57.6 wordmark, and 171.6dp main masthead height. On Home bottom corners are 30dp; other root tabs remain square. Royal Violet #6636D9 is retained as a *token* (not painted over the black iOS masthead).
- Preserved old shared `IumrahRootPageHeader` call sites across the five root tabs; unchanged white/light branch and existing bottom navigation. All changes confined to Android application files.

## Stage 02 — Home

- Compact collapsed search surface with animated monochrome border, 5-second multilingual headline rotation (respects global reduced motion setting), real user trip summary and expand/collapse.
- **Inline, real selectors** for departure airport (existing curated airport list/geolocation), Makkah vs Makkah+Madinah route, Jeddah/Madinah first arrival, return-trip dates, travelers and cabin class.
- Published-flight-only calendar uses existing `CuratedFlightRecommendationService`. It prevents applying a nonpublished round-trip pair. Selecting dates does **not** auto-select/book a ticket.
- Travelers sheet enforces 1–9 people, infants at most adults, real cabin class; changes write to `JourneyStore`, not duplicated local draft data.
- Valid flight search commits shared TripDraft and enters the **existing real Android Flights route**. No fabricated prices, availability or payments.
- The 40dp masthead overlap now reduces actual layout height, not just visually offsets a card; this prevents a phantom 40dp gap under Home search.
- `IumrahPageHelpCard("home.default")` is collapsed by default and expands to reviewed multilingual copy. Notification carousel and marketing sections preserve the iOS display order. Previous stage's nine-service carousel and expandable services page are retained, as are video, story, audience, packages, integrations, products, FAQ and footer.
- Pull-to-refresh calls booking and notification stores and invalidates ready-package carousel state. Privacy/Intelligence telemetry from the previous cumulative update remains in the patch.

## Acceptance run inside this session

- `python3 scripts/verify-ios-stage01-stage02.py --ios <iOS repository root>` — **PASS** (design tokens, real navigation/store dependencies, localizations, assets, published-calendar safeguards, side-effect-free Swift source checks).
- `python3 scripts/test-20261110-ios-parity.py` — **PASS**.
- `python3 scripts/verify-block3-home-parity.py` — **PASS**.
- `python3 scripts/verify-signature-brand-parity.py` — **PASS**.
- `bash scripts/verify-stage12.sh` (includes previous stage gates) — **PASS**.
- Asset hash and cumulative ZIP integrity tested — **PASS**.

**Verification boundary:** This environment has no Android SDK, no Gradle executable/wrapper or device emulator. Therefore an APK build, pixel-level screenshot parity, actual networking and on-device interaction tests **were not executed**. Source parity is implemented, but calling this a production-certified 1:1 clone before CI and device acceptance would be inaccurate. The Flight search handoff uses Android's existing flight results route rather than the specific iOS guided-flight-selection screen; that downstream view belongs to the flight-selection stage.

## Installation, no extra workflow

1. Copy `iumrah-android-update-20261110-cumulative-stage01-stage02.zip` to the **root** of the Android GitHub repository (`kepler-android-main`); do not unpack locally if the existing GitHub workflow does that.
2. Commit and push. Existing `.github/workflows/android-ci.yml` matches `iumrah-android-update-*.zip` and safely extracts paths relative to repo root. Do not upload the old and new archives at the same time; the new one already contains the previous stage.
3. Run the existing `iumrah Android CI` with `run_release_build=false` to compile the application; inspect build logs and address failures before QA. No CI, secrets or backend configuration changes are required by this patch.
4. Manual QA: iPhone 11/13 mini source layout vs narrow Android 320/360/390dp; expand/collapse search; choose departure airport; choose route and first arrival; choose only published dates; edit adult/child/infant counts and cabin; enter flight results; verify refresh and help accordion; test RU/EN/UZ/UZ-Cyrillic, dark appearance, Android system font scaling, TalkBack and back navigation.

## Remaining work outside these stages

Onboarding (stage 03), full flight-selection screen and aviation contracts (04), Hotels (05), package configurator (06), booking flows (07), account/security (08), supplementary modules (09), and finally screen-by-screen device parity, integration tests and release QA (10). Existing code for those areas is retained but **not certified** by this stage's tests.
