# iumrah Android · Block 2 / Common design system · 2026-10-26

## Reference and scope

- **READ-ONLY references:** the original `kepler-ios-main 3.zip` and the separately supplied iOS branding patch `iumrah_headers_and_world_dots_2in1_patch.zip` (applied only in an isolated reference copy).
- **Writable target:** Android only. Cumulative ZIP relative to the initial `kepler-android-main.zip`. No app APIs, booking rules, pricing models, navigation destinations or iOS Swift source were changed.
- **Allowed difference:** Lato 400/700 on Android versus Apple's system font on iOS, and native Liquid Glass implementation. These exceptions do **not** waive matching size, behavior or screen positions.

## Corrections verified against SwiftUI `Sources/Core/DesignSystem.swift`

| Shared primitive | SwiftUI source | Android implementation in block 2 |
|---|---|---|
| Geometry | page 18, card radius 28, hero radius 34, compact radius 19, control height 56, glass icon 46 | All six preserved in `IumrahDesign` and checked by `verify-block2-design-parity.py` |
| Standard primary button | 56 high, radius 19, scale 0.985, pressed background alpha 0.84, easeOut 0.16s | iOS-timed press animation, 0.84 fill alpha, unchanged onClick action and no synthetic ButtonStyle haptic |
| Standard secondary button | 56 high, radius 19, scale 0.985, opacity of **whole control** 0.90 when pressed, easeOut 0.16s; Liquid Glass on iOS | Whole-control opacity 0.90, same scale and timing; Android uses stable `surfaceVariant` rather than Apple-native glass |
| Content card | radius 28, inset 18, inset border 0.75 alpha 0.095, shadow black 0.045 blur radius 18 y8 | `Modifier.iumrahCard()` and `IumrahCardSurface`; Flight Search filters now use the shared modifier |
| Marketing card | radius 34, inset 22, border 0.7 alpha 0.075 / 0.08; gradient CareDark→Graphite when dark | `IumrahMarketingCardSurface` common Compose primitive |
| Grouped raised surface | SwiftUI `UIColor.tertiarySystemGroupedBackground` (dynamic) | Corrected Android dark raised surface to `#3A3A3C` |
| Photo card | 97.5% white or 10.5% grayscale and 96% photo-raised surface | Added constants for photo overlay card backgrounds |
| Text | Android Lato Regular/Bold as approved | Global text provider and MaterialTypography retained; no font change in iOS |
| Semantic icons | 37 `IumrahIconRole` definitions and role→system color | Mirrored all 37 roles; added iOS systemName-based badge entry point; original Cupertino glyphs only |
| Cupertino glyph canvas | per-symbol visual center matters | Center calculated from actual glyph bounding rectangle rather than font-wide ascent/descent |

## Audit results

- All **37** semantic roles have exactly the same color association as their iOS definitions (source-to-source comparison).
- **133** literal `Image(systemName: "...")` values found in original iOS SwiftUI are covered by the existing **204-entry** Android catalog. This validates vocabulary coverage, **not** visual identity of SF Symbol outlines and Cupertino font outlines.
- Eight formerly conflated outlined/filled SF alias pairs now resolve to separate verified official Cupertino font codepoints (`bed`, `building`, `clock`, `exclamation`, `hand`, `map`, `persons`, `person circle`). The Android catalog now defines **140** icon symbols. Exactly **two** outline/fill pairs remain collapsed: `airplane.circle.fill` and `bell.badge.fill` (no matching distinct Cupertino glyph was verified).
- The full existing Android structural verification up to Stage 25 passed, plus `scripts/verify-block2-design-parity.py` with and without the separate read-only iOS reference.
- Common card style was actually adopted by Flight Search filters, not merely declared as an unused helper.

## Not yet accepted / requires device-side verification

1. **Do not claim 100% pixel parity** from source-level assertions. A full Android Gradle build and screenshots have not been run in this Linux environment (SDK / Gradle unavailable). CI needs a separate `workflow_dispatch` run **after** the patch-apply commit.
2. Compose elevation shadow is not precisely the same rasterizer as SwiftUI's `shadow(radius: 18, y:8)`. Compare captures, then calibrate shadows and borders; offsets and alpha must be measured on real screens. New shadow values are structural approximations of the source parameters.
3. Flutter Cupertino font glyphs are licensed alternatives to Apple's SF Symbols and **cannot universally be one-for-one outlines**. Existing lossy aliases such as `airplane.circle.fill` → `Airplane` and `bell.badge.fill` → `BellBadge` remain open discrepancies; no Apple SF Symbols were copied into Android.
4. iOS dynamic system colors can depend on OS version, display and appearance; fixed Android ARGB tokens require device swatch comparison across light/dark and high contrast.
5. Liquid Glass visual rendering uses a native iOS compositor; Android secondary surfaces are functional and layout-compatible substitutes, not an identical implementation.
6. Remaining screens and their per-view typography, states, gestures, navigation and animations are covered by later blocks. This block does not certify all screen-specific design.

## Proposed next test captures

- Light / dark shared buttons: rest, pressed, disabled and dynamic type 100% / 130%.
- Filter card: collapsed/expanded, focus/keyboard, bordered/light/dark, with stable test data.
- Semantic icon badges (yellow, green, orange, blue), SF outline-vs-fill icon pairs and Cupertino glyph centering.
- Marketing light/dark surfaces, fixed viewport at 360/390/414 logical px and both display densities.

**Acceptance state:** Source-level implementation and structural regression tests completed for Block 2; device-build and image-diff approval pending. No blanket 100% parity claim.
