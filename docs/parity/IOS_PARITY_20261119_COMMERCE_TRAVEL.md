# iOS parity — commerce and travel implementation, 2026-11-19

Base: kepler-android-main.zip. Reference: kepler-ios-main.zip. The earlier COMMERCE_CHECKPOINT document is historical; this document supersedes its status. Cumulative root overlay, not a full repository. No Firebase, Gradle, signing or workflow configuration changes. Do not deploy this update as a certified production build.

## Block 1 — implementation
- Includes the previous Trip Summary 2.0, server price breakdown, quote-proof checks, Smart/Manual, mixed-flight, saved-package and unified catalog work.
- Isolated review drafts: flights, one hotel, all hotels, legacy catalog room selection and transfer. Cancel drops the draft. Apply checks the original snapshot, invalidates the previous quote, retains the accepted total and saved-package identity. Legacy catalog apply preserves flight selection. A changed flight route reopens the all-hotels editor when its room selections are incomplete.
- Hotel listing image verification: canonical identity and 16×16 RGB similarity (mean difference <=8), 15 candidates / 3 selected photos, 30-minute cache, bounded downloads. Nuitee and legacy storefront request details if insufficient distinct photos; show one cover if no verified photo is available. Native image decoding differs from iOS, so exact image equivalence is not certified.
- Legacy storefront card geometry updated to 232 dp, 43% media width and a 62/38 collage, matching the reference geometry.

## Block 2 — implementation started
- Guest booking recovery via verified booking number and email/SMS proof; stores returned booking token and trip snapshot.
- Refund preview and explicit cancellation request, using the corresponding iOS endpoints. Hard deletion is restricted to unpaid availability-check requests and requires affirmative server acknowledgement; never delete a paid booking as a substitute for cancellation.
- Checkout payment review, receipt review states/reasons/amounts, per-traveler visa/document requirements, publication status, secure document download and external viewer, completed-trip feedback and certificate metadata.
- Managed itinerary takes priority over generated fallback; delayed/rescheduled states retained; past timestamps alone do not mark events complete.

## Verified locally
- Smart/JourneyStore/Manual harness: 134 JVM assertions, simulated network/serialization; includes isolated edits, stale-commit guard, legacy catalog preserves flights, and RGB similarity.
- Flight/stage10 harness: 73 JVM assertions, simulated external DTO boundary.
- BookingStore runtime harness: passed with mock transport/vault; includes guest recovery, paid deletion prohibition and failed deletion acknowledgment preserving local booking.
- Kotlin PSI parser: 242 source files, 0 syntax errors. Not a Gradle or Compose type-check/build.
- Seamless Booking V1 and V2/V3 source contracts: passed.

## Release acceptance remains open
- Full Gradle Android build, current backend integration, real-device and screenshot comparisons against iOS are NOT executed. No Android SDK/Gradle toolchain or current iumrah Business backend source available in this environment.
- Block 1 targeted outstanding implementation tasks addressed; complete 1:1 acceptance remains unverified, including one-way/legacy edge cases, native image decoding and pixel rendering. The current update is not a claim of perfect parity.
- Block 2 is not finished: full iOS trip-mode experience, complete booking lifecycle and all UI states, notification/deep-link delivery and pixel alignment must still be validated/finished. Certificate display is metadata only, document preview uses Android external PDF/image viewer, not iOS QuickLook.
- Build on a test branch and exercise the actual backend in a non-production test context before shipping. This is one cumulative archive containing the earlier changes; do not overlay the earlier updates separately.
