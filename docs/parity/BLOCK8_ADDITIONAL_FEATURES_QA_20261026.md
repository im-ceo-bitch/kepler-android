# Block 8 — additional experiences: source parity audit (2026-10-26)

## Immutable reference
- Original iOS archive `kepler-ios-main 3.zip`, **read only**.
- iOS reference files: `Sources/Views/ESIM/ESIMView.swift`, `Sources/AppShell/AccountNotificationsView.swift`, `Sources/Views/Planning/UmrahPlanFeature.swift`, `Sources/Umrah Flow/UmrahFlowRootView.swift`, and prayer/ziyarat source files.
- Android baseline: original `kepler-android-main.zip` plus **installed Android patch v5**. Releases v6–v11 have NOT been installed in GitHub. v12 includes changes of v6–v11 plus this block.

## Verified differences fixed in this pass
1. eSIM: iOS refreshes on pull and on app foreground. Android previously polled every 60 seconds only. Android now has both UI pull-to-refresh and foreground refresh, preserving existing 60-second update and backend API.
2. eSIM activation: opening `Uri.parse("LPA:1$...")` via a normal Android ACTION_VIEW intent is not a valid provisioning mechanism. Android now opens the **public EuiccManager eSIM management UI** when supported/enabled. The user can then use the QR or manual SM-DP+/activation code. This is platform-correct, **not** in-app one-tap activation; automatic silent provisioning is not implemented. Other cases show localized error text, never a fake success.
3. eSIM: expiration date uses the current selected locale, and megabyte counts use rounded integers like iOS instead of truncation; all new help text exists in all four Android app languages.
4. Reminder setup: Android 13+ requests POST_NOTIFICATIONS when the user enables reminders or requests a test; a denied permission no longer leads to an incorrect success state. Existing AlarmManager schedule and reminder content are unchanged.
5. Actual notification destination is `AccountSignalsParityScreen` (the separate legacy `NotificationsScreen` is unused). Its notification cards now use destination-specific official Cupertino glyphs, format dates as Today/Yesterday/date, and label the target route as in the SwiftUI source. Added pull-to-refresh and replaced the generic Telegram icon in the Signal tab with the original iOS Telegram mark already present from block 7. Existing hide/restore/open actions still use the real notification store.

## Source checks
- Kotlin syntax parsed with Kotlin PSI on all edited files.
- Existing `verify-stage25.sh` and new `verify-block8-integrations-parity.py` are run.
- ZIP contains only changes relative to original Android + v5; test application reconstructs the edited Android project byte-for-byte.

## Open items; block 8 NOT signed off 100%
- iOS supports eight app languages (EN/RU/UZ-Cyrl/UZ-Latn/TR/AR/ID/MS). Android only four. The remaining languages need a complete app-wide model, content, and RTL QA pass, not ad hoc strings in one page.
- iOS `UmrahFlowRootView` models extra pre-start/inUmrah stage; Android Advisor flow currently uses a different entry and stage representation. Full state-by-state UI, audio, remote content and navigation parity is not demonstrated.
- Notification **Reminders** tab on iOS embeds the entire reminder center. Android displays a link into Umrah Plan instead. This is a known visual/interaction difference.
- System eSIM settings and Apple iOS 17.4 `esim_qrcode_provisioning` use distinct OS APIs. This is platform-specific; neither supplies a guaranteed identical one-tap experience across all devices.
- Offline prayer calculations, Qibla orientation, Ziyarat map content, onboarding, Push consent, wallpaper themes and accessibility still need real device/test-account parity checks.
- Full Android Gradle build, screenshot diff and end-to-end remote data/payment tests are NOT executed in this environment. Structural checks alone do not certify 1:1 parity.

## Install
Upload one cumulative v12 ZIP in the Android repository root (only if v5 is the latest applied update); do not apply v6–v11 separately. Trigger the real Android build in GitHub Actions and run screen comparison before approving block 8.
