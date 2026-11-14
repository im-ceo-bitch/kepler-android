#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
kotlinc app/src/main/java/com/iumrah/beta/core/geometry/IumrahGlobeGeometry.kt scripts/qa/GlobeParityRegression.kt -d "$TMP/globe.jar"
kotlin -cp "$TMP/globe.jar" com.iumrah.beta.core.geometry.GlobeParityRegressionKt
