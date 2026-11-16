# Pixel Perfect 03D1 — guided flights and hotel selection

Reference: `kepler-ios-main 6.zip`, specifically `IumrahGuidedFlightSelectionView.swift`, `IumrahPremiumFlightDetail.swift`, `NuiteeJourneyHotelsView.swift`, `NuiteeHotelsCatalogView.swift`, `HotelDetailView.swift`. Base Android: `kepler-android-main 2.zip` with cumulative 03ABC.

## Changes
- Flight cards have a separate 44dp selection target; tapping the card opens detail. The pending selection enables the fixed confirmation action; confirmed outbound advances immediately to return, confirmed compatible return proceeds to hotel selection. Scrolling resets between stages, headings and route switch to current direction. Displayed times use the departure/arrival airport timezone.
- Guided detail uses the existing verified flight-segment presentation (no guessed segments), includes a fresh Aviasales search for the selected route/date/passengers. Removed flight detail help action per earlier Android product instruction to keep help on hotel pages; current iOS still has a flight help action. This is an intentional divergence to resolve at final parity acceptance.
- Hotel detail gains a working help action. Hotel selection gets a collapsible occupancy card and graphite heading. Choosing a different hotel in the same city clears incompatible rooms and pending offers, retaining other cities; booking validation rejects mixed hotel IDs in one city. Advancing prefers incomplete rooms in the current city.
- The local room journey opens its existing transfer confirmation sheet automatically on first review if transfer is not yet confirmed. This preserves Android's quote boundary, rather than sending the local-room flow through the non-local package generator. iOS has a separate transfer screen: still a visual navigation difference.
- Supplier indicative prices are identified as indicative, not guaranteed rates; indicative cancellation/arrival charges are not presented as live terms. Sandbox flag remains in data; no backend changes or live reservation verification.

## Validation
- Kotlin PSI syntax: 223 files, zero syntax errors (not Android/Compose compilation).
- Stage10 Kotlin JVM rules: 71 assertions passed, including rejection of multiple hotels within the same city.
- Smart/JourneyStore mocked-network JVM tests: 78 assertions passed, including switching hotels without losing other city selections and invalidating the quote.
- Block 03C source contract script passed.

## Remaining within 03D
Current SwiftUI guided-flight filter/search editor, calendar and price graph parity; actual Nuitee server pagination contract and hotel detail full-screen navigation, dedicated transfer flow, exact UI layout/animation screenshots. 03D is not certified complete; continue 03D2 before 04A. No GitHub workflow or backend changes. Android SDK/Gradle/device build unavailable in this environment. Full visual equivalence requires device comparison in block 06.
