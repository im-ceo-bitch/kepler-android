#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
kotlinc app/src/main/java/com/iumrah/beta/data/chat/ChatDelta.kt scripts/qa/ChatDeltaRegression.kt -d "$TMP/chat.jar"
kotlin -cp "$TMP/chat.jar" com.iumrah.beta.data.chat.ChatDeltaRegression
