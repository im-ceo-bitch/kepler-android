# iumrah Android — iOS 6 delta (20 November 2026)

Reference: `kepler-ios-main 6.zip`, compared with the preceding iOS archive and with the cumulative Android update `iumrah-android-update-20261119-zz-lint-fix.zip`.

## Included in this cumulative patch

- Retains all 84 files from the prior Android cumulative update, plus iOS-6-specific changes.
- Brand Experience 15: eight Why iumrah cards, mission, brand slogan, neutral format comparison, first-install six-screen onboarding that can be skipped without signing in.
- Staff-published D1 flight selection: carousel on Home **and** Flights storefront, direction selector, airport-local dates, row-ID pairing, cached origin catalogue, selection into shared `JourneyStore`. The live Aviasales search is separate; prices are shown only if published by the upstream service.
- Booking Empty State 17: compact actions to create a journey, recover a booking through existing verified OTP, or open saved packages; account-trip restoration distinguishes loading/failure from a confirmed empty list.
- Static black-and-white cold-launch overlay with a single haptic and no artificial loading delay; existing Home animations are retained.
- Account: a separate verified booking-recovery entry for authenticated users.
- Added JUnit contract tests for airport-local dates, published pair-IDs and airport/chronology constraints (execution requires Gradle CI).

## Checks completed in the working container

- `bash scripts/verify-stage25.sh` — exit code 0 (cascaded source/structure gates).
- `python scripts/verify-ios-parity-stage10.py` and existing home/booking checks had previously passed; check logs in CI for exact coverage.
- Standalone `kotlinc` lexical/syntax probe of new UI files: no parser-expectation errors; unresolved AndroidX references are expected when compiler is invoked without Android/Compose classpath.

## Acceptance criteria not yet demonstrated

This patch is **not** proof of 1:1 visual/architectural equivalence. Full Android Gradle/AAB build, Android emulator, screenshot-based comparison at iPhone 11 / 13 mini equivalence layouts, and live API/OTP/push/link testing were not available in this environment. The iOS reference's own docs similarly do not claim Xcode/device verification. Specific iOS 6 changes still requiring additional review or direct migration include:

- Trip Summary Design 13 final layout (photo hero, 16pt insets, 24pt cards, price-row grouping, fixed booking bar); previous Android review is kept to avoid altering payment logic without testing.
- Complete Brand Experience propagation into Support, Account, proposals and saved packages; comparison and slogan are currently implemented on Home.
- Booking Notifications 14 full backend behavior (server outbox, permanent unpaid-request deletion, account deletion rules); it is not sufficient to copy these rules into client UI without the current backend.
- Pixel-by-pixel QA, full Gradle lint/release build, and provider/backend testing.

The existing .github/workflows, Firebase files, signing config, and Android Gradle settings are unchanged by this patch.
