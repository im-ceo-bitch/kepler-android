# iOS → Android parity · Stage 07 · Advisor (2026-10-12)

Source: `Sources/Umrah Flow/Services/{UmrahFlowStore,UmrahFlowAudioService,UmrahFlowSupabaseClient}.swift`, `Components/UmrahFlowNavigatorSheet.swift`, `UmrahFlowRootView.swift` from `kepler-ios-main 5.zip`.

Implemented in Android:
- Public Umrah Flow translation/audio URL snapshots persisted **per selected guide language**, read before network request, and retained if a request fails. No booking/account credentials or data are written to these caches.
- Translation and audio requests run concurrently; 12-second connection/read timeouts; no fake narrated words are invented; English copy is the last-resort fallback only when the requested language has no translation rows.
- Translation keys are allowlisted to the canonical Umrah Flow set; cached and downloaded audio URLs are restricted to well-formed HTTPS addresses.
- Playback button now toggles current narration; buffering, speaking and stopped states drive feedback; state is reset when playback completes/fails or the user leaves the stage.
- Navigator now separates selection from navigation: selecting the step does not change the current page until the user taps “Go”, matching iOS.
- Guide language set is distinct from application localization: no new application languages added.

Not yet at pixel/functional parity:
- iOS meters true voice amplitude from its AVAudioPlayer, while Android currently animates playback status, **not actual audio amplitude**; equivalent Media3 audio metering is still required.
- Screens, RTL layouts and remaining stages require screenshot/device tests; some copy uses fallback strings where the remote table has no translations.
- Verified by source-level checks and isolated network-response unit tests; **no Android SDK / full Gradle build in this environment**.
