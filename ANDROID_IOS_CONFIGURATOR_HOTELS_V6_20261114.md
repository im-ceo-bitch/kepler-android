# V6 — configurator and hotels

Reference: kepler-ios-main 3.zip, Sources/Views/Hotels/NuiteeHotelsCatalogView.swift, NuiteeJourneyHotelsView.swift, Sources/Views/Package/IumrahNuiteeJourneyFlow.swift, FinalPackageView.swift, IumrahManualPackageView.swift. Base: cumulative V5 + original Android repository.

- Flight-first Nuitee journey uses the existing authoritative package engine `/api/package/quote` and checkout; passes room/tariff IDs, stay policy and meal flags to quote and booking. Rejects incomplete, unverified, mismatched or unsigned quotes. Reprices before booking and requires user confirmation if price changes.
- Existing legacy flow retained; new flight selections invalidate obsolete Nuitee drafts. Exact stay windows and guest allocation verified; Hotel First stay order follows iOS. Room changes clear quote; selected rates must be valid. No price-pending selection in current guided flow.
- Meal choices, included meals and tier mapping from iOS; review pagination and hotel date labels. V4 daily disk/memory cache retained. Long quote requests have a bounded 120-second timeout.
- Manual estimate API DTO and client added, but standalone manual estimate screen is not wired. Mixed published/Aviasales independent flight pairs are not yet supported. Full screenshot parity, real backend booking, Compose compilation, device and APK verification remain open.
- 69 JVM rule assertions (22 new), 39 V4 cache checks, 18 source contracts; domain and transport API types checked using Kotlin compiler with minimal serialization/transport stubs. This is not an Android Gradle build. Existing workflows unchanged.
