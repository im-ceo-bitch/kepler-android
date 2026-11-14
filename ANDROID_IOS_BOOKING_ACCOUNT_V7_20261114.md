# V7 — Booking & Account Architecture (2026-11-14)

Baseline: original Android repository + cumulative V6; iOS reference: kepler-ios-main 3.zip. No changes to iOS, backend, Gradle configuration or .github/workflows.

- Booking refresh: booking proof first; permanent account fallback only for authorization errors, not transient server failure. Separate existing checkout/chat 404 fallback preserved. Authoritative 404/410 cleans local entry. Pending quote proof preserved and retried. Refresh-all calls coalesced.
- Canonical linking: retry guest bookings on login/resume/new booking; memo by account token and booking proof, apply server pilgrimID and booking number. Late account responses ignored. No locally fabricated identifiers.
- Account-scoped cache: tokenless account-restored sessions carry owner ID; old unscoped tokenless cache is hidden until restored from server. Guest booking proofs retained. Account restore preserves valid local sessions on temporary network failure, clears on 401. This deliberately differs from iOS blanket clearing on errors for offline resilience.
- Merge server status, room/category, customization, eSIMs; retain complete local room category if partial server snapshot omits it. Account-only entries restored from authenticated endpoint.
- Push registration uses BuildConfig.APPLICATION_ID, both booking and notification registrations. Debug ID com.iumrah.beta (androidComponents override), release com.iumrah.app. Separate workflow YAML corrects V5's erroneous debug APK validation. No workflow in ZIP.
- Clear password/OTP after successful checkout activation; shared auth policy for chat. Existing delta chat cache and API preserved. No new UI pixel-parity claims.

## Verified locally
- 39 pure Kotlin policy assertions.
- 11 assertions exercising real BookingStore and model code with fake API/account/vault; compiler-generated serializer replaced only in temporary harness; StateFlow update shim for local Kotlin CLI. Not a real Android build or serialization integration test.
- Legacy Stage 10–25 validator chain passed; 181 Kotlin files, 54 routes, 21 XML documents in structural audit. Stage 07/08 source checks (17) passed; unified API audit (91 literal paths) passed.
- ZIP contains prior V6 entries and V7 updates; no protected workflow paths.

## Release blockers
Full Gradle/Compose APK and JUnit suite not executed: Android SDK/Gradle not installed here and Maven DNS unavailable. Verify real server account link, booking restore, status, FCM registration/delivery and auth revocation on Android. FCM requires actual Firebase configuration and server FCM capability; no delivery claim. Existing old tokenless local snapshots require authenticated server refresh after upgrade. iOS/Android visual comparison remains V8. Server DB migration not part of this Android patch.
