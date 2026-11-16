#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
kotlinc app/src/main/java/com/iumrah/beta/domain/orbit/OrbitV5Geometry.kt scripts/qa/sync03/OrbitGeometryTest.kt -d "$TMP/orbit.jar"
kotlin -cp "$TMP/orbit.jar" OrbitGeometryTestKt
