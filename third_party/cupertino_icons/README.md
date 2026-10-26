# iumrah Android: genuine Cupertino glyphs (native Jetpack Compose)

This is a Kotlin / Jetpack Compose repository, **not a Flutter application**.
Adding `cupertino_icons:` to `pubspec.yaml` or a fictional Gradle package would
not work. The Android implementation uses the **actual typeface from the MIT-
licensed Flutter `cupertino_icons` 1.0.9 package**, and its glyph codepoints
from the Flutter 3.35.0 `CupertinoIcons` registry.

On the first Gradle build, `bootstrapCupertinoIcons` runs
`scripts/install-cupertino-assets.py`, retrieves the version-pinned published
package and generates `app/src/main/res/font/cupertino_icons.ttf` and
`CupertinoGlyphCodepoints.kt`. Once cached, builds require no further download.
To do this manually: `python3 scripts/install-cupertino-assets.py`.

The Compose `CupertinoIcon` API renders font glyphs via native canvas text.
**There are no hand-drawn paths**, and UI call sites can keep the existing
`CupertinoSymbol` enum. `CupertinoSfCatalog` translates iOS `systemName`
strings to the same semantic interface and fails for unknown strings rather
than silently substituting `info.circle`.

## Pixel-parity limitation

Flutter CupertinoIcons is a subset of Apple SF Symbols, so it cannot cover
all modern iOS SF glyphs. `symbol-map.json` lists preferred Cupertino glyphs
and acceptable visual alternatives; `glyph-resolution.json` records any
alternate chosen at build time. Especially icons introduced after the original
Cupertino asset's scope (e.g. a bed, modern building variants, Wi-Fi alert,
passport) may **not** match their SF Symbol counterparts. Build logs warn
where alternatives were used. An exhaustive 1:1 visual audit requires the
actual iOS repository and screenshot comparison. **Do not describe the
Android icon set as genuine Apple SF Symbols.**

## Licensing

The genuine `cupertino_icons` font is fetched at build time and distributed under the package's MIT
license; the license is fetched with the package archive. Flutter SDK icon
metadata is BSD licensed. Apple SF Symbols (Apple SDK-provided images) are
**not** redistributed, copied, or bundled into the Android application.

In air-gapped build environments, run bootstrap once where outbound HTTPS is
available, then check in/cache only the licensed MIT font and generated table.
There is deliberately no fallback to hand-drawn geometry if asset bootstrap
fails.
