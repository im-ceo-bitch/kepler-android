#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/verify-ios-parity-stage05.sh
python3 - <<'PYIN'
from pathlib import Path
r=Path('app/src/main/java/com/iumrah/beta/ui/booking')
d=(r/'BookingDetailScreen.kt').read_text()
h=(r/'BookingsHomeScreen.kt').read_text()
assert 'session.latestStatusTimestamp(setOf("availability_check", "new"))' in d
assert 'session.latestStatusTimestamp(setOf("payment_pending"))' in d
assert 'session.latestStatusTimestamp(setOf("booking_confirmed", "paid"))' in d
assert '!session.paymentReceivedAt.isNullOrBlank()' in d
assert 'transitionDate("availability_check", session)' in h
assert 'transitionDate("new", session)' in h
print('Stage06 booking lifecycle status-transition fallback checks passed.')
PYIN
