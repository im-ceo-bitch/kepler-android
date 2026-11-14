# Android — unified iumrah-backend client migration

Base: `kepler-android-main.zip` + cumulative `stage01-stage10-apkfix01`.
This is an Android-only cumulative patch, not a Cloudflare Worker deployment or D1 migration.

## Public ingress and route contract

The intended canonical public ingress for `iumrah-backend` is `https://iumrah.app` (same public origin as the other clients). `AppConfig.API_BASE_URL` deliberately stays on that domain. The app must not select a second Worker or a database directly. Existing `/api/package/*`, `/api/catalog/*`, `/api/bookings`, `/api/storefront/*` and `/api/airports` contracts are preserved; the Worker must implement these compatibility routes against the unified databases. Prefixes alone do not establish which Worker/database serves the request.

- `APIClient` resolves all business requests via `AppConfig.apiUrl`, canonicalizes absolute first-party API URLs including the current worker hostname, rejects foreign API hosts and unsafe paths, and disables automatic redirects to avoid forwarding booking/account tokens. Public images and links remain separate. Protected document/chat media must be served at a first-party `/api/` route; external private-media redirects are deliberately rejected and require a server-side same-origin route.
- Analytics now posts to the same origin via `AppConfig.INTELLIGENCE_EVENTS_URL`, with redirect following disabled; existing privacy and pending event queue preserved.
- Connectivity probes GET `/api/package/health` and requires a successful HTTP response containing `ok: true`, not just a reachable website.
- Removed legacy Supabase REST translation/audio-table requests and embedded anon credential from `UmrahFlowService`. Offline texts and cached public audio mapping remain; English text fallback when needed. Text updates require an app update until a unified guide catalog endpoint exists.
- Account and booking IDs remain server-assigned. Existing 4-digit booking and 8-digit pilgrim display formatting, activation/registration paths and token handling remain intact; no database/ID generation added to Android.

## Boundaries / server-side work still required

The public health endpoint returned `ok: true` and configured hotels/bookings DB flags during the check. It still identifies itself as `iumrah-package-engine`; this is not proof of a particular Worker/D1 binding or migration of historical data. Direct `workers.dev` health was not verifiable in this environment. Verify the Cloudflare routing of each legacy prefix in `lattice-mobile` before declaring the production migration complete. The latest deployed unified Worker source and actual D1 data are not present in the supplied Android/iOS archives. No changes made to server, Cloudflare, old accounts, tokens, or database contents.

The reviewed offline catalog references 180 public MP3 URLs in the existing Supabase Storage. They are **not** DB queries, but must still be copied into unified R2/storage and served by the Worker to fully retire Supabase. They remain temporarily usable rather than replacing them with unverified routes. Public third-party maps, airline logos, weather, prayer times and CBU FX also remain external; they do not access iumrah account or booking data. If all external integrations must be proxied server-side, implement and verify those contracts in the backend first. Do not remove the old audio storage before media migration.

## Verified locally

- 50 JVM checks compiled/executed against production `AppConfig.kt`: canonical origins, route/query preservation, rejection of hostile/unsafe URLs, health/analytics URLs, public media behavior.
- Static audit: 85 unique API path literals, common resolver, no direct Supabase REST calls or direct workers.dev API request literals in Kotlin source.
- Source/asset checks for stages 1–10 and existing `verify-stage10.sh` passed; stage 05/06 18 checks and 07/08 17 checks passed; previous stage 10 Kotlin 47 assertions passed. JUnit tests added (not executed in Gradle here).
- Full APK compilation / device testing / live authentication, booking creation, flight/hotel results and analytics ingestion not verified. No Android SDK/Gradle wrapper available here.

## Apply

Upload only `iumrah-android-update-20261113-cumulative-unified-backend-v1.zip` to `kepler-android` main and let the existing unpacker apply it. Workflows and signing files are unchanged. Then run the existing `iumrah APK Test Build`. No backend deploy is triggered or required by applying this client patch itself; an independently deployed compatible Worker is a prerequisite for production end-to-end operation.
