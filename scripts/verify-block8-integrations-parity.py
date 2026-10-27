#!/usr/bin/env python3
"""Android Block 8 source regression gate, NOT APK/screenshot parity certification."""
from pathlib import Path
root=Path(__file__).resolve().parent.parent
esim=(root/'app/src/main/java/com/iumrah/beta/ui/esim/ESIMScreen.kt').read_text()
planning=(root/'app/src/main/java/com/iumrah/beta/ui/planning/UmrahPlanScreen.kt').read_text()
signal=(root/'app/src/main/java/com/iumrah/beta/ui/account/AccountSecondaryParity.kt').read_text()
shell=(root/'app/src/main/java/com/iumrah/beta/ui/shell/AppShell.kt').read_text()
checks={
'Android eSIM matches iOS pull-to-refresh': 'PullToRefreshBox(' in esim and 'onRefresh = { scope.launch { refresh() } }' in esim,
'Android eSIM refetches upon resume': 'LifecycleEventEffect(Lifecycle.Event.ON_RESUME)' in esim,
'Existing booking API retained': 'bookingStore.loadESIMs(session.id)' in esim,
'Official Android eUICC management, no fake LPA intent': 'EuiccManager.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS' in esim and 'Uri.parse(payload)' not in esim,
'Android eUICC availability checked': 'euicc?.isEnabled == true' in esim,
'QR and manual credentials retained': 'ESIMQRCode(payload)' in esim and 'CopyRow(context, "SM-DP+"' in esim,
'Localized eSIM installation help in all 4 supported languages': esim.count('"settings_guidance" to') == 4 and esim.count('"esim_unsupported" to') == 4,
'Validity date rendered using selected locale': 'DateTimeFormatter.ofPattern("d MMM yyyy", language.locale)' in esim,
'Android 13+ requests notifications before test': 'permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)' in planning,
'No test alarm success when permission absent': 'ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED' in planning,
'Existing Umrah Plan alarms still scheduled': 'PlanScheduler.schedulePreview(context, previewTrip)' in planning and 'PlanScheduler.reschedule(context,t)' in planning,
'Signal screen uses actual deployed parity route': 'AppRoute.Notifications -> AccountSignalsParityScreen(' in shell,
'Signal destination icons match iOS semantics': 'signalDestinationIcon(n.destination)' in signal and 'CupertinoSymbol.BuildingFill' in signal,
'Signal refresh gesture matches iOS': 'PullToRefreshBox(' in signal and 'notifications.refresh(accountStore.bearerToken)' in signal,
'Signal Telegram tab uses original iOS artwork': 'painterResource(R.drawable.iumrah_telegram_mark)' in signal,
'Signal timestamps are formatted': 'signalTimestamp(language, n.sentAt ?: n.createdAt)' in signal,
'Signal hide-on-Home remains functional': 'notifications.restoreToHome(n.id)' in signal and 'notifications.dismissFromHome(n.id)' in signal,
'Opened signals are marked read via backend': 'notifications.markOpened(n, accountStore.bearerToken)' in signal,
}
for name,passed in checks.items():
 assert passed, 'BLOCK 8 FAIL: '+name
 print('PASS:',name)
print(f'Block 8: {len(checks)} source guards passed; device build and pixel QA remain open.')
