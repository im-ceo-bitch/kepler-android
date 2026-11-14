# Android iOS sync — unified backend V2 (2026-11-14)

Source of truth: supplied `kepler-ios-main 3.zip`; Android baseline: supplied `kepler-android-main.zip` + `iumrah-android-update-20261113-cumulative-unified-backend-v1.zip`. This is a cumulative Android-only patch. No GitHub workflows or server deployments are included.

## Changes
- Match the new iOS AppConfig Worker origin. Legacy first-party API URLs are normalized to it; public share URLs remain on iumrah.app. No automatic HTTP redirects for authenticated API requests.
- Copy the exact iOS Umrah Flow offline JSON. All 180 audio paths are first-party Advisor paths; no direct Supabase host remains in Android Kotlin or this catalog. Fetch translation and audio catalogs from `/api/services/advisor/{translations,audio}?lang=...`; retain reviewed bundled fallback. Server media availability and R2 object migration are not verified by a client patch.
- Chat: in-memory bounded booking cache, scoped by hashed credentials; delta `after` and repeated `unread` query parameters, merge updated messages by ID, stable client message ID for retry, separate booking/account proofs, push-triggered refresh and chat deep-link. Cache is not persisted across process termination; actual delta cursor semantics and push delivery require backend/device verification.
- Booking: add top-level quoteProof; reject explicitly non-bookable server quotes; persist pending proof in existing secure booking vault before report commit, retry at refresh, omit client pricing snapshots from create/sync payloads. Retain existing IDs and add server identity validity predicate (not a new ID generator).

## Validation executed locally
- 53 unified URL JVM checks passed.
- 47 stage-10 JVM domain checks passed.
- 9 chat delta JVM checks passed.
- Stage 01/02, 03/04, 05/06, 07/08, 09 and stage 10 static contract checks passed after aligning the older Home route test with stage 04.
- 88 distinct API path literals in static audit; new source contract assertions for quote proof, audio catalog and chat passed.
- APK/Gradle/Compose build, device screenshots, production API end-to-end and FCM delivery NOT run. No live backend data was changed.

## Scope remaining
Full visual parity with this newer iOS archive is not complete: updated launch/hemisphere graphics, flight detail operational enrichment and presentation, manual journey estimator, Nuitee cache/detail changes, account and other page refinements still need dedicated ports. Do not describe this patch as complete 1:1 parity. A current post-patch Android snapshot is needed if independent changes were made after the supplied baseline, to avoid overwriting those changes. Backend source/deployment and historical database migration are not part of this Android-only patch.
