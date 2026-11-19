# Commerce parity — cumulative Android update, 2026-11-19

Base: kepler-android-main.zip; reference: kepler-ios-main.zip. Overlay at repository root. No workflow, Firebase, signing or Gradle configuration changes.

## Implementation
- Trip Summary 2.0 signed server price, cent reconciliation, fees, occupancy validation, 180-second quote; explicit acceptance of changed totals in review, save, checkout and booking creation.
- Smart room allocation and supplier-confirmed fallback; complete static catalog, independent batched rates, child ages and fail-closed network behavior.
- Mixed curated/Aviasales flights and paired round-trip compatibility; common hotel entry and server quote path.
- Separate manual builder with partial server estimate, full shared quote before booking; flight, hotel, transfer and train selections.
- Saved Package create/update/read, encrypted owner capability, revision, saved draft restoration, public sharing, thread, room approval request, and booking association with retry of pending links. No cached quote proof used for restoration. Secure storage failure does not overwrite an unreadable package index.
- Business offer adoption merges returned trip, preserves existing credentials and avoids replacing newer booking state.
- Reviews cache shared across hotel screens: 30 minutes, duplicate-request coalescing, stale fallback and page ID deduplication; canonical photo URL deduplication.

## Validation
- Smart/Journey/Manual JVM harness: 119 assertions passed, using simulated service/serialization boundaries.
- Flight and guided route JVM harness: 73 assertions passed.
- Kotlin PSI syntax: 237 files, zero errors. This is NOT Android type checking.
- Seamless Booking V1 and V2/V3 static source contract scripts: passed.
- No live server calls, Android Gradle/Compose compilation, APK/AAB or device testing: Android SDK/Gradle and current backend source not available here.

## Remaining parity work / limitations
- Exact atomic iOS TripSummaryEditSession (draft flight/hotel replacement, cancel/apply without altering original until commit); current shared review changes are not full isolated edit-session parity.
- Manual builder/storefront cross-entry and final flight engine edge-case parity require further end-to-end verification; photo deduplication canonicalizes URLs, not actual pixel content.
- Saved-package serialization and live server contracts need integration verification; client implementation alone is not proof of server acceptance.
- Block 2 (Booking Experience 3.0 and travel lifecycle) not implemented by this update.

This archive is a cumulative implementation update, not a certified 1:1 release. Use a test branch and run existing Android CI before production release.
