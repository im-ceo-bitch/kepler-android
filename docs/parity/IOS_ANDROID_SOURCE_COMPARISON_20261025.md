# iumrah iOS ↔ Android source visual parity audit (2026-10-25)

Read-only comparison against the **original, unchanged uploaded SwiftUI iOS** tree. Changes are applied exclusively to Android.

## Checkable facts

- iOS Swift files: **270**; direct `Image(systemName:)` occurrences: **666**.
- Distinct literal SF Symbol names: **158**, with **604** literal references in Swift source.
- Android SF→Cupertino alias entries: **204**; **missing literal aliases: 0**.
- Missing literal names: none.
- Original iOS Lato text style calls: **0**. Existing `.font(.system(...))` styles: **881**. No typography changes were made to iOS; Android is configured for Lato Regular/Bold.
- Android light page color matches iOS standard grouped page approximation: **True** (`#F2F2F7` in light mode).

## Shared nominal design metrics

| Design token | iOS pt | Android dp | Numeric equality |
|---|---:|---:|---|
| pagePadding | 18.0 | 18.0 | ✓ |
| cardRadius | 28.0 | 28.0 | ✓ |
| heroRadius | 34.0 | 34.0 | ✓ |
| compactRadius | 19.0 | 19.0 | ✓ |
| controlHeight | 56.0 | 56.0 | ✓ |
| glassIconSize | 46.0 | 46.0 | ✓ |

## Important limitations

1. **SF Symbols cannot be shipped to Android**; Android uses the MIT Flutter CupertinoIcons glyph asset. Some aliases are semantically similar, not geometrically identical. No pixel-perfect icon claim.
2. Values in pt vs dp are numerically synchronized but may produce different on-screen pixels due to density, font rendering, navigation bars and safe areas.
3. iOS has dynamic UIKit appearance, including iOS 26 Liquid Glass. Android Compose's deterministic surfaces are not a literal implementation of Liquid Glass.
4. Sources only: **no Android Gradle build, Xcode build, emulator test or screenshot regression comparison has been run** in this environment.
5. iOS source is left completely unchanged and remains the visual reference. Android uses Lato regular/bold globally via Compose typography. Since original iOS uses its existing fonts, typographic pixel parity is not guaranteed.
6. Only Android Gradle bootstrap installs original Flutter CupertinoIcons and OFL Lato fonts before native compilation. The original iOS project and its CI are not modified. Font binaries are not distributed in this source archive.
7. Semantic SVGs, illustrations, maps, photos, OS components and screen-specific logic need device-level comparisons before claiming total visual identity.

Audit command:

```bash
python3 scripts/audit-ios-source-parity.py --ios /path/to/kepler-ios-main
```
