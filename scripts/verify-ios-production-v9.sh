#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
KOTLIN_HOME="$(cd "$(dirname "$(readlink -f "$(command -v kotlinc)")")/.." && pwd)"
CP="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
S=app/src/main/java/com/iumrah/beta/core/network
kotlinc -cp "$CP" "$S/HttpCallAwait.kt" "$S/ResponseBounds.kt" scripts/qa/v9-network/*.kt -d "$TMP/qa.jar"
kotlin -cp "$CP:$TMP/qa.jar" TestKt
