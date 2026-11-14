# Android / iOS architecture parity V4 — 2026-11-14

Baseline: kepler-android-main.zip + cumulative Android unified-backend V3. Reference: kepler-ios-main 3.zip.

## Implemented in this patch
- Swift `Sources/Services/NuiteeDailyHotelCache.swift` and `NuiteeHotelService.swift`: application-wide four-entry access-ordered memory cache, atomic disk persistence in cache directory, local-calendar day + strictly less than 24 hours, independent city/date/adult/catalog-only keys, no failed rate responses cached. Supplier offer IDs removed from cached catalogue as an additional Android security precaution; room selection still goes through `/rates`. Existing upstream stale flags preserved. Disk errors do not block search.
- Swift `NuiteeHotelsCatalogView.swift`: read completed cached listing before querying catalogue; separate draft stay dates/nights/adults from committed search; re-read when returning on a new day; explicit retry bypasses cache. Journey hotel selection uses the same cache. Room rates are still fetched separately and revalidated; no local final quote cache.
- Swift `IumrahFlightDetailService.swift` and `IumrahPremiumFlightLeg.swift`: operational detail models, unified API `/api/package/flights/detail`, 15 second timeout, validated flight identity and local departure date/time, five-minute successful-response memory cache, in-flight request coalescing, manual refresh, no timer polling. Integrated into the existing Android fare detail leg UI; only direct or verified segments queried. Real response fields shown when available; no fabricated flight data.
- Removed blue accent from trip type label on the updated fare detail screen.

## Verification
- 39 JVM checks of cache policy, LRU, flight identity, request coalescing and refresh; mock API transport (no production requests).
- Existing Stage 10 repository verifier passed (176 Kotlin files, 54 routes, 21 XML documents, 11 groups); existing stage 05/06 verifier: 18 passed; unified API audit: 89 path literals.
- No workflow, D1 schema, backend or user data changes.

## Remaining / acceptance
- This is an incremental architecture patch, not complete pixel parity. Remaining: full premium flight detail layout and globe, launch animation, latest local/manual journey builder and booking handoff, hotel detail reviews and meal choices, remaining screen-by-screen layout and state comparison (including account, chat and onboarding), full audit of all cache policies and business logic against the current iOS source.
- Android SDK/Gradle compilation, Android disk cache instrumentation and live API E2E not run. Verify `:app:testDebugUnitTest` and `:app:assembleDebug` in existing CI. Compare matching reference screenshots on iOS and Android with same logical viewport, language, font scale and state; system UI and platform font rasterization may differ. Do not declare 1:1 until verified.
