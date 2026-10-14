# iumrah Android · Global Cupertino icon audit · Stage 10

**Reference:** `kepler-ios-main 5.zip` (current iOS UI source). **Android baseline:** `kepler-android-recovered.zip` + cumulative Android Stage 01–09 ZIP.

## Scope
- Audited **all** Android Kotlin files in `app/src/main/java`, including all UI submodules: Home, Flights, Hotels, Configurator, Package Flow, Booking, Account, Care, Chat, Advisor, Ziyarats, Prayer, eSIM, Transfers, Telegram, Plan, Onboarding and shell.
- All Compose control icons use the vendored `CupertinoSymbol` renderer; no Material Icons API references/imports remain.
- Replaced Unicode pseudo-icons in Flight Discovery, flight detail, flight assembly, itinerary selection, hotel selection, Ziyarats and Care Request with semantic Cupertino icons. New shared `CupertinoRatingStars` replaces star characters used as hotel rating *icons* in primary hotel listings.
- Textual route separators (for example `TAS → JED`), mathematical symbols, prose such as `3★ hotel` and the gift emoji in the **shared message text** are text content, not icon controls, and are left unchanged. Airline, payment, Haramain, Telegram and iumrah brand graphics remain their original assets; logos are not Cupertino symbols.
- Fixed global icon theme tint: default now follows `LocalContentColor` rather than hardcoded black. Content descriptions now propagate to Compose semantics. Icon with no explicit sizing now gets default 24 dp size.
- Added filled SF-style rating star and separate takeoff/arrival visual accents. Added scanner, search, chart, ticket, cart, Wi-Fi, sunrise, cloud-sun and other semantic shapes used by the current iOS vocabulary.

## Coverage
- **125** enum symbols with drawing implementations.
- **200** system-name aliases in `CupertinoSfCatalog`.
- **194/194** static `systemName:` / `systemImage:` values found in the reference iOS Swift source map to Cupertino symbols.
- **0** Material icon imports or `Icons.Default` calls across the Android Kotlin source tree.
- **0** outstanding Unicode pseudo-icons in the checked UI source set (`✈ ✓ ⌄ ⌃ ♥ ♡ ↻ ＋ ↗ ↩`).
- `scripts/verify-global-cupertino-icons.sh` enforces these invariants and is called by `scripts/verify-stage10.sh`, which existing GitHub workflows invoke.

## Validation limits
- Existing Android icon assets under `third_party/cupertino_icons/` are a **local Android Compose vector approximation** inspired by Cupertino/SF Symbols, not Apple's proprietary SF Symbols font or pixel-exact SF Symbols vectors. Source-level mapping does **not** prove pixel-perfect parity at runtime.
- **No full Android SDK / Gradle APK compilation** or on-device visual regression testing was available in this environment. This patch must pass GitHub Actions `:app:assembleDebug`, Kotlin tests, and Android device/emulator inspection before a production release.
- New localizations are outside scope and untouched.

## Applying
Upload the **cumulative** ZIP to the repository root. Do not apply Stage 01–09 separately: those changes are already inside this archive. The ZIP paths are relative to the Android repository root. The CI application workflow safely extracts and commits the changes; the debug-build workflow then performs compilation separately.
