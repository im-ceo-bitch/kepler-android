# iumrah Android ⇄ iOS baseline — 12 October 2026

## Source of truth and scope
- iOS source: uploaded `kepler-ios-main 5.zip` (263 Swift files under Sources).
- Android source: uploaded recovered `kepler-android-recovered.zip` (132 Kotlin files before this patch).
- Objective: native Android UI/flow and backend-contract parity with the uploaded iOS source; do not redesign business logic or create separate backend data.
- New languages: **deferred** until the functional and visual parity gates are met. This patch does not add locales or alter the language menu.
- This file documents **source audit observations**. Presence of a similarly named Kotlin screen is not proof of runtime or pixel parity.

## Implementation checkpoint 01 (included in this ZIP)

### Ziyarats — iOS 2026-10-09 stability rebuild
- BEFORE: Android constructed MapLibre, animated pins and route layout during screen entry. An offline startup relied on a single bundled Madinah stop, no Makkah stops. iOS renders a lightweight list with an offline catalog first, constructs MapKit only when the map is requested, and has 6 Makkah + 5 Madinah offline places.
- NOW: Android entry point preserves `ZiyaratJourneyScreen` API while displaying offline-first lazy rows, city picker, details, pull-to-refresh, and a map opened only on explicit tap. Built-in stop IDs, titles, coordinates and four existing locales correspond to the iOS seed. Booking-facing Ziyarats also receives immediate offline places. Remote catalogues are vetted by city, ID, coordinates, image URLs/count, and text lengths. Network city changes cancel requests.
- Still needs device QA: layout comparison, MapLibre marker interaction, place images and Directions app handoff.

### Aviasales flight details — iOS 2026-10-09 ticket lookup repair
- BEFORE: Android CTA opened a possibly stale `bookingUrl`, and the UI named the internal supplier API. Missing transit segments could be misunderstood as complete flight details.
- NOW: Android builds a new route/date/passenger search link, using the supplier's calendar day in the same format as the iOS builder. Old cached booking URL is not used. The UI removes internal API jargon, explicitly marks arrival derived from aggregate journey duration as estimated, and shows complete flight segments only if an optional verified segment array passes validation. Segment continuity, chronological ordering, stop count, and layovers are checked; nothing is fabricated from the cached fare.
- Seller price/availability remains independent of the cached result; this does not introduce licensed real-time booking or automatic affiliate attribution.

## Source-level capability comparison (not certified pixel parity)
| Product area | iOS reference | Android corresponding source | Gate status |
|---|---|---|---|
| Main navigation / 5 tabs | `Sources/AppShell`, `Views/RootView` | `ui/shell`, `core/navigation` | Present; deep-route QA pending |
| Home / services | `Views/Home` | `ui/home`, `ui/homeflow` | Present; appearance/layout QA pending |
| Hotel storefront + details | `Views/Hotels`, `HotelPricing` | `ui/hotels`, `data/hotel` | Present; search/price QA pending |
| Airfares / calendar / discovery | `Views/Flights`, `FlightEngine`, `Services` | `ui/flights`, `data/flight` | Flight detail gap addressed; full search parity pending |
| Configurator and package pricing | `Views/Trip`, `Views/Package`, `State/JourneyStore` | `ui/trip`, `ui/packageflow`, `domain/pricing` | Present; quote parity test pending |
| Booking / checkout / passenger data | `Views/Booking` | `ui/booking`, `data/booking` | Present; complete status lifecycle QA pending |
| Care / chat / notifications | `Views/Chat`, `Services/ClientNotificationCenter` | `ui/care`, `ui/chat`, `ui/notifications` | Present; API/action parity QA pending |
| Ziyarats | `Ziyarats`, `Services/ZiyaratService` | `ui/ziyarats`, `data/ziyarat` | Rebuilt, manual device QA pending |
| Advisor / Umrah voice flow | `Umrah Flow` | `ui/advisor`, `data/advisor` | Present; full audio/state comparison pending |
| eSIM | `Views/ESIM` | `ui/esim` | Present; payment/QR QA pending |
| Account security / traveler / wallet | `AppShell/IumrahAccount*`, `Views/Wallet` | `ui/account` | Present; KYC and Wallet Pass platform-specific QA pending |
| Prayer Times standalone experience | `Sources/PrayerTimes` (4 Swift files) | Booking Travel Info prayer card, no standalone prayer module | **Not yet ported**; verify iOS navigation exposure before adding entry |
| Home-screen widgets | `WidgetExtension`, `Services/IumrahWidgetSyncService` | No Android App Widget provider found in uploaded source | **Unimplemented**; Android-native widget equivalent later |
| Localization expansion | iOS has additional `.lproj` resources | Android currently 4 app languages | **Intentionally postponed** |

## Required parity acceptance gates
1. Run Android `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintRelease` on GitHub Actions. Run release AAB workflow separately when appropriate. This environment lacks Android SDK/Gradle, so a full APK/AAB build **has not been verified** here.
2. Compare same API/account datasets on iPhone 11, iPhone 13 mini and Android Galaxy baseline. Capture screen-by-screen screenshots: fonts, states, spacing, navigation, sheets, scrolling, keyboard, safe areas, loaders, errors.
3. Test cold launch, offline and online transitions, city switching, request cancellation, data refresh, carousel details, and map dismissal. In Flights verify outbound, return, stopover and no segment-data cases; verify resulting Aviasales URL and passenger counts.
4. Exercise all booking states, manual payments/KYC, hotel pricing, package markup and server contracts. Confirm no regressions to Google Play workflows and 16 KB native-library checks.
5. Only after the above and remaining gaps are closed, start new localization work.

## Patch delivery contract
`iumrah-android-update-20261012-parity-stage01.zip` is a **root-relative patch**, not a complete repository and not a release APK. Upload the ZIP to repository root to use the existing update extraction workflow; GitHub compiles/tests require `workflow_dispatch` and must be monitored separately. No signing, Gradle, GitHub workflow, or localization files are part of this patch.
