#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
kotlinc app/src/main/java/com/iumrah/beta/core/config/AppConfig.kt scripts/qa/UnifiedBackendJvmRegression.kt -d "$TMP/qa.jar"
kotlin -cp "$TMP/qa.jar" com.iumrah.beta.core.config.UnifiedBackendJvmRegression
python3 scripts/verify-unified-backend.py
