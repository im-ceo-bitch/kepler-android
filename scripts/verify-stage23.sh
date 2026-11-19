#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export IUMRAH_VERIFY_LATEST=1
bash scripts/verify-stage22.sh
ACCOUNT=app/src/main/java/com/iumrah/beta/ui/account/AccountScreens.kt
BOOKING=app/src/main/java/com/iumrah/beta/ui/booking/BookingsHomeScreen.kt
# Verify Compose observes the booking StateFlow, instead of reading .value in composition.
python3 - <<'PYV43'
from pathlib import Path
account=Path('app/src/main/java/com/iumrah/beta/ui/account/AccountScreens.kt').read_text()
booking=Path('app/src/main/java/com/iumrah/beta/ui/booking/BookingsHomeScreen.kt').read_text()
start=account.index('private fun GuestLoginCard(')
end=account.index('\n@Composable',start+1)
login=account[start:end]
assert 'val bookings by bookingStore.state.collectAsState()' in login, 'GuestLoginCard must subscribe to StateFlow'
assert 'bookings.sessions.isNotEmpty()' in login, 'GuestLoginCard must use observed sessions'
assert 'bookingStore.state.value' not in login, 'Unobserved StateFlow read in GuestLoginCard'
start=booking.index('    AnimatedContent(\n        targetState = activeSession?.id,')
end=booking.index('\n}\n\n@Composable',start)
animated=booking[start:end]
assert ') { bookingID ->' in animated, 'Animation target not used'
assert 'val displayedSession = bookingID?.let(bookingStore::booking)' in animated, 'Animation not tied to target ID'
assert 'if (displayedSession == null)' in animated and 'session = displayedSession' in animated
assert 'otherSessions = activeSessions.filterNot { it.id == displayedSession.id }' in animated
assert 'if (activeSession == null)' not in animated
print('Stage 023: remaining Compose Lint ERROR regression checks passed.')
PYV43
