# iumrah Android V9 — Production Engineering

Base: `kepler-android-main.zip` + cumulative V8. iOS reference: `kepler-ios-main 3.zip`, especially `Sources/Networking/APIClient.swift`, `Sources/Services/IumrahConnectivityMonitor.swift`, `Sources/Views/Tabs/HotelsHomeView.swift`, and `IUMRAH_IOS_PERFORMANCE_20261114.md` in that archive.

## Changes
- Declare `ACCESS_NETWORK_STATE`, required by `ConnectivityManager` on Android. Handle unavailable connectivity service safely. Limit health probes to one active coroutine, cancel on path changes/close, ignore obsolete results, throttle same-path probes for 20 seconds, refresh on resume. Keep V8's stricter unified-backend JSON `ok` probe instead of the iOS HEAD-only reachability signal; no change to backend contracts.
- Shared default OkHttp transport, cancellable asynchronous API calls (including response reading), close response on every callback path. No connection-failure automatic retry for non-GET/HEAD API operations. Preserve prior auth/redirect policy and timeouts. Bound decoded JSON (16 MiB), media (64 MiB) and error bodies (64 KiB); oversized successful responses fail explicitly, never masquerade as empty successful results. These limits are Android engineering defaults, not copied iOS constants.
- Secure storage keeps existing ciphertext on read/decode failures and does not create a replacement key while decrypting. Serialize key generation. Existing format, alias and preferences remain compatible. Device Keystore invalidation still requires recovery; not a data-migration guarantee.
- Reject concurrent checkout submissions in the real `BookingStore`; release creation mutex on success/failure/cancellation. Preserve existing `quoteProof`, token and server price validation. Server-side idempotency after ambiguous network failures remains a separate requirement.
- Remove per-card hotel detail API calls from the Android discovery list, use existing catalogue cover instead of repeated identical images, leave full detail gallery intact. Cache package grouping per snapshot. This follows the supplied iOS performance direction; the old compatibility SwiftUI card still has a detail-fetch task, and this does not certify exact UI parity for that legacy card. Preserve the Android card footprint and actions rather than redesigning it in an engineering release.
- Memoize flight summary metrics and parse calendar dates once per result, preserve ranking. Route retry through the existing LaunchedEffect, snapshot inputs, propagate cancellation through optional fallback and do not publish stale results or clear newer loading state.
- Add source guard to existing Stage 25 chain, portable JVM transport/cancellation tests, real BookingStore checkout tests with fake transport/vault, and JUnit response-size tests (no new dependencies).

## Verification
- Kotlin compiler PSI syntax: all 204 main/test Kotlin files checked; 0 syntax errors. Not equivalent to Android type checking.
- V9 response bounds and coroutine bridge: 19 assertions, real implementation + fake HTTP transport.
- BookingStore runtime: 20 assertions, real store + fake API/vault/DTO boundaries; 9 new creation checks.
- Domain rules: 69 assertions. Architecture/cache: 39. Unified URL: 53, static route literals: 91. ChatDelta: 9. Discovery navigation: 13. Tests are JVM/static, not real backend transactions.
- Stage 10–25 chain and build environment preflight: PASS. Previous obsolete collage guard replaced with current cover/no per-card-request + action guard, no blanket suppression.
- ZIP application: cumulative V8 paths retained, only source/scripts/tests/report changes; no `.github/workflows`, secrets, signing, database or backend changes.

## Remaining production gates
- Full Gradle `:app:assembleDebug` and `:app:testDebugUnitTest` are not executed locally: no Gradle or Android SDK, DNS cannot resolve services.gradle.org. Run the existing manually triggered `iumrah APK Test Build` after V9 is applied (the original apply-only CI does not build). New JUnit tests will run there.
- Need real backend/account/booking/FCM E2E, device launch on fresh installation and after upgrade, Android Keystore recovery, network loss during checkout, frame/memory profiling and screenshots. Confirm binary media and JSON sizes against live traffic. Test cross-platform parity including manual configurator, Nuitee detail, and all localization. No confirmed pixel-level sign-off.
- For V10: signed release AAB, Play release checks, and regression fixes from actual CI/device results. No changes to existing application IDs or Gradle dependencies in V9.
