# iOS → Android: non-Home parity, increment 1

Reference: supplied `kepler-ios-main 5.zip`; base: `kepler-android-main 2.zip`.

## Implemented
- Smart: private JourneyStore retained through review, hotel editing and checkout; back restores the previous Smart step; verified quote required before review; no adoption into ordinary journey; navigation smoke coverage.
- Trip Summary: hotels grouped by city, compact room cards, room-level disclosure, occupancy, meals, refund terms, edit actions, verified totals preferred; supplier-only estimate explicitly labelled.
- Account security: verified login-phone changes, primary-device recovery via email/SMS, Apple authorization and provider-unlink API/store operations, owner/proof checks. Apple web authorization UI and provider unlink UI are not implemented.
- Booking: owner-authorized public-share create/revoke operations at service/store level, strict HTTPS public viewer URL policy. No new public-share UI added; source iOS service operations do not establish a matching UI.
- Home files and visual design tokens unchanged. Shell navigation changed for Smart only; no intended Home appearance changes.

## Verification
- P00/P02 JVM chrome smoke: PASS, including isolated Smart routes.
- V8 discovery navigation JVM regression: 19 checks PASS.
- Public share URI policy JVM: 11 cases PASS.
- Public trip merge source checks: PASS.
- Existing stage25 checks ran through final iOS source contract; public trip merge separately passed.
- Existing `scripts/tests/test-ios6-screen-regressions.py` still fails `Root viewport has lost safe status treatment`; already failing in base. No change to global status-bar geometry in this increment.
- No Gradle Android build, device screenshots, live API test or Xcode build: SDK/device environment unavailable. Static and JVM tests do not certify complete visual/function parity.

## Open scope
Remaining global visuals and transitions, full official/manual configurators, all screen/state-level visual comparison, hotels/flights, Care, account, onboarding, ritual flows, eSIM, notification delivery, localization and platform integration parity need further implementation/verification. No claim of 100% parity. Apple platform-specific effects require equivalent Android rendering, not Apple API copying.

Run `bash scripts/verify-nonhome-parity-20261125.sh` for new regression checks. Upload the update ZIP unchanged to repository root; existing workflow extracts it. Run Android CI manually afterward. No workflow changed.
