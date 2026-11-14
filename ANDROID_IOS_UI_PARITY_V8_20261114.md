# Android iumrah V8 — UI parity increment

Baseline: original kepler-android-main + cumulative V7. Visual reference: kepler-ios-main 3.zip (14 November 2026). No backend or workflow changes.

## Scope
- Shared discovery masthead with five scrollable actions and SwiftUI-derived dimensions; shared action headers for Booking, Care and Account.
- Home search expansion shared with the header; overlapping search measured as a sibling inside one parent, rather than positioned outside touch bounds. Updated root Home section order; removed old ready-packages root section.
- Hotels: same masthead and overlap, initial check-in +14 days and 7 nights; existing cache and network clients preserved.
- Flights: root catalog stays inside the tab shell. Transfer/package shortcuts use existing destinations and restore the origin tab. Bottom bar uses a fixed icon slot and selection semantics.
- Account: header controls actual sign-in/register form, changing mode resets local form state. Booking: status/documents/schedule select existing panels. Care: call/Telegram/chat actions, chat disabled without booking.
- Previous source checks updated to reflect current iOS structure; new source parity gate integrated in Stage 25. Kotlin compiler PSI syntax and JVM navigation test scripts included.

## Verification
- SwiftUI/source contract: 35 checks including comparison with supplied iOS tree.
- Kotlin compiler PSI: 201 source/test files, zero syntax errors. This does not resolve Android/Compose types.
- JVM navigation checks: 13 assertions against real AppChromeStore with test-only dependencies.
- Stage 10–25 and supplementary stage 1–2 source checks pass.
- Cumulative ZIP contains V7 files and V8 changes; clean overlay verified.

## Release limitations
- No Android SDK, Gradle executable or wrapper available in local environment; dependency download unavailable. Full debug APK compilation, instrumented tests and device rendering are not verified. Existing manual CI must run.
- This is a root-screen visual alignment increment, not a proven pixel-for-pixel clone. Actual matched screenshots are required; native icon/corner/text rendering can differ. Detailed screens, remaining motion and the independent iOS manual package builder are not fully ported. Transfer and My package currently use existing functional routes, not new independent discovery boards. No claim of production readiness.
