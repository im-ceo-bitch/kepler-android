# Block 8 — Reminder Center follow-up (2026-10-26)

## Explicit scope
- **Exclude Umrah Flow entirely** (pilgrim ritual stage-by-stage flow). User requested it not be copied or parity-tested.
- Preserve/port **Umrah Plan** (future trip planning and notifications). This is a different feature from Umrah Flow.
- iOS repository is READ ONLY. Only Android changes in this patch.

## Source comparison
Reference: `kepler-ios-main 3.zip` / `Sources/Views/Planning/UmrahPlanFeature.swift` / `UmrahPlanReminderCenterView` and `UmrahPlanHomeEntryCard`. The previous Android Signal reminders tab displayed a fixed 60/30/20/... schedule description and only a navigation CTA, regardless of whether the user had created a trip. This did not match iOS.

Android changes:
- Signal → Reminders now embeds a live stateful `UmrahPlanReminderCenter`.
- Without a saved plan it renders the iOS-style warm-gradient creation card with a stacked emblem and Create trip action; it explains that reminders appear only after planning.
- With a plan it renders a 286 dp, 32 dp radius background card with status and reminder state, and a separate reminder details card (48 dp blue badge; top 4 schedule entries; saved time; 50 dp black edit button with 17 dp radius).
- Edits use the pre-existing Android `ReminderEditor`, `PlanStore.save` and `PlanScheduler`. They do not invent a second notification scheduler.
- On resume, the center reloads the saved trip, avoiding stale data after a trip is created/edited on another page.
- The old Android 13+ POST_NOTIFICATIONS permission gate is retained.
- Original lifecycle of signals, Telegram and eSIM remain intact.

## Validation
- Kotlin PSI syntax parsing: 0 syntax errors in the two edited Kotlin files. PSI does **not** validate Android Compose type resolution.
- 25 structural stages, including 18 previous block 8 checks, passed.
- 11 additional checks on the reminder center passed.
- ZIP application is tested against the original Android + installed v5 baseline with byte-for-byte content verification.

## Not yet verified / remaining
- Complete app-wide localization: iOS has eight choices (ru, en, ar, tr, id, ms, uz, uz-cyrl) and this Android baseline has only four. Do not claim this is completed: adding enum cases without auditing exhaustive `when` expressions and translating all strings is unsafe.
- End-to-end eSIM provisioning, notifications, push flows, maps, accessibility and payment scenarios require real devices/test accounts.
- Full Android Gradle/APK build and screenshot/pixel comparisons are still pending and cannot be certified by PSI or source-only tests.
- Umrah Flow parity has been deliberately **removed from the scope**, not silently marked complete.

## Deployment
Patch is cumulative for **blocks 2–8** relative to the already-installed v5. Do not apply v6–v12 first. No files removed. No iOS source included.
