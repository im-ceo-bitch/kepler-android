#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
KOTLIN_HOME="$(cd "$(dirname "$(readlink -f "$(command -v kotlinc)")")/.." && pwd)"
CP="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
S=app/src/main/java/com/iumrah/beta
# Use the actual production declarations, not a copied contract.
python3 - "$TMP/Models.kt" <<'PY'
from pathlib import Path
import sys
s=Path('app/src/main/java/com/iumrah/beta/models/booking/BookingSharedModels.kt').read_text()
a=s.index('@Serializable data class ChatListResponse'); b=s.index('@Serializable\ndata class BookingItineraryItem',a)
Path(sys.argv[1]).write_text('package com.iumrah.beta.models.booking\nimport kotlinx.serialization.Serializable\n'+s[a:b])
PY
kotlinc -nowarn -cp "$CP" "$S/data/chat/ChatDelta.kt" "$S/data/chat/ChatService.kt" "$S/domain/booking/BookingAuthFallbackPolicy.kt" scripts/qa/Stage10SerializationStubs.kt scripts/qa/v7-store/Flow.kt scripts/qa/nov16-chat/*.kt "$TMP/Models.kt" -d "$TMP/chat.jar"
kotlin -cp "$CP:$TMP/chat.jar" TestKt
kotlinc "$S/domain/booking/BookingAvailabilityEstimate.kt" scripts/qa/BookingAvailabilityEstimateRegression.kt -include-runtime -d "$TMP/estimate.jar"
java -jar "$TMP/estimate.jar"
bash scripts/verify-chat-delta.sh
