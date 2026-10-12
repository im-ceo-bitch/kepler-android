# Android ⇄ iOS parity | Stage 04 | Hotel detail stability, safe areas and cached content

Reference: iOS `Sources/Views/Hotels/HotelDetailView.swift` and `Sources/State/HotelStorefrontStore.swift` in `kepler-ios-main 5.zip`.

### Updated

- Public hotel detail descriptions, image URLs and amenities now persist to an **atomic local disk cache** keyed by SHA-256 hotel ID. Data is validated by exact ID, cache schema and seven-day time-to-live; maximum 2 MB per hotel.
- Detail/hero/room information can appear on subsequent visits before network retrieval. An intermittent detail API failure no longer wipes a valid currently displayed card.
- Any network request whose originating journey airport no longer matches the active airport is ignored. Cancellation checkpoints protect `LaunchedEffect` requests and progressive package refresh from updating a stale route.
- Room category loading preserves existing categories on transient endpoint failure rather than erasing the user's current selection UI.
- Ready Hotel First snapshots are kept across an empty server batch; active package prices are still from current server sessions. The cache **does not contain package quotes**.
- The room selection confirmation bar now reserves Android gesture navigation space, keeping its Done button reachable.
- Original package pricing contract, checkout / booking flow, languages, account storage, permissions and CI workflows untouched.

### QA

- Stage 04 script additionally verifies cached detail, cancellation, package retention and bottom inset source guards, plus Stage 01-03 tests.
- JVM test `StorefrontCachePolicyTest` has hotel detail expiration/future-date checks, and can be run by Android CI.
- Standalone Kotlin smoke tests compile and execute TTL policy.
- Cumulative ZIP root extraction checked from a pristine Android baseline.

### Release qualification

Stage 04 is an intermediate source patch, **not** a certified 50%-done parity claim or Android production build. APK/AAB compilation, screenshot comparison, catalog endpoint and checkout testing still require Android CI and devices. New localization work remains deferred.
