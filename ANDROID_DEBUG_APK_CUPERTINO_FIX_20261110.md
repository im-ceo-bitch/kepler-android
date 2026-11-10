# iumrah Android — Debug APK bootstrap fix (10 November 2026)

## Evidence from user-provided GitHub Actions log

The SDK installation now succeeds. The failure is in the project's Gradle task:

```
> Task :app:bootstrapCupertinoIcons FAILED
... scripts/install-cupertino-assets.py line 98 ...
EOFError: Compressed file ended before the end-of-stream marker was reached
```

**Root cause:** `request()` unconditionally read only `2_000_000` bytes of every HTTP download, including `cupertino_icons-1.0.9.tar.gz`, so the script passed a truncated archive into `tarfile.open`. The CLI reported EOF while enumerating tar members. This failure occurs **before** Kotlin/Compose compilation.

## Changes

1. `scripts/install-cupertino-assets.py`:
   - Reads complete network responses, with a defensive 32-MiB maximum instead of the erroneous 2-MB truncation.
   - Checks advertised Content-Length when present (also catches prematurely closed HTTP responses).
   - Retries transient download errors up to three times.
   - Retries the **entire icon archive** if gzip/tar decoding detects a truncated/corrupt response.
   - Verifies the expected font signature, font size, and included license before installing generated assets.
   - Retains pinned package/version, native Compose rendering, and MIT license handling. **No binary fonts are included in this ZIP.**
2. `scripts/tests/test_install_cupertino_assets.py`:
   - Six offline regression tests for the original truncation, retries and malformed responses.

## Verification conducted

- Python syntax compile: passed.
- Six mocked-network unit tests: passed.
- `python3 scripts/verify-ios-parity-stage10.py` on merged Android tree: passed.
- `bash scripts/verify-stage10.sh` on merged Android tree: passed.
- Cumulative ZIP integrity and carry-forward checks: passed.
- **Not verified:** remote Android Gradle compilation / APK packaging / device runtime. Those require the next GitHub Actions run.

## Installation

1. Upload **only** `iumrah-android-update-20261110-cumulative-stage01-stage10-apkfix01.zip` to the root of `kepler-android` GitHub repo. Previous stage ZIPs do not need to be uploaded again.
2. Commit/push and allow the existing ZIP unpacker to finish.
3. Open `Actions` -> `iumrah APK Test Build` -> `Run workflow` on `main`.
4. Check that `bootstrapCupertinoIcons` succeeds; after that Kotlin compile/test and APK packaging will run. Download `app-debug.apk` from build Artifacts if successful.

**Do not replace `.github/workflows/iumrah-apk-test.yml`** for this failure. This patch changes project source only.

If the build subsequently fails at `compileDebugKotlin` or another stage, collect the new log; those errors were masked by the earlier bootstrap failure and are not evidence that this bootstrap fix failed.
