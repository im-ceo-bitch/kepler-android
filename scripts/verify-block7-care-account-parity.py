#!/usr/bin/env python3
"""Block 7 source contract, pinned to the original iOS assets of 2026-10-26.

This is a structural regression gate. It is NOT a screenshot or UI test.
"""
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CARE = (ROOT / "app/src/main/java/com/iumrah/beta/ui/care/CareHomeScreen.kt").read_text()
ACCOUNT = (ROOT / "app/src/main/java/com/iumrah/beta/ui/account/AccountScreens.kt").read_text()
IDENTITY = (ROOT / "app/src/main/java/com/iumrah/beta/ui/account/AccountIdentityWallet.kt").read_text()

def check(condition, message):
    assert condition, f"BLOCK 7: {message}"
    print("PASS", message)

check('CareQuickContacts(' in CARE and CARE.index('CareQuickContacts(') < CARE.index('CareIntro(language = language'), "Care quick actions precede introduction")
check('Arrangement.spacedBy(11.dp)' in CARE and '.height(105.dp)' in CARE and 'RoundedCornerShape(25.dp)' in CARE, "Care quick action tiles: 11dp spacing, 105dp, 25dp radius")
check('chatEnabled = activeSession != null' in CARE and '.clickable(enabled = enabled' in CARE, "Care chat is disabled without active booking")
check('CupertinoSymbol.Phone' in CARE and 'CupertinoSymbol.Send' in CARE and 'CupertinoSymbol.ChatBubble' in CARE, "Care quick contacts preserve iOS action order")
check('PullToRefreshBox(' in CARE and 'bookingStore.refreshAll()' in CARE, "Care refreshes all existing booking sessions")
check('if (maxWidth >= 700.dp)' in CARE and 'widthIn(max = 470.dp)' in CARE, "Care adapts to wide iOS-style two-column layout")
check('onChat = openChat, onCall = callCare, onTelegram = openTelegram' in CARE, "Care actions use the same callbacks in quick and hero tiles")
check('fun IumrahLockedIdentityCardAndroid(language: AppLanguage, onUnlock: () -> Unit)' in IDENTITY and '.clickable(onClick = onUnlock)' in IDENTITY, "Guest identity card is actionable")
check('IumrahLockedIdentityCardAndroid(language) { unlockSheet = true }' in ACCOUNT, "Guest ID unlock opens a sheet")
check('AccountIdentityUnlockSheetAndroid(' in ACCOUNT and 'animateScrollToItem(2)' in ACCOUNT, "Unlock flow navigates to existing sign-in options")
check('accountTripPriority(' in ACCOUNT and 'minByOrNull { accountTripPriority(it) }' in ACCOUNT, "Active trip follows iOS status priority")
check('PullToRefreshBox(' in ACCOUNT and 'bookingStore.refreshAll()' in ACCOUNT, "Account supports refresh on entry and pull")
check('AccountSignOutConfirmationSheetAndroid(' in ACCOUNT and 'height(188.dp)' in ACCOUNT and 'height(52.dp)' in ACCOUNT, "Sign-out sheet uses original iOS photo and geometry")
check('scope.launch { accountStore.logout() }' in ACCOUNT, "Sign-out invokes existing real AccountStore logout")
for rel, digest in {
    'app/src/main/res/drawable-nodpi/iumrah_telegram_mark.png': '5ad7907ec39fa1305ff47c304425737e8e9892651c8d6a97a5b4f4b155117205',
    'app/src/main/res/drawable-nodpi/iumrah_logout_confirm_hero.jpg': '7a66103a8907c34600677ab58dbfb9ad4e50c5b8fd3b3f92c40c8a9bf8742714',
}.items():
    p = ROOT / rel
    check(p.is_file() and hashlib.sha256(p.read_bytes()).hexdigest() == digest, f"Original iOS illustration byte-identical: {p.name}")
print('Block 7 source contract passed; APK compilation/device QA still required')
