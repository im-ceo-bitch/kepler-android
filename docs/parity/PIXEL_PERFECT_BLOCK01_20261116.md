# Pixel Perfect Audit — Block 01, source design system

Reference: uploaded `kepler-ios-main 2.zip` (2026-11-16). Android baseline: uploaded `kepler-android-main.zip`, Hotfix 03 cumulative, Sync 01, Sync 02, Sync 03, combined Sync 04+05. No server or workflow changes.

## Changes
- IumrahType nominal shared sizes: 34 / 28 / 22 / 18 / 16 / 14 / 12. Shared section title uses the section role; buttons use 18dp horizontal padding and centered text. Android line heights and the approved Lato font remain Android implementations; not a font-rendering equivalence claim.
- All 157 literal SF names from this iOS snapshot have a catalog alias; 218 total aliases, 149 Cupertino enum entries. Nine new real Flutter glyph names checked in Flutter SDK 3.35.0's `icons.dart`. Separate outline/fill variants for bag, card, plane, document and existing checkmark. Apple-only compound symbols use documented approximations. Existing approximations elsewhere are not eliminated merely by alias coverage.
- Semantic icon inference mirrors original Swift (including heart, chat, mail and ticket rules).
- Header logo maximum width respects actual bleed width, page insets and symmetric 48dp slots. Removed the obsolete numeric unread overlay; current iOS uses bell/bell.badge only.
- Asset generator now invalidates stale codepoint caches on mapping changes; Gradle records the bootstrap script as an input. No bundled font files are part of the patch.
- Strict screenshot comparator `--exact` detects every RGB pixel difference, never resizes mismatched screenshots. This does not measure motion, accessibility, business behavior or cross-platform color management; screenshot comparison must use the same content viewport, appearance, font scale, language and deterministic state.

## Local validation
- `verify-kotlin-syntax-v8.sh`: 219 main/test Kotlin files, zero PSI syntax errors.
- `audit-ios-source-parity.py --ios <actual iOS checkout>`: 157/157 literal names, 6/6 nominal metrics.
- `verify-block2-design-parity.py --ios <actual iOS checkout>`: pass, including 37 semantic color-role assignments and common button/card source contracts.
- `verify-pixel-perfect-block01.py --jvm`: 218 Swift/Kotlin role matches, 218 lookup checks. Original Swift inference compiled independently; Kotlin real catalog and inference compiled with minimal color stubs. This is not full Android compilation.
- `verify-global-cupertino-icons.sh`: pass.
- `python -m unittest discover -s scripts/tests -p test_install_cupertino_assets.py`: 7 tests pass.
- `python -m unittest discover -s scripts/tests -p test_pixel_comparison.py`: 1 synthetic test with multiple assertions passes.
- `verify-stage25.sh`: pass, including new Block 01 gate.

## Not certified
No Android SDK/Gradle installation or real-device captures available locally. No debug/release APK compiled in this turn; glyph asset generation must still run against actual font data in CI. The iOS archive does not contain Lato font binaries; iOS has UIFont fallback and native SF Symbols/system rounded fonts, while Android uses Lato + Cupertino, not Apple-proprietary glyphs. UIKit adaptive color resolution, continuous corners, shadows, native Liquid Glass and all screen-level geometry/motion need real captures. Source design-system alignment is not 100% visual parity.

## Remaining work blocks
02: root shell, Home, navigation, page-help, onboarding. 03: Smart, manual configurator, flights and hotels including all sheets and empty/error states. 04: bookings, account, Care, chat, services and Rewards. 05: all motion, gestures, haptics, performance, service/cache contracts. 06: builds and actual paired screenshot/frame regression plus production scenarios. Do not close any block on static checks alone.
