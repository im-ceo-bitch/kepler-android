#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
bash scripts/verify-stage13.sh

PASS=app/src/main/java/com/iumrah/beta/ui/booking/PilgrimCheckoutScreen.kt
HOME=app/src/main/java/com/iumrah/beta/ui/booking/BookingsHomeScreen.kt
GUIDE=app/src/main/java/com/iumrah/beta/ui/booking/IumrahGuideTransferScreen.kt
NAV=app/src/main/java/com/iumrah/beta/core/navigation/AppChromeStore.kt
SHELL=app/src/main/java/com/iumrah/beta/ui/shell/AppShell.kt
STORE=app/src/main/java/com/iumrah/beta/data/booking/BookingStore.kt
MODELS=app/src/main/java/com/iumrah/beta/models/booking/BookingSharedModels.kt
TRANSFER=app/src/main/java/com/iumrah/beta/domain/trip/TransferModels.kt
CHAT=app/src/main/java/com/iumrah/beta/data/chat/ChatService.kt
PARITY=app/src/main/java/com/iumrah/beta/ui/booking/BookingParityComponents.kt

test -f "$GUIDE"
grep -q '"01"' "$PASS"
grep -q '"Attach passports"' "$PASS"
grep -q 'A clear photo of the passport information page is enough.' "$PASS"
! grep -q 'Fill in manually' "$PASS"
grep -q 'resourceName = "iumrah_security_identity"' "$PASS"
grep -q 'TravelDocumentsLockedCard' "$PASS"
grep -q 'GuideTransferCheckoutCard' "$PASS"
grep -q 'StageHeader("02", CupertinoSymbol.CreditCard' "$PASS"
grep -q 'StageHeader("03", CupertinoSymbol.Document' "$PASS"

! grep -q 'KYC · iumrah Security' "$HOME"
grep -q 'no KYC or long forms' "$HOME"
grep -q 'AssignedGuideStatusCard' "$HOME"
grep -q 'openBookingGuideTransfer' "$HOME"
grep -q 'Color(0xFFF2F2F7)' "$HOME"
grep -q 'Color(0xFFF2F2F7)' "$PARITY"

grep -q 'data class BookingGuideTransfer' "$NAV"
grep -q 'IumrahGuideTransferScreen' "$SHELL"
grep -q 'loadTeamProfiles' "$CHAT"
grep -q '/api/catalog/hotels/team' "$CHAT"
grep -q 'var transferVehicle: TransferVehicleKind? = null' "$MODELS"
grep -q 'transferVehicle = journey.resolvedTransferVehicle' "$STORE"
grep -q '@Serializable' "$TRANSFER"
grep -q '@SerialName("carnival")' "$TRANSFER"

grep -q 'Founder · iumrah' "$GUIDE"
grep -q 'Phone and Telegram unlock after payment is confirmed' "$GUIDE"
grep -q 'transfer_carnival' "$GUIDE"
grep -q 'transfer_malibu' "$GUIDE"
grep -q 'transfer_yukon' "$GUIDE"
grep -q 'Asia/Riyadh' "$GUIDE"
grep -q 'Face photo for airport pickup recognition' "$GUIDE"

echo "Stage 014 passport / status / verified guide + transfer parity checks passed."
