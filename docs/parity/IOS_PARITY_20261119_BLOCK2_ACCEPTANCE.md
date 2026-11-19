# iumrah Android — cumulative Commerce / Travel update (2026-11-19)

Baseline: supplied kepler-android-main.zip, with the supplied kepler-ios-main.zip as the reference. This is a source update, not an APK/AAB or a production acceptance certificate.

## Additional changes after the Block 1 / Block 2 checkpoint
- Deep links, notification opens and widgets: defer booking-specific routes until account restoration; check booking proof and matching identity; consume the opened push once. Open the intended package/chat rather than indiscriminately opening another user's booking. Tracking opens the selected trip; an inaccessible trip does not fall back to a different trip.
- Booking hub, detail, checkout: refresh during the resumed lifecycle; retain last successful itinerary/checkout during transient errors; reject stale account responses; expose travel-ready flight cards, package management, and the schedule.
- Cancellation: distinguish deletion of an unpaid availability-check request from cancellation/refund of a confirmed trip. Check the server checkout for an already requested cancellation before allowing another request; use refund preview and explicit confirmation, not automatic deletion.
- Managed itinerary: preserve explicit server management, including empty managed schedules; validate booking IDs; display execution status for all days and include dates introduced by a revised itinerary. Time passing does not by itself complete an event.
- Checkout and archive: protected in-app PDF/image viewer for documents and receipts, secure dialog window, temporary PDF cleanup; archive shortcut to documents and feedback; filter deleted trips; payment review/retry and guest recovery; no fictional downloadable certificate endpoint.
- Flights: stored flight schedule, not verified real-time operational data. Demo notifications explicitly identify themselves as tests. This is an intentional clarity improvement compared with the reference preview.

## Verified locally
- `bash scripts/verify-ios-booking-v7-store.sh`: PASS, real BookingStore and booking policies with test transport/vault. Includes seven added push-owner/access assertions.
- `bash scripts/verify-kotlin-syntax-v8.sh`: PASS, Kotlin compiler PSI, 243 files, zero syntax errors. Not Android type checking.
- `python scripts/verify-seamless-booking-20261117.py`: PASS, source contracts.
- `python scripts/verify-seamless-booking-v2-v3.py`: PASS, source contracts.
- Earlier checkpoint includes separate commerce JVM tests; the source changes from that checkpoint remain in this cumulative archive.

## Release acceptance still open
- Android Gradle compilation, resource linking, instrumentation and signed AAB: Android SDK/Gradle unavailable here, Gradle distribution host could not be resolved. Run existing CI unchanged.
- Live backend contracts, OTP, FCM delivery, actual booking/payment/refund/document upload, managed itinerary, certificate/feedback and account isolation: require the deployed backend and test accounts. No live customer data or transactions were used.
- Pixel parity: no Android/iOS simulator screenshot comparison was executed; native rendering, layout and text differences remain unmeasured.
- Reference iOS Flights screen itself is a stored-snapshot preview and a notification demo. Certificate contract exposes metadata only. No verified account deletion endpoint in supplied client sources. Do not claim live tracking, a downloadable certificate, or true account deletion based on this update.
- This checkpoint adds targeted source implementation; it does not certify exhaustive behavior or visual equality across all screens and API conditions.

## Installation
Overlay archive paths directly into the original Android repository. No previous patch is required. Existing Firebase, Gradle/signing configuration and GitHub workflows remain unchanged. No original files are removed. Run CI and review release acceptance before distributing.
