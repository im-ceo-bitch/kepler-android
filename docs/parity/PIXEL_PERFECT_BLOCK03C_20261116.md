# Pixel Perfect 03C — package review

Reference: `kepler-ios-main 6.zip` (SwiftUI). Base: `kepler-android-main 2.zip` + cumulative 03AB.

## Implemented
- Shared review header, overlapping/collapsible trip summary, help, price state, service and trust rows, fixed total/per-person footer.
- Manual and Smart review screens follow the current iOS content order: summary, assembly animation, flights, hotels, services, checkout. The historical final-page tier carousel is no longer the manual review screen.
- Room and meal edits invalidate the accepted quote. Transfer changes return to the existing final screen. Booking checks current state and current server quote. Round-trip display avoids double-counting a shared fare.
- Shared hotel and flight cards and package information sheets. Existing native Android checkout routes remain in place.

## Validation
- Kotlin PSI parse: 223 files, zero syntax errors. Not Android/Compose type checking.
- Smart/JourneyStore JVM harness: 75 assertions passed using mocked network, including stale transfer responses, room/meal edits and fare display.
- `scripts/verify-pixel-perfect-block03c.py`: review source guards passed.
- Android SDK/Gradle compilation, physical-device screenshots, live supplier checkout and frame-by-frame motion verification were not performed in this environment. The package server quote refresh is not an independent live supplier room-rate verification. Native iOS glass and Android effects are not yet certified equivalent.

## Planned remaining blocks
03D remaining discovery/manual configurator and detail sheets; 04A booking/checkout; 04B account/support/chat; 04C auxiliary services, onboarding and localization; 05 gestures, motion, design tokens and performance; 06 build, device comparisons and regression correction. These are planned work packages, not a guarantee of the number of corrective iterations. No backend or GitHub workflow modifications.
