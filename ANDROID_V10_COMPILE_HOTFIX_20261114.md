# V10 compile hotfix 01 — 2026-11-14

Source: logs_118094182122.zip, task :app:compileDebugKotlin (18 diagnostics).

- ESIMScreen: select dictionary entry before phrase translation; all branches return strings.
- OnboardingFlow: capture BoxWithConstraints maxHeight outside nested Column scope; import sp and InfiniteTransition.animateFloat.
- AppShell: import AccountPasswordSecurityScreen.
- CuratedRoundTripFlights: pass declared language parameter l.

Apply cumulative ZIP to root using existing Android update workflow. No workflow or backend changes. Archive name sorts after prior cumulative V10.

Release workflow input failure is independent. Enter version_code as a plain integer >=15 and greater than every already uploaded Play versionCode (e.g. 16 only if highest uploaded is 15); version_name e.g. 2.0.4 if intended. Keep the release guards intact. Run debug build first. Do not rerun the old failed job because it uses the old commit/inputs.

Local verification: Stage 10–25 source gates passed, Kotlin PSI 204 files / 0 syntax errors, extracted production localization functions compiled with actual translation catalogs (42 checks), 15 release tests passed. Not a full Gradle Android compilation; SDK and Gradle unavailable locally. Real debug/release compilation and unit tests required in CI.
