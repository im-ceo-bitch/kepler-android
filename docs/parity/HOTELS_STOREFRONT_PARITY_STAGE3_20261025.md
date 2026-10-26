# iumrah: Hotels storefront alignment — Stage 3 (Android only)

**Immutable reference:** `kepler-ios-main 3.zip` — no Swift, assets, Xcode project or iOS archive modified.
**Cumulative Android baseline:** first `kepler-android-main.zip`, **NOT** the prior two Android patch archives.

## Verified iOS components and matching Compose changes

| iOS source | Reference value / behavior | Android update |
|---|---|---|
| `Views/Tabs/HotelsHomeView.swift`, `boardTile` | Three HStack buttons, spacing **10pt**, height **94pt**, rounded **24pt** | `StorefrontSegmentedControl`: Row spacing 10dp, height 94dp, radius 24dp |
| `boardTile` | System **orange / blue / green** by board; white icon/text when selected | `IumrahColors.SystemOrange/SystemBlue/SystemGreen`, animated foreground/background |
| `boardTile` | Font size **12pt semibold**, icon size **24pt**, border .8 / 16% tint when inactive | Lato typography from global Android theme, Cupertino font glyph 24dp, 0.8dp line |
| `HotelsHomeView.body` | Shared `AirportSelectorButton` before `switch board`; present on all three boards | Airport selector no longer removed on Flights |
| `Views/Airports/AirportSelectorButton.swift` | Two independent buttons; travel badge 38pt; 42pt location | Independent Compose press targets inside raised 17dp container. Airport badge now `AirplaneTakeoff` |
| `hotelsBoard` and `flightsBoard` | 'FLIGHT FIRST', localized Ready packages title, subtitle and loading state | Header and standalone loading indicator shown only if needed, all four Android locales |

Previous patches (fonts, Cupertino, global controls, flight filters, status labels and header) are carried forward. All flight-search fields and `onClick` navigation continue to use existing Android services.

## Known gaps and policy

- CupertinoIcons and SF Symbols are distinct typefaces: mapped icons can be semantically equivalent without identical glyph outlines.
- Apple iOS 26 Liquid Glass cannot be faithfully implemented using Android's plain Compose background. Raised fallback uses the design's fallback surface, not simulated glass.
- iOS still uses its original system text font; Android is configured for Lato as requested. iOS must remain unchanged. Thus text raster metrics may diverge.
- Source-level geometry in logical points/dp is not evidence of equal physical pixels across densities.
- Full Gradle build, APK render, screenshot diff and booking API end-to-end tests require an Android SDK/device and were not run in the source-editing environment.

## How to verify

Run inside the patched Android repository:

```bash
python3 scripts/verify-hotels-storefront-parity.py
bash scripts/verify-global-cupertino-icons.sh
bash scripts/verify-stage25.sh
```

Optional immutable iOS source comparison:

```bash
python3 scripts/verify-hotels-storefront-parity.py --ios /path/to/original/kepler-ios-main
```

Do **not** upload the iOS repository or any iOS patch to Android or modify iOS files.
