# iumrah Visual Unification — Android source patch, 2026-10-25

## Applied

- Eliminated the procedural 38-KB `CupertinoIcons.kt` vector-drawing engine.
- Preserved all original `CupertinoIcon` / `Icon(CupertinoSymbol)` call-sites.
- Added bootstrap of the **real Flutter CupertinoIcons 1.0.9 TTF** and a Kotlin
  glyph lookup generated from the canonical Flutter 3.35.0 source registry.
- Added SF `systemName` semantic convenience API; disallowed fallback to an
  unrelated information icon when a systemName is unknown.
- Fixed distinct UIKit icon aliases where the real Cupertino font has variants:
  `house.fill`, `bell.fill`, `bell.slash.fill`, `info.circle.fill`, `tray`,
  `photo.fill`, `exclamationmark.triangle.fill`.
- Provisioned official OFL-licensed Lato Regular, Medium, Semibold, Bold, Light,
  ExtraBold (automatically before building); enabled Lato through Compose typography, base Android view themes,
  explicit previously-monospace call-sites, and generated share PDFs/images.
- Preserved all network paths, feature logic, routing and booking calculations.

## Before building

`python3 scripts/install-cupertino-assets.py` is executed automatically by
Gradle's `bootstrapCupertinoIcons` task. The font binary is fetched from the
version-pinned official Flutter package on the first connected build.

The delivery ZIP does not embed separate font binaries because this offline
execution environment could not retrieve it. The Gradle bootstrap enforces
installation before building. If HTTPS is blocked, the first build correctly
fails rather than reverting to fake icons.

## Verification

- `bash scripts/verify-global-cupertino-icons.sh`: source/asset checks.
- `bash scripts/verify-stage25.sh`: existing full static parity gates.
- `python3 scripts/install-cupertino-assets.py --verify`: run **after** the
  first connected bootstrap, to validate downloaded font and generated table.

The current environment does **not** have Android SDK/Gradle, and cannot
perform a real Android compile or screenshot-based iOS parity comparison.
Flutter CupertinoIcons cannot provide all Apple SF Symbols; audit any
substituted glyph listed in `glyph-resolution.json` once iOS source arrives.
