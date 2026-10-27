#!/usr/bin/env python3
"""Home source parity contract against iOS. Offline-safe; never mutates Swift source.

Not a substitute for Android compilation or on-device screenshot diff.
"""
from pathlib import Path
import argparse
import re

ROOT=Path(__file__).resolve().parent.parent
PKG=ROOT/'app/src/main/java/com/iumrah/beta'

def check(condition, description):
    if not condition:
        raise AssertionError(description)
    print('PASS',description)

def main():
    arg=argparse.ArgumentParser()
    arg.add_argument('--ios',type=Path,help='Read-only original iOS project or isolated patch-applied reference')
    a=arg.parse_args()
    home=(PKG/'ui/home/HomeScreen.kt').read_text()
    shell=(PKG/'ui/shell/AppShell.kt').read_text()
    nav=(PKG/'core/navigation/AppChromeStore.kt').read_text()
    weather=(PKG/'ui/home/HomeWeatherScreen.kt').read_text()
    panel=(PKG/'ui/booking/JourneyTravelInfoPanel.kt').read_text()

    ordering=[
        'item { HomeTripToolsSection(language, chrome) }',
        'item { EmotionalPrompt(',
        'item { HomeVideoCarousel(',
        'item { AudienceSection(',
        'item { BuildMyUmrahSection(',
        'item { ServicesSection(',
        'item { ReadyPackagesSection(',
        'item { HotelFirstPackagesSection(',
        'item { HomeIntegrationsSection(',
        'item { ProductsSection(',
        'item { ConfidenceStrip(',
        'item { PhilosophyCard(',
        'item { ConnectedTripCard(',
        'item { PersonalUmrahFAQ(',
        'item { AboutFooter(',
    ]
    check(all(x in home for x in ordering) and [home.index(x) for x in ordering]==sorted(home.index(x) for x in ordering),'Home sections preserve original SwiftUI marketing order')
    check('private fun HomeTripToolsSection' in home and 'Arrangement.spacedBy(13.dp)' in home,'Pilgrim tools block present with 13dp section spacing')
    check('modifier = modifier.height(98.dp)' in home and 'cornerRadius = 22.dp' in home and 'Modifier.height(28.dp)' in home and 'Modifier.height(10.dp)' in home,'Three tool tiles have iOS 98/22/28/10 geometry')
    check('fontSize = 11.sp' in home and 'textAlign = TextAlign.Center' in home, 'Tools text uses centered compact caption')
    for action in ('chrome.openPrayerTimes()', 'chrome.openHomeWeather()', 'chrome.openUmrahAdvisor()'):
        check(action in home,'Trip tool destination '+action)
    check('AppRoute.HomeWeather -> HomeWeatherScreen(language, chrome)' in shell and 'fun openHomeWeather()' in nav,'Weather tool routes to working screen')
    check('JourneyTravelInfoPanel(language, initialPage = 1)' in weather and 'initialPage: Int = 0' in panel,'Weather view opens real travel weather forecast, existing Booking keeps default prayer tab')
    check('HomeScreen(language, chrome, hotelCatalog, journey, notifications, accountStore.bearerToken)' in shell,'Home receives account token for notification-read API')
    check('coroutineScope.launch { store.markOpened(item, accountToken) }' in home,'Home open event marks notification as read')
    check('private val homeReadyCardWidth = 318.dp' in home and 'private val homeReadyCardHeight = 398.dp' in home,'Ready package carousel footprint 318x398dp matches iOS')
    check(home.count('Modifier.width(homeReadyCardWidth).height(homeReadyCardHeight)')==2,'Both ready flight package cards share 318x398 footprint')
    check('IumrahIconRole.UMRAH.color' in home,'Generated package role uses systemTeal palette instead of custom cyan')
    check(home.count('flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState)')>=6,'Home horizontal carousels snap to cards like iOS viewAligned')
    check('Modifier.width(width).height(448.dp).clip(shape)' in home and 'modifier = Modifier.width(width).height(448.dp), cornerRadius = 34.dp' in home, 'Integration cards use iOS 448dp height and 34dp rounded corners')
    check('Modifier.fillMaxWidth().height(210.dp).background(Color.White)' in home and 'Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp))' in home, 'Integration media 210 and CTA 48dp match iOS')
    check('if (cta == null) CupertinoSymbol.ClockFill else CupertinoSymbol.ArrowRight' in home, 'Disabled third integration retains iOS clock CTA')
    check('CupertinoSymbol.MoonStarsFill' in home and 'CupertinoSymbol.CloudSun' in home and 'CupertinoSymbol.SignalWave' in home,'Trip tool icons are official Cupertino glyphs')

    if a.ios:
        ios=a.ios/'Sources'
        source=(ios/'Views/Home/HomeDashboardView.swift').read_text()
        flight=(ios/'Views/Home/HomeAppleStoreSections.swift').read_text()
        check('tripToolsSection' in source and 'HomeEmotionalJourneyPrompt()' in source and source.index('tripToolsSection')<source.index('HomeEmotionalJourneyPrompt()'),'Original iOS Home has tools before emotional journey')
        check('.frame(height: 98)' in source and 'cornerRadius: 22' in source and 'HStack(spacing: 9)' in source,'iOS trip tiles geometry compared directly')
        check('showPrayerTimes = true' in source and 'showTravelInfo = true' in source and 'openGuideLanguages()' in source,'iOS tool navigation baseline inspected')
        check('.frame(width: 318, height: 398' in source and '.frame(height: 398' in flight,'iOS flight cards really use 318x398pt')
        check('markOpened(notification, accountToken: account.bearerToken)' in source,'Original iOS marks opened notification read')
        check('let cardHeight: CGFloat = 448' in source and '.frame(height: 210)' in source and '.frame(height: 48)' in source, 'iOS integration card 448/210/48 geometry verified')
        check('scrollTargetBehavior(.viewAligned' in flight,'iOS carousels are view-aligned')
    print('Block 3 Home: source parity guard passes; compilation and screenshot QA outstanding.')

if __name__=='__main__':main()
