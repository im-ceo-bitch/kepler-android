# Android ⇄ iOS Parity / Stage 05 — Prayer Times

## Scope
- Source: `Sources/PrayerTimes/IumrahPrayerCalculator.swift`, `IumrahPrayerTimesView.swift`, `IumrahPrayerPreferences.swift`, and 7 bundled iOS `PrayerWallpaper*` JPEG assets.
- Native Compose screen reachable through Sidebar → Prayer Times, without altering main five tabs.
- Ported offline astronomical calculations, preset coordinates/zones, five methods, standard/Hanafi Asr, 8 daily moments, next prayer countdown, date navigation, wallpaper picker, persisted preferences, optional reminders.
- Current location is opt-in; Android location API supplies coordinates but **not the authoritative time zone**. It temporarily uses the device time zone; for another zone choose a verified preset.
- Android reminders use best-effort `AlarmManager.setAndAllowWhileIdle` and may be delayed by the OS. iOS uses `UNCalendarNotificationTrigger`; not guaranteed second-for-second equivalence.
- Seven wallpapers are byte-identical to the ones included in the supplied iOS project.

## Tests
- Swift original calculator was compiled and executed on 2026-10-12 and its eight preset-city schedules compared with compiled Kotlin port; 8/8 daily arrays matched to the minute.
- Unit tests: golden-output snapshots, variants, time-ordering, next prayer, invalid location.
- `scripts/verify-ios-parity-stage05.sh` performs cumulative root patch and calculator checks.

## Not done / parity gaps
- Arbitrary worldwide city lookup (iOS CLGeocoder) and timezone-resolution from location are not implemented for Android; fixed list of eight iOS presets and opt-in current location available.
- Wallpaper main-screen placement, color sampling, and notification scheduling need real-device side-by-side QA.
- Android full `:app:assembleDebug`, tests, Play Release were **not run** without Android SDK.
- iOS source has this standalone View but no visible reference in the parent navigation graph; drawer discoverability on Android is an added route, not a confirmed identical home shortcut.
- Do not start new localization expansion until all major parity gaps are closed.
