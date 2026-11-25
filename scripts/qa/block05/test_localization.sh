#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
Q=scripts/qa/block05
K=app/src/main/java/com/iumrah/beta/core/localization
kotlinc "$K/L10n.kt" "$K/IOSLocalizationDelta.kt" "$K/IumrahInternationalTranslations.kt" "$Q/LanguageStub.kt" "$Q/LocalizationSmoke.kt" -include-runtime -d "$T/localization.jar"
java -jar "$T/localization.jar" "$Q/localization.tsv"
