#!/usr/bin/env python3
"""Static regression guard for the Home and navigation source contracts (not a render test)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
K = ROOT / 'app/src/main/java/com/iumrah/beta'
checks = {
    'ui/home/HomeScreen.kt': ['onHelp = { helpOpen = true }', 'IumrahHomeHelpCard(language) { helpOpen = false }', 'LocalOverscrollFactory provides null', 'delay(120_000)', 'homeListState.animateScrollToItem(0)', 'notificationState.home.take(5)', 'journey.startHomePublishedSearch(trip)'],
    'ui/components/IumrahRootPageHeader.kt': ['if (onHelp != null) 88.dp else 48.dp'],
    'ui/components/IumrahAnimatedSearchBorder.kt': ['5_000L', 'delay(42)', '.border(3.dp, Brush.sweepGradient(*stops), shape)'],
    'ui/home/IumrahHomeSearchHero.kt': ['420.dp else 330.dp', 'Arrangement.spacedBy(5.dp)', 'compactMonochrome = true', 'journey.startHomePublishedSearch(trip)'],
    'ui/home/IumrahHomeHelpCard.kt': ['maxHeight * .75f', 'alpha = .30f', 'top = 52.dp', 'padding(22.dp)', 'delay(18)', 'delay(2500)'],
    'ui/shell/SidebarDrawer.kt': ['(maxWidth * .74f).coerceAtMost(360.dp)', '(maxWidth * .78f).coerceAtMost(380.dp)', 'iosSpring(.34f, .88f)', 'R.drawable.iumrah_header_wordmark_dark', 'Modifier.width(160.dp).height(42.dp)', 'cornerRadius = 18.dp', '.height(52.dp)', 'cornerRadius = 28.dp'],
    'ui/onboarding/OnboardingFlow.kt': ['AppLanguage.entries.forEach', 'if (selected) 25f else 7f', 'maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), 20.dp) + 8.dp', 'maxOf(WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), 44.dp) + 12.dp'],
    'ui/home/HomePersonalizationSections.kt': ['fontSize = 31.sp', 'if (recent) 220.dp else 205.dp', 'if (recent) 184.dp else 202.dp'],
    'domain/journey/JourneyStore.kt': ['fun startHomePublishedSearch(value: TripDraft)', 'JourneyState(trip = value.copy(saudiArrivalDate = null,'],
}
for file, patterns in checks.items():
    source = (K/file).read_text()
    for pattern in patterns:
        assert pattern in source, f'{file}: missing {pattern}'
assert (K/'ui/shell/SidebarDrawer.kt').read_text().count('DrawerRow(CupertinoSymbol.') == 4
assert 'item { IumrahHomeHelpCard(language) }' not in (K/'ui/home/HomeScreen.kt').read_text()
print(f'Pixel block 02: {sum(map(len, checks.values()))+2} Home/navigation static contracts PASS')
