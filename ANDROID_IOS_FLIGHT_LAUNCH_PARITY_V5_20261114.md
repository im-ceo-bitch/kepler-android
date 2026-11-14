# iumrah Android — V5 Flight detail / signature launch parity

Source of truth: `kepler-ios-main 3.zip` (uploaded 14 November 2026). Android base: `kepler-android-main.zip` + cumulative V4. This update is Android-only; no server, D1, signing, or `.github/workflows` changes.

## Changes

- `Sources/App/IumrahLaunchGeometry.swift`, `IumrahSignatureLaunchLayer.swift`, `IumrahLaunchExperience.swift`: ported the 1.2-second one-shot cold-launch sequence, 840ms root mounting, easing, geographic dot cloud, TAS–JED illustrative path, monochrome logo handoff, reduced-motion fallback, bypass for routed deep links and opened push, and cancellation on background. Android's system splash and haptic API remain platform-specific. No network dependencies in animation. The native system splash may precede the custom launch.
- `Sources/Core/Components/IumrahHemisphereProjection.swift`, `IumrahHemisphereMap.swift`, `IumrahHemisphereLandData.swift`: ported orthographic projection, horizon-clipped triangle mesh, staggered 112-row dot lattice, 12-entry projection cache, route midpoint camera, 142dp cropped hero and real position overlay. Binary assets are decoded directly from the original Swift base64 data; Natural Earth source is public domain. Launch data contains 5,195 samples; land mesh contains 16,905 triangles; airport coordinates contain 54 iOS entries. Unknown airports are resolved with the existing unified API service. The SwiftUI premium detail hemisphere is a decorative, noninteractive map; this port does not add arbitrary map gestures.
- `Sources/Views/Flights/IumrahPremiumFlightDetail.swift`: shared Compose flight detail used in search, guided selection, legacy generator, expanded booked-flight and storefront cards. Monochrome hero, 29/38/30sp number / IATA / time, source-confirmed operational details, airports, aircraft, cabin, terminal/gate, baggage belt, revised and actual times, provider status, explicitly estimated arrival only when supported. Discovery and guided flight details have a fixed airline header and share/help actions; favorite remains available in discovery. Uses actual `arrivalAt` when present. Existing price refresh, exact itinerary verification, selection and booking flows are preserved.
- V4 flight operational service remains the single 5-minute cache and in-flight request deduplication mechanism. No timer-based provider polling. Local UI checks freshness each minute and stops drawing aircraft telemetry after 15 minutes; invalid/future/stale coordinates are not displayed. Force-refresh remains explicit and disabled on quota exhaustion.
- Updated old source guard from bottom sheet to the actual dedicated full-screen guided flight destination, not a skipped check. Added offline Kotlin regression and Gradle JUnit geometry tests.

## Validation

- Kotlin PSI syntax parse: 180 production Kotlin files, zero syntax errors. This does not resolve Compose types or replace Gradle compilation.
- `scripts/verify-stage10.sh`: complete existing Stage 10–25 source validation passed, including 54 navigation routes and 21 XML documents.
- `scripts/verify-ios-architecture-v4.sh`: 39 JVM checks passed (mock transport).
- `scripts/verify-ios-parity-stage10-kotlin.sh`: 47 JVM regression assertions passed (minimal external DTO stubs).
- `scripts/verify-ios-flight-launch-v5.sh`: 30 JVM geometry checks passed; route math, clipped horizon, original binary datasets, cold-launch timings, live-position freshness.
- `scripts/verify-stage03-stage04.py`, global Cupertino checks, build environment preflight: passed.
- Actual Android SDK / Gradle unavailable in this environment (no gradlew wrapper). APK/AAB, Compose typechecking, physical device layout, GPU performance, haptic equivalence and screenshot comparison remain unverified. Do not mark V4 or V5 as production complete until CI/device QA succeeds.

## CI correction (separate YAML, not in ZIP)

The prior manually installed test workflow checked `com.iumrah.beta` although `app/build.gradle.kts` declares the application ID `com.iumrah.app` (namespace is `com.iumrah.beta`). Replace `.github/workflows/iumrah-apk-test.yml` manually with `iumrah-apk-test-v5.yml` only if it still has the old package check. No application ID change. The standard repo CI and ZIP unpacker are untouched. Run manual `iumrah APK Test Build` after applying the V5 patch. Upload build logs if the compile step fails.
