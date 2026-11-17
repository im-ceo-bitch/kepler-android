# Android — Seamless Booking V1 + V2 + V3 cumulative (2026-11-17)

Apply as an overlay at the repository root. All V1 entries are included. Existing workflows, signing and the backend are not modified. The iOS reference is the supplied `kepler-ios-main 4.zip` (truncated archive, 1272 CRC-verified entries recovered). The Android base is `kepler-android-main 2.zip` plus V1.

## V2 — Smart and hotel selection
- Child age must be explicitly selected; incomplete draft resumes at age selection. Compact Smart header and Orbit, progress and category controls updated against the recovered SwiftUI.
- Flight, hotel/room, and transfer-vehicle edits use an independent draft. Changes are committed only after a complete server-approved quote. Flight date changes refresh affected hotel rates, retain existing rooms for unchanged dates, or select a replacement in the same category when prior rooms cannot be matched. Hotel selection preserves the other city. Cancel or quote failure retains the prior package.
- Both Nuitee catalog and official room-selection flow request pages of 12, with filter-scoped pagination, compatibility for legacy full lists, deduplication, cancellation and retry. Catalogue-only fallback strips unavailable prices. The older unused storefront branch is retained.

## V3 — guest Home, Account, Business offers
- Home marketing sequence follows the recovered iOS order; recent booking cards use local booking proof or the current account's authorized trip. Guest profile provides direct My bookings and passwordless access. Wallet selection is scoped to the authenticated pilgrim. Automatic tips are persistently configurable and applied to the Home help card; other screen help contexts have not been ported.
- Native Business offers from the documented `/api/bookings/offers/{token}` and `/accept` contract (64-character hex bearer link): server price, revision, verified SMS/email, stable request secret during screen lifecycle, receipt/package/proof validation, guest booking adoption without a second create call. Browser/app-link association requires live domain verification; custom scheme is present. Acceptance does not imply payment or availability confirmation. Offer identity and notifications still depend on backend status/registration.
- Prior V1 guest booking, Care, FCM, passport photos and passwordless account changes retained. Existing deployed FCM/APNs configuration unchanged. The last supplied backend source still lacks the new `booking-session` exchange; automatic guest profile activation is not proven. Guest proof remains authoritative.

## Verification performed
- Kotlin compiler PSI: 230 files, 0 syntax errors; not a Gradle/Compose type check.
- Smart service/JourneyStore/domain test suite: 96 JVM assertions, mocked IO boundaries, including paging, edit isolation and server-quote requirement.
- BookingStore fake transport/vault suite passed, including native offer receipt checks, guest proof and idempotent local adoption.
- Stage 1–25 source gate, including V1 and new V2/V3 structural contracts, passed. Old Home/guest-ID static guards were updated to assert the current iOS order and guest flow rather than historical UI.
- No Android SDK/Gradle/device or iOS rendering available; APK/AAB compilation, live OTP, offer API, notification delivery, domain associations, and exact device visual parity are not certified. No server changes or new secrets in this package. Run existing APK test build first, then real device smoke tests before release.
