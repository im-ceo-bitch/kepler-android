# iumrah Android — source-level parity delta to iOS 6

**Reference inputs:** `kepler-ios-main 6.zip`; preceding `kepler-ios-main.zip`;
Android base `kepler-android-main.zip` plus cumulative Android update `iumrah-android-update-20261120-ios6-cumulative.zip`.

**Scope:** Android client only. This is one cumulative update; apply it to the root of the
Android repository instead of applying earlier Android updates again. It does not deploy
Cloudflare D1 or push providers, and does not modify signing credentials.

## Client changes contained in this update

- **Trip Summary Design 13:** shared compact 16 dp inset; 24 dp photo-based Kaaba hero
  using the existing `about_iumrah_kaaba_corner` asset, 168 dp picture and dark gradient,
  itinerary dates/routes, total days and Makkah/Madinah nights, quoted total and
  quoted per-pilgrim amount. Used by both classic and Nuitee final-review routes.
- **Financial lines:** ported iOS `TripReviewPriceGrouping` exactly by component code:
  `flight_*`, `meal_*` and `meals`, `ziyarat_*`, `transfers` and
  `vip_transfer_upgrade`. Source BigDecimal components and their order are unchanged.
  Server quote, support 9%, payment 2%, rounding and signed-booking gate remain the
  financial authority. Price chart is a separate **optional** presentation card.
- **Brand Experience 15:** existing Home carousel/onboarding, shared world-map
  slogan on Care/Profile and contextual non-derogatory group/private comparison
  entry on final reviews, saved packages and the Flights proposals storefront.
- **Booking & Notifications 14:** iOS-style booking-created **local receipt** for
  recently created verified sessions, separately merged with remote notifications.
  Read receipts persist locally. They do not trigger a spurious remote `/read` call,
  do not expose tokens, and routing checks the booking's access proof.
- **Account deletion parity:** Android now invokes the iOS 6 `DELETE
  /api/catalog/hotels/client/account/profile` contract, displays an explicit
  destructive confirmation and backend rejection, and clears local session only
  if the deletion is confirmed. The server enforces financial/security/trip rules.
- **Continuation:** retains all previously delivered architecture, catalog, Smart,
  flight, saved-package, booking, cancellation/refund, itinerary, Care, and account
  changes from the cumulative iOS6 patch. No GitHub Actions, Firebase credentials,
  keystore, or Gradle signing modifications are introduced in this final delta.

## Verification executed in the preparation container

1. `bash scripts/verify-stage25.sh` — **exit 0**, includes all legacy structural
   source-contract checks and the new `scripts/verify-ios6-parity-final.py`.
2. Standalone `kotlinc` compilation with a stub of only the public component
   type followed by actual execution of `TripReviewPriceGrouping.kt` — seven
   explicit checks passed (first-appearance ordering; amounts, totals, fees
   unaffected, VIP + transfer, flight/meal/ziyarat grouping).
3. Android JUnit tests for financial grouping included at
   `app/src/test/java/com/iumrah/beta/domain/pricing/TripReviewPriceGroupingTest.kt`.
   Execution in full Android Gradle environment is **not** claimed here.
4. Patch-file SHA-256 validation, zip CRC verification and duplicate entry check.

## NOT verified and must not be described as 1:1 certification

- Android SDK, Gradle Android plugin, Play Release build/AAB and Lint were not
  available in this container. Kotlin isolated parser probes without AndroidX
  classpath show unresolved-reference errors: they **cannot** replace compilation.
- Production backend, real notification outbox and SMS, OTP, D1, APNs/FCM,
  account deletion eligibility, payment provider and public Universal/App Links
  have not been tested end-to-end.
- Compose vs SwiftUI visual screenshot comparison has not been performed.
  No pixel-perfect parity is asserted without device renders, fonts, dynamic type,
  accessibility and responsive checks. Reference iOS docs themselves did not
  confirm Xcode/device verification.
- This client archive cannot retroactively guarantee all iOS backend behavior;
  the backend is a separate deployable and must be verified at the same revision.

## Recommended CI gate after applying

Run the existing `apply-update` workflow, then build using the existing Android
CI configuration and run `testDebugUnitTest`, `lintRelease`, compile Debug/Release
and signed AAB build. Compare both screens with iOS on corresponding handset
sizes; verify guest booking recovery, quotation mismatch, package sharing,
notification receipt and deletion eligibility against the deployed backend.

A structural source gate passing is not an AAB build and not screenshot parity.
