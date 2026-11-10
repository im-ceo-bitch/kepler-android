# iumrah Android — cumulative stages 05 & 06 (2026-11-10)

## Reference and scope

Reference: current attached `kepler-ios-main 7.zip`. Android-only update on top of cumulative stages 01–04. The historical Hotel First, package quote and booking paths remain in source; no backend, domain or GitHub workflow is changed. This is **source-level parity implementation**, not a certified pixel-perfect/production release.

## Stage 05 — Hotels / Nuitee

- Added typed DTOs and source-matched Nuitee service: `GET /api/package/nuitee/hotels`, `GET /api/package/nuitee/hotel`, `POST /api/package/nuitee/rates`.
- Replaced **Hotels board only** with a Nuitee catalog. Flights and Sunday boards and their existing routes are retained.
- Catalog is visible by default; expanded search can change city, dates, stay nights and adults. Star and name filters, favorites, supplier preview image, hotel detail/amenities/policies, live room offers and terms.
- Preserves distinction between **room-stay supplier rates** and **full package totals**. Cached/stale rates cannot be selected as verified offers; a hotel without a fresh rate can only be marked "price pending".
- Stores independent city selections persistently, and deletes incompatible pending/saved variants when switching supplier option.

## Stage 06 — Flight-first journey configurator

- The stage-04 guided flight flow now opens `AppRoute.NuiteeJourneyHotels` and then `AppRoute.NuiteeJourneyReview`.
- Draft retains two selected flights and room keys per **city and room index**; supports Makkah/Madinah, number of rooms, adults and ages of children. Stay dates are calculated using the existing `TripStayPlanner`; Saudi arrival is converted from the first flight's arrival time.
- Real Nuitee room offers are selectable. Missing offers are explicitly marked pending, not silently priced. Changing room count or child ages removes stale room choices. Room rates can be revalidated; failed/stale offers are removed.
- Review displays chosen flights, room terms, transfer class, optional Haramain and an **indicative** itemized estimate only when tariff assumptions are verifiable. Child/infant fares and missing airline prices are not guessed. Pending hotel requests are excluded from the indicative sum.
- Review submits a contact request to the **existing** `POST /api/package/care-requests` with `configuration` v2 (trip, flights, rooms, pendingHotels, transfer, train, estimate). It does **not** create a paid booking or claim supplier availability.
- Existing legacy itinerary + server-authoritative quote route remains present for current bookings and journeys.

## Verification and known gaps

- `bash scripts/verify-stage12.sh`: PASS, existing inherited source contracts.
- `python3 scripts/verify-stage03-stage04.py`: PASS.
- `python3 scripts/verify-stage05-stage06.py`: PASS, 21 source/contract guards matched against the SwiftUI routes and integrated Android files.
- Actual `NuiteeModels`, `NuiteeRules` and `NuiteeJourneyPricing` source compiled with `kotlinc` using local model stubs and passed behavior checks for stale/unknown prices, adults/children, room completion, pending and transfer upgrades. Unit test source: `app/src/test/java/com/iumrah/beta/domain/nuitee/NuiteeJourneyParityTest.kt` (needs Gradle to run in Android module).
- **Not run**: `:app:assembleDebug`, Gradle/JUnit module tests, connected real/sandbox Nuitee requests, test submission to backend, simulator/device UI comparisons. Gradle and Android SDK are unavailable here. Backend acceptance of `configuration` must be verified in staging before relying on requests.
- Full iOS UX and visual 1:1 parity, and actual confirmed hotel booking, have **not** been certified. Supplied Android price is explicitly preliminary and subject to server verification.

## Apply

Upload only `iumrah-android-update-20261110-cumulative-stage01-stage06.zip` to the root of your Android GitHub repository and commit/push. It includes stages 01–06; do not reapply stages 01–04 separately. The existing ZIP extractor and `.github/workflows/android-ci.yml` are preserved. Run existing `iumrah Android CI` with `run_release_build=false`; if Gradle fails, obtain the compile logs before proceeding to stages 07–08. Test real Nuitee dates, room offers, stage transitions, form submission, screenshots and localization on hardware.
