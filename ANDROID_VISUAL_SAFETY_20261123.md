# iumrah Android — iOS6 Visual Safety / 2026-11-23

Cumulative patch: includes the November 20 iOS6 build fix and all preceding changes.

Source corrections:
- Root tab content is clipped within Android status-bar safe-area while the top system area stays signature black with legible white icons.
- Uzbek navigation labels shrink instead of showing truncated text on narrow devices.
- Account guest content matches iOS6 order: profile, help, booking access, settings, slogan. Redundant recovery/analytics row removed; settings icons use semantic iOS colors.
- Nuitee hotels no longer show permanent 'Loading price' when the live-rate request was skipped (sandbox/invalid input) or later batches were abandoned after rate limiting. Prices are never invented.

Verification: `bash scripts/verify-stage25.sh` and `python scripts/tests/test-ios6-screen-regressions.py` passed in local source tree. This does NOT prove APK/AAB compilation, screenshots/pixel parity, or live backend rates.

The archive contains only changed/previously patched files. Preserve all other repository files; extract at repository root.
