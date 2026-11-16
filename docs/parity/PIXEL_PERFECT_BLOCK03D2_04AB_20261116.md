# Pixel Perfect 03D2 / 04A / 04B — accelerated release candidate
Reference: kepler-ios-main 6.zip. Cumulative over 03ABCD1. Android source changes only; no backend, secrets, workflows, or Play version changes.

## 03D2 — Flight and hotel discovery
- Reset old results on a route/date/filter refresh; clear airline selections on route change. Apply direct-only locally as well as on the server.
- Disable dates before today and disallow confirming an inbound date before the outbound date. Graph follows direct filter and refreshes month when selected dates cross a month; ignores invalid/past fares, uses server currency, and follows iOS black CTA and selected-fare summary.
- Preserve the selected hotel package by ID through progressive data refresh, scope gallery/help to hotel, and replace text pseudo-icons in hotel room selection with Cupertino glyphs.

## 04A — Booking and payment
- Four booking sections as in iOS: booking status, documents, package, schedule. Use the existing protected checkout embedded for Documents and booking detail for Package. Selected trip and account scope govern checkout and itinerary; old animated card cannot show another trip's data. Schedule link returns to root schedule tab.
- Refresh payment checkout on lifecycle status changes, clear stale reward summary before evaluating a new authoritative quote, preserve existing verification of server amounts and receipt status. No payment provider changes.

## 04B — Care and account
- Root Care uses one authenticated account chat for active bookings, matching the SwiftUI account endpoint; account-only credential, authorization-scoped cache, account thread messages can span bookings, booking-specific thread remains filtered. Re-key chat UI on auth changes. Support hero follows iOS availability note; redundant action tiles removed. Pull refresh updates support contact profile.
- Account refresh also reloads profile. Both companion views key private data to account token and reject late responses on account change.

## Verification and release limits
- Kotlin PSI syntax checks, chat regression (including account thread and separate credential caches), availability and ChatDelta regression, Stage 1–10 structural checks. These are not Android compilation or live integration tests.
- Android SDK/Gradle and emulator are unavailable in this environment. Build APK/AAB on existing CI and smoke-test on device. Full screenshot comparison, onboarding and motion remain outstanding. The account chat uses the same endpoint as iOS; live API compatibility is not verified here. No changes to versionCode; use existing release workflow's unused version code for Play.
