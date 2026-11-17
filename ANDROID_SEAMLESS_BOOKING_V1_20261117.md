# Seamless Booking Android V1 — 2026-11-17

Cumulative over the Pixel Perfect Block 06 Hotfix 03. New iOS reference: kepler-ios-main 4.zip (archive is truncated; 1,272 complete local entries recovered and CRC checked; no claim of complete archive recovery).

## Implemented
- Guest checkout uses email/SMS OTP before creating the quote-authorized booking. It forwards the verified contact and optional FCM device token in notificationIntent; attaches verified contact using the booking proof. No password/registration form in checkout; no automatic repeat of ambiguous create POST within the current checkout attempt. The OTP proof is request-only.
- Account root uses Find booking; status uses passwordless access. Server session is accepted only after authorized recovery/linking. A missing booking-session endpoint after checkout does not undo the local guest booking.
- Care uses booking scope for guest and account scope only for a matching verified owner. No automatic attachment of unrelated trips. Push routing checks local booking authorization and optional owner ID, and registration uses exact-case FCM tokens and checks the server response.
- Booking passport upload is photo-only; server ok=true required and current account checked. Companion profiles retain their own editing flow.

## Verification
- Kotlin PSI: 224 source/test files, zero syntax errors. Not Compose/Gradle type checking.
- Booking policy: 57 assertions; BookingStore regression suite including guest Care, authorization, push false response, case-sensitive token and closed booking: pass with mock network/vault.
- Stage 1–25 structural release gate and new Seamless source contract: pass. Existing obsolete registration/manual passport requirements updated to reflect current behavior, not disabled.

## Remaining / deployment limits
- The last available authoritative backend source (lattice-mobile-main 3 + FCM patch) has OTP/verified-contact routes but does not contain the newer booking-session exchange or notificationIntent processing. Client supports the new contract, but durable booking creation deduplication, contact outbox, automatic profile exchange and real SMS/email/FCM delivery must be verified against the current backend. Do not deploy the embedded iOS server as production.
- New Smart component editing, shared official/manual hotel pagination, Business offer, home personalization and final visual synchronization remain subsequent work. Existing checkout route is migrated; other iOS-specific entry paths need comparison. This is a source checkpoint, not a production-certified 1:1 release.
- No Android SDK/Gradle runtime available here; run the existing APK workflow before AAB and test on a device. No GitHub workflow, credentials, backend, or signing changes.
