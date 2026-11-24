# IUMRAH · November 20, 2026 · cumulative update V2

CUMULATIVE: contains previous Public Trip + Configurators update AND this revision.
Install each ZIP at the repository root with the existing repository ZIP installer.

Web: adds website hotel/flights switch, 3 flow entrypoints, public trip viewer,
same-origin share API, AASA + Android assetlinks responder. Refreshes public
status every 90 seconds and displays airport-local flight schedules.

Beta iOS: adds owner-verified share link create/revoke and shared-trip links,
server-side whitelist response, direct D1 account session verification, and
Business V10 unified backend overlay deploy step and remote D1 migration.
The unified source Business V10 business-v10.tar.gz is NOT INCLUDED because it
was absent in the supplied iOS sources. Deploy will not succeed without the
canonical snapshot and its SHA256SUMS. Do not create a fake snapshot.

Android: native HTTPS deep link into read-only shared trip viewer. Source
is October 5 version, not verified up-to-date with November iOS. Needs real
Play signing certificate SHA-256 + matching environment variables for App Links.

Local unit/syntax tests pass. Full cloud deployment, iOS and Android builds,
and 1:1 functional/visual parity have NOT been verified.
See downloadable RELEASE_READINESS_RU.md for exact details.
