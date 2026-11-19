#!/usr/bin/env python3
"""Source guards for the current iOS FinalPackageView hierarchy, not screenshot claims."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'app/src/main/java/com/iumrah/beta'
legacy=(root/'ui/packageflow/FinalPackageScreen.kt').read_text()
local=(root/'ui/packageflow/NuiteeJourneyReviewScreen.kt').read_text()
chrome=(root/'ui/packageflow/PackageReviewChrome.kt').read_text()
state=(root/'domain/journey/JourneyStore.kt').read_text()
for screen in (legacy,local):
    ordered=['ReviewHeader(', 'IumrahClientPackageMoment(language', 'ReviewHelp(', 'ReviewSection(reviewText(language,"Авиабилеты"', 'ReviewSection(reviewText(language,"Отели и номера"', 'ReviewServices(', 'ReviewBookingBar(']
    assert all(token in screen for token in ordered)
    assert [screen.index(token) for token in ordered]==sorted(screen.index(token) for token in ordered)
    assert 'PullToRefreshBox' in screen and 'Modifier.weight(1f)' in screen
    assert 'hasFinalGeneratorQuote' in screen and 'transferSelectionConfirmed' in screen
assert 'TierComparisonOption' not in legacy and 'buildTierComparisons' not in legacy
assert 'editor.replaceNuiteeRoom(room,room.copy(rate=rate,meals=meals))' in local and 'journey.updateNuiteeRoomMeals(room,meals)' in local
assert 'journey.beginReviewEdit()' in local and 'journey.commitReviewEdit(original,editor)' in local
assert 'journey.prepareNuiteePackage' in local and 'bookingAllowed == true' in local
assert 'accepted.totalPackagePrice.compareTo(fresh.totalPackagePrice)' in local
assert 'draft.rooms[expected.key] != expected' in state and 'room != expected' in state
assert 'quote = null, packageError = null' in state
assert 'navigationBarsPadding()' in chrome and 'Arrangement.spacedBy((-46).dp)' in chrome
assert 'ReviewUnitPrice' in legacy and 'ReviewUnitPrice' in local
assert 'ReviewFareDisplay.flight' in legacy
print('Block 03C source contracts passed: shared review hierarchy, sticky footer, room/meal edits, quote safety and unit prices.')
