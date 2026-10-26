# iumrah Android — iOS-referenced visual alignment

## Scope

**Only the Android source is modified.** The uploaded `kepler-ios-main 3.zip` is the read-only visual/design reference. No iOS Swift, Xcode, asset, project or CI files belong in this archive or patch. Do not apply the previously supplied combined iOS/Android parity patch.

## Android changes

- Global Lato Regular/Bold typography via `IumrahTheme.kt`.
- Authentic Flutter CupertinoIcons font outlines for Compose; no manually drawn icon geometry in the shared icon renderer.
- Expanded SF-symbol-name-to-Cupertino alias catalog based on iOS source.
- Shared nominal layout tokens and light grouped background based on original iOS source.
- Android-only parity audit (`scripts/audit-ios-source-parity.py`).

## Technical limits

The iOS project continues to use its original typography and original Apple SF Symbols. Cupertino Icons are not identical to every SF Symbol. No Xcode or Android device builds, screenshot comparisons, or verified pixel-identical outcome have been completed. Some UI elements may still differ between platforms. The Android resource bootstrap may require network access at build time to fetch the freely distributable icon and font assets.

Delivery for the next stage is a cumulative **Android-only PATCH** rooted directly at Android repository paths. Previous patches are already included; do not apply them separately. See `HOW_TO_APPLY_RU.md`.
