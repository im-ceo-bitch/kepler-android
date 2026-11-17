# iumrah Android — Google Play release

Production identity: `com.iumrah.app`  
Default version name: `2.0.3`  
Target/compile SDK: `36`  
Minimum SDK: `26`

## One-time GitHub setup

The repository's automatic update ZIP extractor intentionally does not overwrite `.github/workflows/`. After applying cumulative v35 (includes v34), run:

```bash
bash scripts/install-play-release-workflow.sh
git add .github/workflows/iumrah-play-release.yml
git commit -m "Add iumrah Play release workflow"
git push
```

Configure these GitHub Actions secrets:

- `PLAY_UPLOAD_KEYSTORE_BASE64` — the existing Google Play upload keystore, Base64 encoded. Legacy `IUMRAH_UPLOAD_KEYSTORE_BASE64` is also accepted.
- `PLAY_STORE_PASSWORD` (or legacy `IUMRAH_UPLOAD_STORE_PASSWORD`)
- `PLAY_KEY_ALIAS` (or legacy `IUMRAH_UPLOAD_KEY_ALIAS`)
- `PLAY_KEY_PASSWORD` (or legacy `IUMRAH_UPLOAD_KEY_PASSWORD`)
Add repository Variable `PLAY_UPLOAD_CERT_SHA256` with the SHA-256 fingerprint of the existing Play Console **Upload key certificate**. The release build stops if the restored keystore does not match it.

Optional only, if Android remote push is desired (not required to build or sign the AAB):

- `IUMRAH_FIREBASE_API_KEY`
- `IUMRAH_FIREBASE_APP_ID`
- `IUMRAH_FIREBASE_PROJECT_ID`
- `IUMRAH_FIREBASE_SENDER_ID`

Do not create a different signing key for an update unless Play Console explicitly resets the upload key. The new bundle must keep package `com.iumrah.app`, use a higher `versionCode`, and be signed with the accepted upload key.

## Build

Run **Actions → iumrah Play Release AAB → Run workflow** and enter a version code higher than every build already uploaded to Play Console. The workflow runs the full Stage 17 audit, resolves `releaseRuntimeClasspath`, runs `dependencies` and `dependencyInsight` for both `billing` and `billingclient`, then executes clean, unit tests, `lintRelease`, `assembleRelease` and `bundleRelease`. It verifies the existing upload-key fingerprint, merged release manifest, 16 KB native libraries and the decompressed final AAB for Billing SDK markers. It produces the signed `.aab`, release APK, dependency audit logs, SHA-256 file and release metadata as GitHub artifacts.

For a local build with the same gates:

```bash
export PLAY_KEYSTORE_PATH=/absolute/path/to/upload.jks
export PLAY_STORE_PASSWORD='...'
export PLAY_KEY_ALIAS='...'
export PLAY_KEY_PASSWORD='...'
export PLAY_UPLOAD_CERT_SHA256='AA:BB:...from Play Console Upload key certificate...'
bash scripts/build-play-release.sh 15 2.0.3
```

## Play Console pre-publish checks

Before Production rollout, confirm: package identity and signing certificate accepted; versionCode higher than previous; App content/Data safety reflects coarse location, notifications, account/profile data, passport/receipt uploads and booking data actually collected by the product; Privacy Policy and Terms links resolve; no subscriptions or Play Billing products are declared for this build; internal/closed testing has covered login, booking, passport upload, payment receipt, push, flights, maps, Ziyarats and Care on at least one Android 8–9 class device and one current Android device.


## 2026-10-05 CI hotfix (v36)

The Play workflow uses `android-actions/setup-android@v4`. Do not downgrade it to v3: Google removed the legacy SDK package named `tools`, and v3 fails before Gradle with `Failed to find package 'tools'`.
