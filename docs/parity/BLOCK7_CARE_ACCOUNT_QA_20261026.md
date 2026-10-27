# Block 7 — Care + Account (iOS → Android, 2026-10-26)

## Reference and baseline
- **Read-only iOS source:** `kepler-ios-main 3.zip` (including `CareHomeView.swift`, `IumrahAccountView.swift`, `IumrahIdentityRevealComponents.swift`, `IumrahActionConfirmationSheet.swift`).
- **Android starting point:** original `kepler-android-main.zip` + installed Android patch v5 + cumulative v10 (blocks 2–6).
- Final deliverable v11 is intended **only on top of installed v5**. Do not also apply v10.

## Confirmed source corrections
1. Care: restored the direct Call / Telegram / Chat tiles below the root header (105dp height; 25dp radius; 11dp spacing). Chat is disabled without an active booking.
2. Care: same navigation/call callbacks for quick and hero actions; existing remote Care profile and booked chat are preserved.
3. Care: restored user-initiated refresh and responsive two-column display (max hero width 470dp), matching the iOS wide layout intent.
4. Telegram logo is the byte-for-byte iOS-provided `IumrahTelegramMark` asset, **not** a generic airplane or hand-drawn substitute.
5. Account: locked ID card now opens an informational bottom sheet, with the primary action scrolling to the existing sign-in section.
6. Account: selected active journey prioritizes `IN_TRIP`, `READY_TO_TRAVEL`, `BOOKING_CONFIRMED`, `PAYMENT_PENDING`, `AVAILABILITY_CHECK` like iOS. Travel history sorts descending by trip start date and creation time.
7. Account: refresh-on-entry and pull-to-refresh use existing BookingStore API.
8. Account: sign-out confirmation now uses the original iOS photo and approximates the iOS 188pt image, 52pt button, 18pt radius, keeping the existing `AccountStore.logout()` operation.

## Verified
- Android stage 1–25 structural guards, plus new block 7 source contract.
- Kotlin parser zero syntax errors for three edited files.
- Original iOS visual resources copied byte-for-byte (SHA256 checked).
- ZIP integrity, cumulative patch application to v5, no iOS source written (verified separately).

## Not verified / remaining differences
- Full Gradle Android compilation / emulator or hardware UX tests (Android SDK/Gradle not available here).
- Pixel-by-pixel screenshot comparison, including typography, native bottom sheet, dynamic type, device insets, shadows and transitions.
- The iOS ID-unlock sheet mentions Apple Wallet; Android copy refers to native in-app wallet instead, **without falsely promising Apple Wallet functionality**.
- SF Symbols and CupertinoIcons may have different visual contours.
- Android currently supports only four languages vs eight in iOS; localization remains part of block 8.
- Some deeper Account and Care subflows still require end-to-end verification; block 7 is not certified 100% visually/functionally.

## Release instructions
Upload only the resulting `iumrah-android-update-20261026-v11.zip` to root of Android GitHub repository if current applied baseline is v5. Trigger Android CI full build from `workflow_dispatch` and compare iOS/Android screenshots before final approval.
