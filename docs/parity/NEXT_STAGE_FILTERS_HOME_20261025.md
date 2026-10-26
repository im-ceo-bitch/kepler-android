# Android-only iOS source parity: next stage, 2026-10-25

## Authoritative sources (read-only)

- `Sources/Views/Trip/FlightSearchFiltersCard.swift`
- `Sources/Views/Components/IumrahRootPageTitle.swift`
- `Sources/Views/Components/ConnectivityStatusPill.swift`
- `Sources/Core/DesignSystem.swift`
- `Sources/FlightEngine/FlightSearchRequest.swift`

All paths above are from the untouched original `kepler-ios-main 3.zip`.

## Mirrored UI features on Android

| iOS SwiftUI | Android Compose | Scope |
|---|---|---|
| `.iumrahCard()` 18pt padding, 28pt radius, 0.75pt thin outline | FlightSearchFiltersCard 18dp padding, 28dp radius, 0.75dp border + subtle shadow | visual |
| `IumrahIconBadge(settings, 38/16/13)` | `IumrahIconBadge(Sliders, SystemGray, 38/16/13)` | visual |
| two 54pt time `Menu` tiles | two `DropdownMenu` tiles, 54dp high, 16dp radius | visual and interaction |
| two baggage `Menu`s (any, 1+, 2+) | two `DropdownMenu` tiles instead of ± counters | visual and interaction |
| 48pt maximum fare entry, currency label, clear button | BasicTextField with 48dp surface, `$` prefix and clear | visual and interaction |
| secondary plain-text reset | plain-text reset | visual |
| 34pt compact pill within a fixed 88pt frame | fixed 88dp outer slot containing animated 34/88dp capsule | layout |
| animated border, stationary globe/text | animated sweep colors, stationary globe/text | motion approximation |
| `bell` / `bell.badge.fill` | canonical Cupertino `Bell` / `BellSignal` (= filled-badge glyph candidate) | semantic icon |
| `PAYMENT_PENDING` orange | Android orange only for `PAYMENT_PENDING` | semantic color |

## Preservation and verification

`FlightSearchFilters` field names, domain defaults, `onChange` callbacks, and booking/search services are unchanged. System colors, geometry tokens, app typography, icons from the earlier **Android-only** visual patch remain included.

Commands:

```sh
bash scripts/verify-global-cupertino-icons.sh
bash scripts/verify-stage25.sh
python3 scripts/verify-next-stage-parity.py --ios /path/to/original/kepler-ios-main
```

The latter `--ios` option is optional: CI can check Android-only invariants with no iOS repo. The iOS tree must not be copied into Android.

### Not established

No APK/AAB build, rendering screenshot, Cupertino font raster comparison, or service E2E test was run here. Source equality is not pixel equality. iOS currently uses Apple system type instead of Lato, so typography is an explicitly remaining difference. Complex Apple SF variants without exact Cupertino counterparts will remain visually approximate. Android-native menus and glass are not byte-for-byte copies of SwiftUI/UIKit controls.
