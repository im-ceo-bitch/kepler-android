# Android parity Stage 09 — flight Kotlin compile correction

## Failure reproduced from user-supplied GitHub Actions logs

`build-install-debug/6_Build debug APK.txt`: `:app:compileDebugKotlin` failed with
`FlightDiscoveryPanel.kt:608:1 Syntax error: Expecting a top level declaration.`

## Root cause

An extra closing curly brace was present immediately after
`FavoriteFlightsSheet` and before the file-level `airportCity` helper. This was
introduced in Stage 03 and persisted in later cumulative patches.

## Fix

Removed exactly that stray brace from
`app/src/main/java/com/iumrah/beta/ui/flights/FlightDiscoveryPanel.kt`.
No business logic, UI presentation, prices, or localization code was changed.
Added a regression guard in `scripts/verify-build-env.sh`, which the existing
GitHub debug workflow executes before `:app:assembleDebug`. Added a Stage 09
verification script for the cumulative patch.

## Verification and release boundary

Archive extraction and repository/parity verifiers can be run offline.
Local Kotlin CLI can parse the corrected file, but unresolved Android/Compose
symbols prevent compiling it without Android SDK and Gradle dependencies.
A successful `:app:assembleDebug` on GitHub Actions is still necessary before
calling this artifact a build-validated release candidate.
