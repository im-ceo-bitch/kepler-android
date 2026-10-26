# iumrah · Block 1: Brand chrome · Source audit and acceptance

**Android-only cumulative source patch. Reference:** `kepler-ios-main 3.zip` with the separate original `iumrah_headers_and_world_dots_2in1_patch.zip` applied in an isolated **read-only reference copy**. Neither the original nor its owner repository is modified.

## Verified against the reference SwiftUI files

| Area | Android implementation | Verification |
| --- | --- | --- |
| Monochrome theme | `#090909`, original wordmark PNG, white text/controls | Source constants and original wordmark SHA-256 verified |
| World-dot geography | original 1600×800 PNG, 5,026 coordinates | Entire PNG byte-for-byte SHA-256 matches iOS |
| World-dot movement | Android 13+ AGSL RuntimeShader uses the Metal source's equations, constants, premultiplied source and second-based time | Formula and shader input contracts statically checked; needs GPU capture |
| Android 8–12 | same atlas, position, mask; gradient-motion fallback | **NOT pixel-identical** to Metal animation; platform limitation outstanding |
| Header width | equal 18dp bleed on left/right, without `requiredWidth` centering ambiguity | Explicit child measurement and `placeRelative(-18dp,0)` |
| Hotels shared surface | no double width expansion; one continuous map across header and segmented control | Exact one-bleed layout; static check |
| Header responsive presets | ViewThatFits analog: 340dp/297dp inner-width thresholds on Home, 243dp/202dp elsewhere | Checked for viewport 320, 360, 375, 390 and 414dp |
| Wordmark | original 42pt/42dp signature image frame, widths 132/105/84 | Static layout assertions |
| Buttons | 42/38/36dp; 17dp icon; fill white 0.105, border white 0.25 at 0.8dp | Static layout assertions |
| Unread indicator | red white-stroked 16dp counter, 26dp for `9+`, offset (+3,-4) | Source comparison and regression assertions |
| Connectivity indicator | black pill 34→88dp, white/gray rotating border, 1.55s linear orbit, 1.65/3.65/0.62s dwell, spring | Source timings and colors; needs motion-video comparison |
| Bottom navigation | five equal slots, white 0.96 bar, 5dp inner inset, 65dp tab, radius25, icon21, text10.5, horizontal16 | Source assertions |
| Hotels switch | monochrome 41dp pills, 7dp spacing, icons13dp | Previous storefront verifier |
| Behavior | header bell opens notifications; menu opens drawer; tab selects same Android routes | Source handlers untouched; interactive device test not yet completed |

## Quality gates

- `bash scripts/verify-stage25.sh` includes the new `verify-brand-block1-final.py` source-contract guard.
- Both archive files verified with `zipfile.testzip()` and a fresh reapply to the pristine 2026-10 Android ZIP.
- **Not passed / not executed here:** `gradle :app:assembleDebug`, `gradle :app:testDebugUnitTest`, on-device screenshots, GPU frame comparison, 60-second interaction and performance tests.

## Final release acceptance still requires device testing

1. Apply this **one cumulative update** to the initial Android repository, then run GitHub Actions > Android CI > **Run workflow**. Verify actual Android compilation and unit tests; attaching only the ZIP does **not** trigger the `workflow_dispatch` build job.
2. Capture identical states on a current iPhone and Android phone with equal **logical viewport widths**, light and dark, all four Android languages, and display-font scaling 100% and 130%.
3. Compare header boundaries, wordmark baseline, notification pill, connectivity timing and world-dots animation (at t=0, 1, 3, 5 seconds), selected/unselected tab bar, keyboard open, accessibility reduced-motion and no-network states.
4. On Android API 26–32, separately accept or replace the shader fallback. The present effect is expressly **not** a guaranteed frame match there.
5. Record and fix any mismatched screenshots / broken interactions before signing off **Block 1 as 100% visually closed**.

Do not interpret the static checks as a completed device-verified parity certificate. This distinction is intentional for release quality.
