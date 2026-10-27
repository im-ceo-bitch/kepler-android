#!/usr/bin/env python3
"""Source-level guard for Signal reminders, excluding Umrah Flow by design."""
from pathlib import Path
root=Path(__file__).resolve().parent.parent
plan=(root/'app/src/main/java/com/iumrah/beta/ui/planning/UmrahPlanScreen.kt').read_text()
signal=(root/'app/src/main/java/com/iumrah/beta/ui/account/AccountSecondaryParity.kt').read_text()
start=signal.index('fun AccountSignalsParityScreen(')
reminders=signal[signal.index('            1 -> {',start):signal.index('            else -> {',start)]
checks={
 'Signal reminders has inline plan center, not a mere redirect': 'UmrahPlanReminderCenter(language)' in reminders and 'ApCard(radius = 26.dp)' not in reminders,
 'The center uses the persisted plan instead of placeholder data': 'val store = remember(context) { PlanStore(context) }' in plan and 'val trip = store.trip' in plan,
 'The center refetches saved plan after a background change': 'LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { store.reload() }' in plan,
 'The empty state has the iOS trip creation entry': 'fun UmrahPlanReminderCenter(' in plan and 'StackedPlanIcon()' in plan[plan.index('fun UmrahPlanReminderCenter('):],
 'The selected trip card follows iOS 286dp and radius 32': 'height(286.dp).clip(RoundedCornerShape(32.dp))' in plan,
 'The center previews at most 4 scheduled days': 'trip.reminderDays.sortedDescending().take(4)' in plan,
 'The center includes time and interactive reminder controls': '"%02d:%02d".format(trip.hour, trip.minute)' in plan and 'ReminderEditor(language, trip,' in plan,
 'All edits still persist to the same plan store and scheduler': 'store.save(it)' in plan and 'PlanScheduler.reschedule(context,t)' in plan,
 'Reminder settings still requests runtime Android 13 notifications permission': 'permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)' in plan,
 'Existing full Umrah Plan route remains accessible': 'chrome.openUmrahPlan()' in reminders,
 'Ritual Umrah Flow imports or stages not introduced': 'UmrahFlow' not in reminders,
}
for label, ok in checks.items():
    assert ok, 'FAIL: '+label
    print('PASS:',label)
print('Reminder-center source checks:',len(checks))
