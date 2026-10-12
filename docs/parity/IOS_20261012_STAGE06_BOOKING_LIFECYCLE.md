# Android ↔ iOS booking lifecycle timer source parity (Stage 06)

A mismatch was found between the iOS BookingDetailView and Android BookingDetailScreen: when explicit lifecycle start fields are missing, Android used `createdAt`/`updatedAt` even when the server supplied a later timestamp for the matching status transition. The same mismatch affected the Android BookingsHome availability countdown.

Changes:
- Details availability: explicit deadline > startedAt > matching `availability_check`/`new` status-history transition > creation.
- Details price lock: explicit > startedAt > `payment_pending` transition > booking update.
- Details paid/documents: explicit > startedAt > `booking_confirmed`/`paid` transition > booking update.
- Blank `paymentReceivedAt` no longer hides the price-lock timer.
- BookingsHome availability now uses matching status transition fallback before creation.
- Existing server deadlines remain authoritative and no billing or pricing calculation changed.

Verification: source invariants and previous Stage 01–05 checks; full Android build and real-device countdown still pending.
