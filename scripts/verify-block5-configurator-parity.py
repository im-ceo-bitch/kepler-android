#!/usr/bin/env python3
"""Block 5: source-driven regression contracts for published-ticket selection.

This is deliberately NOT an APK/visual device test. Its inputs are the original
SwiftUI reference and the Android-only implementation, and it checks the
specific feature that had been missing in the Android generator.
"""
from pathlib import Path
import re

root=Path(__file__).resolve().parent.parent
screen=(root/'app/src/main/java/com/iumrah/beta/ui/trip/TripBuilderScreen.kt').read_text()
roundtrip=(root/'app/src/main/java/com/iumrah/beta/ui/trip/CuratedRoundTripFlights.kt').read_text()
store=(root/'app/src/main/java/com/iumrah/beta/domain/journey/JourneyStore.kt').read_text()
models=(root/'app/src/main/java/com/iumrah/beta/models/flight/CuratedFlightRecommendationModels.kt').read_text()
service=(root/'app/src/main/java/com/iumrah/beta/data/flight/CuratedFlightRecommendationService.kt').read_text()

def require(expr, msg):
    if not expr: raise AssertionError(msg)

# Inventory: retain the two API endpoints and the exact published ID semantics.
for endpoint in ('/api/package/flights/recommendations','/api/package/storefront/flights'):
    require(endpoint in service, f'Backend publication source unexpectedly changed: {endpoint}')
for field in ('completeID','outboundID','returnID'):
    require(field in models and field in store and field in screen, f'No published {field}')
require('roundTripFlights = recommendations.filter' in screen, 'Missing complete-offer inventory')
require('recommendation.inbound ?: return@filter false' in screen, 'Unpaired one-way flights must not become complete offers')
for token in ('recommendation.outbound.origin.equals(draft.originCode, true)',
              'recommendation.outbound.destination.equals(draft.outboundDestinationCode, true)',
              'inbound.origin.equals(draft.returnOriginCode, true)',
              'inbound.destination.equals(draft.originCode, true)',
              '!inboundDate.isBefore(outboundDate)'):
    require(token in screen, f'Published round trip does not enforce route or chronology: {token}')
require('roundTrips.isNotEmpty()' in screen and 'CuratedRoundTripRow(' in screen, 'Round trips not rendered')
require('CuratedFlightTypeSelector(' in screen and 'curatedDisplayMode' in screen, 'Selection tab not wired')
require('selectedCompleteID = recommendation.id' in screen, 'Full ID is not saved')
require('selectedOutboundID = null' in screen and 'selectedReturnID = null' in screen, 'Staged one-ways are not cleared by full selection')
require('selectedPublishedCompleteID = completeID,' in store, 'Stale complete ID may survive a new selection')
require('selectedPublishedOutboundID = outboundID,' in store, 'Stale outbound ID may survive a new selection')
require('selectedPublishedReturnID = returnID,' in store, 'Stale return ID may survive a new selection')
require('selectedPublishedCompleteID = completeID ?:' not in store, 'Implicit fallback to stale complete selection')
for number in ('292.dp','204.dp','22.dp','44.dp','25.dp','15.dp'):
    require(number in roundtrip, f'Missing curated iOS card geometry {number}')
require('AppLanguage.UZBEK_CYRILLIC' in roundtrip, 'Missing 4th locale')
require('canContinue = draft.canContinue' in screen, 'Builder continuation guard removed')
require('journey.commitTripBuilder(draft, path, selectedCompleteID, selectedOutboundID, selectedReturnID)' in screen, 'Builder does not persist the full published selection')
require('generator.generate(' not in screen, 'First step should not synthesize a quote')

# Screen process keeps original product configuration stages, not just airfares.
for token in ('GeneratorStage.TRIP','StepperRow(', 'GroupSavingsSummaryCard(', 'TierCard(', 'FlightDateCalendarDialog(', 'chrome.openHotelSelection()'):
    require(token in screen, f'Configurator flow damaged: {token}')
print('Block 5 source contract: published round-trip / separate legs, state clearing, card geometry and downstream flow OK.')

# Transfer map: iOS renders MapKit rather than an abstract Canvas road drawing.
transfer=(root/'app/src/main/java/com/iumrah/beta/ui/packageflow/TransferSelectionScreen.kt').read_text()
transfer_map=(root/'app/src/main/java/com/iumrah/beta/ui/packageflow/TransferSearchLiveMap.kt').read_text()
require('TransferSearchLiveMap(second, duration, language)' in transfer, 'Transfer screen still uses artificial roads')
require('fun TransferMapCanvas(' not in transfer, 'Procedural map still present')
for token in ('rememberIumrahMapController()','IumrahMapLibreView(', 'IumrahMapStyle.STANDARD', 'CameraUpdateFactory',
              'makkahCenter = LatLng(21.4225, 39.8262)', '650.0 + 5900.0', 'transferCandidates', 'MapLibreMap.OnCameraMoveListener'):
    require(token in transfer_map, f'Missing native MapKit-parity behavior: {token}')
require(transfer_map.count('Candidate("') == 9, 'Transfer search must match nine iOS map candidates')
print('Block 5 native map: MapLibre, nine iOS candidate trajectories and physical search radius verified.')
