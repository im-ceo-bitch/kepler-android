# iumrah Android / iOS parity Stage 08 — cumulative release-candidate patch

Reference: uploaded `kepler-ios-main 5.zip` vs recovered `kepler-android-recovered.zip`. This ZIP **replaces Stage 01–07** and applies directly to the ORIGINAL Android repository root.

## Changes in Stage 08

1. Android App Widgets: five Android-native launchers corresponding to iOS active trip, booking status, planned Umrah, identity and countdown widgets. Android RemoteViews are used (not an iOS WidgetKit binary). They read offline display summaries only, never access tokens or quote proofs. Widgets deep-link to an actual booking, booking tab, plan screen or account. Booking/account snapshot refreshes from the central Compose state and the saved plan triggers refresh when edited/deleted. Same existing 4 locale choices in widgets, no locale expansion.
2. Advisor: replaced the plain pulse with a Canvas-drawn indigo/cyan/violet/orange layered orb following the iOS Siri Orb palette and 9.05-second movement, with playing/buffering/idle visual states. This is **playback-state reactive, not real audio waveform amplitude**; iOS still has finer metering-dependent animation. Device screenshot comparison remains required.
3. Configurator: quote input identity and request-generation checks prevent an older asynchronous quote from overwriting updated airports, flight selection, meal selection, hotel rooms or transfer configuration. Clearing a selected flight clears dependent stale itinerary, quote and transition state. Server quote proof remains mandatory for final checkout.
4. Booking integration regression tests: synthetic 2-pilgrim TAS→JED return route, official server-quote gate, booking draft totals and provider identity; clearing published flight drops previous quote. These tests require running Gradle/JUnit in the Android build environment; only source checks have run here.
5. Stage 08 structural gate: XML resource and widget declaration parsing, no booking tokens in widgets, quote request guards and existing Stage 01–07 gates.

## Explicit release blockers — not certified in this environment

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintRelease` with a proper Android SDK and Gradle installation has **not run** here; source-only `kotlinc` produces unresolved Android/Compose dependencies and is not a substitute for Android Gradle compilation.
- No Android emulator or real device with 375px/414px equivalent screenshots is available here. **Pixel-perfect correspondence is unverified** for Home, Flights, Hotels, Trip, Booking, Advisor, Account, Care, Ziyarats and edge cases.
- No actual authenticated end-to-end booking, live supplier reprice, KYC/manual payment or operational status updates have been performed: these require controlled backend fixtures, device QA and staging identities.
- The iOS WidgetExtension uses custom SwiftUI widgets and audio-metering Siri animations. Android platform widgets/animation approximate the visual identity, but are not exact pixel copies.
- Tests have not exercised date/calendar boundaries, RTL rendering, Android accessibility and all supported densities.

## Minimum release gate before Play publication

1. Apply this ZIP once to the original repo. Do not apply Stage 01–07 first.
2. Run `bash scripts/verify-ios-parity-stage08.sh` and `bash scripts/verify-stage25.sh`.
3. Run Android CI with `:app:testDebugUnitTest :app:assembleDebug :app:lintRelease`; investigate every compilation/test/lint error. Validate release signing, AAB, Play 16 KB native alignment.
4. On Samsung Galaxy test real widgets (add, resize, remove, reopen), widget deep links, logout/privacy, background update, phone timezone change.
5. Compare iOS reference and Android screenshots on the same seed data and font scale: Home; flights search/calendar/details; hotels and packages; configuration; checkout/passports; booking statuses; Advisor stages; account; prayer times; maps. Record differences as bugs, retest fixed builds.
6. Use controlled staging bookings for all 5 statuses plus cancel, expired timers, partial data, no network and invalid quote. Verify prices remain server authoritative and no unexpected purchases occur.

## ZIP content

Root-relative changed/new files only; includes all 7 previous cumulative stages. The patch does not touch `.github/`, Gradle configuration, signing, payment provider declarations or translation expansion.

**Status:** pre-build candidate; do not label as 100% iOS-parity or production-ready until the remaining build/device/live-flow gates pass.
