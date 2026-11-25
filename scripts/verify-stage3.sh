#!/usr/bin/env bash
set -euo pipefail

test -f scripts/verify-stage2.sh || { echo 'STOP: Stage 003 requires Stage 002 verifier; repair/update 003 prerequisites first.'; exit 2; }
bash scripts/verify-stage2.sh

test -f ANDROID_STAGE_003_SHELL_MOTION_APPLIED.txt
test -f app/src/main/java/com/iumrah/beta/core/design/IumrahMotion.kt
test -f app/src/main/java/com/iumrah/beta/core/localization/L10n.kt
test -f app/src/main/java/com/iumrah/beta/ui/onboarding/OnboardingFlow.kt
test -f app/src/main/java/com/iumrah/beta/ui/home/HomeScreen.kt
test -f app/src/main/java/com/iumrah/beta/ui/shell/AppShell.kt

grep -q 'const val PressedScale' app/src/main/java/com/iumrah/beta/core/design/IumrahMotion.kt
grep -q 'const val CardPressedScale' app/src/main/java/com/iumrah/beta/core/design/IumrahMotion.kt
grep -q 'val vapor' app/src/main/java/com/iumrah/beta/core/design/IumrahMotion.kt
# Foundation object specifiers include positional %1$@; retain literal %% and numeric format tokens.
grep -Fq 'Regex("%%|%(?:[0-9]+\\$)?@")' app/src/main/java/com/iumrah/beta/core/localization/L10n.kt
grep -Fq 'if (token.value == "%%") token.value else token.value.dropLast(1) + "s"' app/src/main/java/com/iumrah/beta/core/localization/L10n.kt
grep -q 'packageMarkupRate: BigDecimal = BigDecimal("0.50")' app/src/main/java/com/iumrah/beta/domain/pricing/LocalPackagePricingEngine.kt

echo 'Stage 003 shell/motion structural checks passed.'
