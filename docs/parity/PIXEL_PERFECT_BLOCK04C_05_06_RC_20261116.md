# iumrah Android — cumulative production candidate, 16 November 2026
Reference: supplied kepler-ios-main 6.zip. Base: supplied kepler-android-main 2.zip plus the complete 03ABCD2/04AB overlay. No workflow, backend, secrets, versionCode or payment-provider changes.

## 04C — services and onboarding
- Nine service cards share stable IDs and 36 exact copy tuples extracted from the current SwiftUI reference, including explicit Uzbek Cyrillic. Card geometry 282×322, photo 164, corner 26, typography and carousel dots. Native snapping; guide and Care source artwork; fixed navigation back outside scrolling service sections; localized visa details and KSA Visa official link.
- Reference is five cinematic onboarding pages (not the proposed eight). Footer-safe scrollable layout, adaptive scene proportions, five-stop fade, introduction timing, shared Orbit V5 closing scene and localized three benefits. Some preview compositions remain Android-specific and are not screenshot-certified.
- eSIM polling stops when backgrounded and on session change; cancellation propagated and refresh state scoped to the selected booking. Reject operational eSIM responses when current booking authorization differs from request authorization. No new API or eSIM provisioning changes.

## 05 — motion and lifecycle
- Settle pager selection before haptics/analytics; intro fades before pager, finish interpolates scale/alpha/blur, disables pager input. Use source entrance spring and native shared Orbit; respect reduced motion for manual transforms.
- Pause Media3 playback in background, detach lifecycle observer and release player. Header wave/search outline/payment marquee stop when backgrounded or reduced motion enabled. Payment strip matches source speed 21/25 dp/s and lays out repeated logos beyond viewport without compression.
- Android shader/blur/icon and typography implementations remain platform equivalents, not identical iOS rendering.

## 06 — release preflight (not signed-off)
- Kotlin PSI syntax: 224 files, 0 errors; not Android type checking. Orbit V5 geometry: 280 JVM assertions. ChatService mock regression passed. Full Stage 1–25 structural gate passed before final eSIM/marquee changes; new checks verify final delta. Legacy source assertions were updated to check actual code (travel info now Home, booking owner-aware reload, embedded schedule routing, Care hero replacing redundant tiles, real flight detail, shared Orbit); no checks disabled or workflows changed.
- Final repeat Smart/BookingStore/full chat suites did not finish within execution budget; do not claim pass. Real Android SDK/Gradle/emulator unavailable; build debug and release in existing GitHub CI. No pixel screenshots provided, so block 06 remains open and 1:1 is unverified.
- Before production: check clean install, four languages, small phone and large fonts, first launch and skip, video foreground/background, services deep links and visa, outbound/return, hotel/room/meal/transfers, final server quote, OTP booking, status/document/payment/schedule switching, Care, account switching and eSIM. Confirm cancellation and no stale data. Compare same content viewport and state with iOS using existing scripts/qa/compare_ios_android_screens.py --exact; report differences rather than resizing. Verify signed AAB and Play version code, then test internal track before rollout.
