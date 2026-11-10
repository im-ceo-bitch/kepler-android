# iumrah Android — updated iOS parity package, November 10, 2026

This document describes the **cumulative** `iumrah-android-update-20261110-cumulative-stage01-stage02.zip`. Detailed stage acceptance: [`ANDROID_IOS_STAGE_01_02_20261110.md`](ANDROID_IOS_STAGE_01_02_20261110.md).

## Previously supplied features retained in the cumulative patch

- Shared production Intelligence HTTP endpoint `/api/catalog/hotels/intelligence/events` at `https://iumrah-backend.helloiumrah.workers.dev`; Android session and visitor identifiers; bounded encrypted/private app preferences are not assumed; client-controlled collection and offline queue. Data contracts and private-data exclusion are guarded by `scripts/test-20261110-ios-parity.py`.
- Analytics event wiring for app navigation, onboarding, flights/hotels, booking requests and account preferences. A booking request is not claimed as a completed payment or sale.
- Services with stable iOS identifiers `visa`, `flights`, `hotels`, `transfer`, `guide`, `ziyarat`, `haramain`, `support`, `digital`; deep links, images and expanded service page.
- Existing Cloudflare booking/package API routing remains unchanged (`https://iumrah.app` for original endpoints). Never replace the global base URL solely to make the analytics endpoint work.

## Added/completed in stages 01–02

- Shared Compose design tokens and signature header geometry from the actual 2026-11-10 iOS source; menu left, centered wordmark, notifications right, Home-only rounded bottom corners.
- Real editable **inline Home search**, not a non-interactive preview: airport, Makkah/Madinah scope, arrival, published dates, party size, cabin; state written to the same JourneyStore as the package flow.
- Date selection is advisory only: no fare is pre-booked or implicitly selected. Search opens the existing flight results route with the validated trip draft.
- iOS-correct collapsible guidance card, 40dp overlap without empty space, live Home refresh, existing carousels preserved.

## Install

Upload only `iumrah-android-update-20261110-cumulative-stage01-stage02.zip` into the Android repository root and push it. The existing Android CI unpacker automatically consumes filenames `iumrah-android-update-*.zip`. No new workflows. Then run the usual Android CI; review APK build before on-device validation.

## Limitations

All shipped checks are offline **source/asset contracts**; this session could not run Android Gradle or emulator because the Android SDK and Gradle were not installed. The next rounds cover onboarding, complete flight/hotel workflows and screenshot parity; production 1:1 is not yet certified. Backend live acceptance and privacy-policy Google Play disclosures are separate obligations.
