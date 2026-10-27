# Android ↔ iOS visual parity — Block 4 / Hotels + Flights (26 October 2026)

## Scope and installation baseline

- **iOS is immutable reference.** Compared original `kepler-ios-main 3.zip` plus the supplied read-only signature/world-dots patch for the updated Hotels header.
- **Android baseline is installed Block 1 v5.** This delivery contains a cumulative patch for *Blocks 2 + 3 + 4 relative to v5*. Do not upload v6 or v7 separately before or after v8.
- **This is source-level parity work**, not an APK compilation or device screenshot certification.

## Implemented in Block 4

### Hotels storefront

1. Replaced the legacy `iumrah Friends` hero leading to a modal with the **inline `IumrahFriendsCalculatorCard`** seen immediately below the Hotels hero on iOS, with 124dp artwork, 20dp image radius, 34dp outer card, 15dp inset, 22sp heading and semantic systemGreen.
2. Preserved the existing real Package Engine request (`storefrontPackageQuote`) and adult/child limits, room calculation, 420ms debounce, published direct-flight policy and Primary Hotel selections. No price is synthesized or inferred. Cancellation of a superseded request now propagates instead of being converted into an unavailable-price error.
3. Replaced the obsolete modal-specific presentation with an included-services bottom sheet; retained its localized descriptions and original Cupertino icons.
4. Fixed a Compose layout error where multiple result rows were being placed inside a `Box` instead of stacking; switched the reusable interior surface to `Column` with 12dp spacing.
5. Corrected currency output from `1200 $` to `$1200`, matched iOS grouped-result amount at 33sp, and formatted stars with the same `★` character used in SwiftUI segmented picker and hotel rating.
6. Preserved hotel storefront 204dp card, 28dp outer radius, media width `clamp(31%,108,122)`, 1+2 photo collage (63/37 vertical split, 3dp gap), 32dp favorite/share actions; aligned the visual rating tint with iOS `IumrahIconRole.rating`.

### Flights storefront

1. Added the second 40dp airline logo when outbound and return carriers differ, matching `StorefrontFlightOptionCard` SwiftUI media row.
2. Used Cupertino `PackageBox` for the iOS `shippingbox.fill` information row, and a filled status icon with the semantic iOS Umrah role tint.
3. Added a 14dp/0.035-opacity shadow to ready package cards, retaining the 118dp media, 28dp radius, source-level layout, server snapshot and disabled navigation when there is no package preview.
4. Kept one `FlightDiscoveryPanel` and one floating `FlightAssemblyCard`, with the existing live offer/calendar/recommendations/favorites services and no replacement mock data.

### Hotel details (verified, no code changes)

- 340dp hero carousel, gallery control, property photo overview and room gallery were found implemented. Not certified pixel-identical without screenshots.

## Verification

- `bash scripts/verify-stage25.sh`: PASS after updating a stale Stage 12 check that incorrectly required the old calculator modal.
- `python3 scripts/verify-block4-hotels-flights-parity.py --ios <original-ios> --ios-headers <signature-reference>`: PASS against untouched SwiftUI files.
- Kotlin command-line syntax pass can only check parsing without Android/Compose libraries: **does not constitute a compiled APK**.
- Final cumulative ZIP: verify ZIP CRC, paths and a reconstruct-and-compare using `original Android ZIP + installed v5 + delivery v8`.

## Open parity issues — not certified as complete

- Real screenshot capture and instrumentation on the same selected devices, content width, font scale, light/dark settings and language remain necessary for precise colors, vertical rhythm, safe-area insets, icon outlines and scroll physics.
- Live server/provider responses, price changes, no-internet state, calendar/flight filters and booking hand-off need interactive end-to-end testing. Structural checks do not test these scenarios.
- Cupertino icons are not a license to copy proprietary Apple SF Symbols onto Android; some glyph silhouettes can differ. The agreed platform exceptions remain Lato on Android / SF Pro on iOS and Liquid Glass implementation.
- Remaining cross-platform gaps outside Block 4 (for instance four Android vs eight iOS localizations) are tracked globally and prevent a 100% feature-parity claim.
- This block does not introduce a different Nuitee inventory/pricing provider. The input iOS reference here is its uploaded snapshot, not future integrations.

## Workflow

Upload only the single ZIP `iumrah-android-update-20261026-v8.zip` to the Android GitHub repository **root** (not unzipped). Existing workflow will apply relative paths. Do not modify the iOS repository. Run Android CI/APK build and compare physical/simulator screenshots before marking Block 4 fully accepted.
