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
# Trip Summary Design 13 supersedes the overlapping -46 dp legacy panel.
# Preserve the fixed safe-area footer, but validate the actual iOS-6 photo hero.
assert 'navigationBarsPadding()' in chrome
assert 'ReviewSummaryHero(' in chrome and 'R.drawable.about_iumrah_kaaba_corner' in chrome
assert '.height(168.dp)' in chrome and 'RoundedCornerShape(24.dp)' in chrome
hero = chrome.split('private fun ReviewSummaryHero(', 1)[1].split('private fun ReviewHeroMetric(', 1)[0]
assert 'TripStayPlanner.breakdown(trip)' in hero and 'quote?.stayPlan?.takeIf { state.hasFinalGeneratorQuote }' in hero
assert 'quote?.takeIf { state.hasFinalGeneratorQuote && it.hasVerifiedBreakdown }?.tripSummary' in hero
assert 'verified?.let {smartMoney(it.totalUsd,"USD",l)}' in hero
assert 'verified?.let {smartMoney(it.perPilgrimUsd,"USD",l)}' in hero
assert 'quote.pricePerPerson' not in hero and 'it.pricePerPerson' not in hero
assert 'ReviewUnitPrice' in legacy and 'ReviewUnitPrice' in local
assert 'ReviewFareDisplay.flight' in legacy
print('Block 03C source contracts passed: shared review hierarchy, sticky footer, room/meal edits, quote safety and unit prices.')
