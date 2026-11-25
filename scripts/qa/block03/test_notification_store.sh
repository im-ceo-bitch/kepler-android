#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
S=app/src/main/java/com/iumrah/beta
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
KOTLIN_HOME="$(cd "$(dirname "$(readlink -f "$(command -v kotlinc)")")/.." && pwd)"
CP="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
# Compile real store code; only generated serializer is substituted for offline compilation.
sed 's/ClientSystemNotification.serializer()/ClientSystemNotification::class/g;s/json.decodeFromString(ListSerializer/json.decodeFromString<List<ClientSystemNotification>>(ListSerializer/' "$S/data/notification/ClientNotificationStore.kt" > "$TMP/Store.kt"
kotlinc -nowarn -cp "$CP" "$TMP/Store.kt" "$S/data/notification/NotificationScopeGate.kt" "$S/models/notification/NotificationModels.kt" scripts/qa/Stage10SerializationStubs.kt scripts/qa/v7-store/Flow.kt scripts/qa/block03/Notification*.kt -d "$TMP/store.jar"
kotlin -cp "$CP:$TMP/store.jar" NotificationStoreSmokeKt
