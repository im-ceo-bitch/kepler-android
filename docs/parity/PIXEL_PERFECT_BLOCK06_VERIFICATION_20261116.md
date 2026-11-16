# Block 06 — verification, 16 November 2026

Reference: `kepler-ios-main 6.zip`; Android base: `kepler-android-main 2.zip`; cumulative overlay: `iumrah-android-update-20261116-pixel-perfect-release-candidate.zip`.

## Completed on the actual overlaid source

- Kotlin PSI parser: 224 files, zero syntax errors. Not Android/Compose type checking.
- Smart Configurator: 78 assertions, real service and JourneyStore with mocked network.
- BookingStore: 27 assertions with fake transport/vault and serializer stubs.
- ChatService: paging, cursor, receipts, reset, authorization scope, account thread and clearing; 11 availability estimate and 11 ChatDelta assertions.
- Orbit V5: 280 JVM geometry assertions. Direct original Swift vs production Kotlin comparison: 53,530 numeric values across 11,110 rows, 101 sample times, max absolute error 2.84e-14 (threshold 1e-8), 22 palette entries identical. This is not pixel or frame timing certification.
- Stage 25 recursive structural gate (includes lower stages and release-source checks) reached final Block 04C/05 pass; Python tooling: 24 tests passed. Android build environment *source* check passed (SDK target 36, default code 15).

## New QA tools

- `bash scripts/verify-orbit-cross-platform.sh /path/to/kepler-ios-main` — compiles the real original Swift and production Kotlin geometry, compares emitted numeric fixtures and palette literals. Requires swiftc, kotlinc, kotlin, python3.
- Required screenshot coverage: `docs/parity/PIXEL_PERFECT_06_REQUIRED_CAPTURES.txt` (55 pairs). The manifest is a minimum selected release QA matrix, not proof every possible state or device size is covered. Use matching test data, screen size in physical pixels for the content region, locale, appearance, fonts, and elapsed animation time. Do not rescale. Keep system chrome separate and capture no private real booking data. The three non-Russian locale entries are critical-path samples; repeat full flows on all languages for comprehensive release testing.

```bash
python3 scripts/qa/compare_ios_android_screens.py \
  --ios captures/ios --android captures/android --out captures/diff \
  --required-list docs/parity/PIXEL_PERFECT_06_REQUIRED_CAPTURES.txt --exact
```

The comparator rejects missing pairs, mismatched sizes, overlapping input/output folders, and incomplete required coverage; deletes old comparison.json before a fresh comparison; returns nonzero for any differing RGB pixel in exact mode. For diagnosis without literal RGB certification omit `--exact` (MAE tolerance is not exact parity). The screenshot test suite uses synthetic data only.

## Remaining blockers — actual production evidence

This environment has Java and Kotlin/Swift compilers but no Android SDK, Gradle or emulator; downloading Gradle failed with DNS resolution error. No Xcode/SwiftUI rendering runtime. No real screenshot captures or signed artifacts were provided. APK/AAB compile, on-device behavior, live API E2E, full 55-pair visual comparison, and signing/16 KB binary validation remain unverified. Block 06 is not closed; release candidate only. Source checks are not a substitute for APK compilation.

Existing workflows unchanged. Apply the cumulative ZIP to repository root, confirm extraction commit, run existing **iumrah APK Test Build** (`run_unit_tests=true`), download APK and build reports. Test on Android and compare with iOS using the matrix. Only after a successful build, test results, and smoke checks run existing Play release workflow with a versionCode greater than all uploaded versions in Play Console; validate signed AAB and internal testing. If a build fails, provide build-report ZIP; no backend changes or secrets are required for this QA overlay. Do not mark Pixel Perfect 1:1 without real captures. Native SwiftUI and Compose font rasterization and glass shaders can differ even with identical layout geometry.
