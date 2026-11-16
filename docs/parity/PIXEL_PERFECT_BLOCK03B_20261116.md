# Pixel Perfect Block 03B — Smart configurator source delta

Reference: `kepler-ios-main 6.zip` SHA-256 `d967068e29311336f5b65d28985fefdb55f310b7ff74c3ae38299f1587a8c130`. Android base `kepler-android-main 2.zip`, Block 02. Cumulative update includes all seven Block 03A files (one amended here) plus Smart changes and this document. No workflows or backend changes.

## Swift source contracts ported
- `Sources/Views/SmartConfigurator/SmartConfiguratorFlow.swift`, `partyContent`: child-age menu 0–11 and infant/adult validation message in four languages.
- `flowContent`: step-content fade and 10dp vertical entrance; `assemblyContent`: 350ms animated size change as previews arrive. These are Compose equivalents, not frame-certified SwiftUI transitions.
- `assemblyContent`, `errorText`: specific hotel network/room errors, family flight advice, search-city wording, idle progress circle, estimated total and manual confirmation disclosure.
- `footer`: Review package CTA distinct from booking, retry label, estimated total and signed, allowed quote check. New assembly attempts reset progress and city. Existing private assembly and replacement semantics preserved.
- `Sources/Views/Package/FinalPackageView.swift`, `bookingBar`: Nuitee review shows estimated amount, per-person price and pre-payment confirmation notice. Currency rendering uses the quote currency and exact decimal amount. Existing Android reprice and booking flow preserved.

## Verification
- Kotlin PSI: 221 Kotlin files, zero syntax errors.
- `scripts/verify-stage25.sh`: passed existing static checks.
- `scripts/verify-ios-smart-sync02.sh`: 62 JVM assertions passed for actual Smart service and JourneyStore with mocked network; no live requests.
- Full Android Compose typecheck/APK and device comparison not run (no Android SDK/Gradle environment). The Block 03A JUnit file is not executed by those JVM checks.

## Remaining
The Android Smart flow opens NuiteeJourneyReviewScreen rather than the iOS FinalPackageView route. Full final review layout, navigation return semantics, first-launch Smart entry, exact native glass and animation-frame comparisons are not certified. Existing route and checkout validation intentionally retained rather than replacing them with an unverified architecture. No claim of completed 1:1 parity.
