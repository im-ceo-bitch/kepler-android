#!/usr/bin/env bash
set -euo pipefail
bash scripts/verify-stage7.sh
required=(
 ANDROID_STAGE_008_CARE_NOTIFICATIONS_APPLIED.txt
 app/src/main/java/com/iumrah/beta/data/chat/ChatService.kt
 app/src/main/java/com/iumrah/beta/data/notification/ClientNotificationStore.kt
 app/src/main/java/com/iumrah/beta/models/notification/NotificationModels.kt
 app/src/main/java/com/iumrah/beta/ui/care/CareHomeScreen.kt
 app/src/main/java/com/iumrah/beta/ui/chat/BookingChatScreen.kt
 app/src/main/java/com/iumrah/beta/ui/notifications/NotificationsScreen.kt
)
for f in "${required[@]}"; do test -s "$f" || { echo "STOP: missing Stage 008 file $f"; exit 1; }; done
grep -q '/client/chats/\$bookingID/messages' app/src/main/java/com/iumrah/beta/data/chat/ChatService.kt
# Chat refresh is now push-aware: wait for a revision or poll after six seconds.
chat_screen=app/src/main/java/com/iumrah/beta/ui/chat/BookingChatScreen.kt
chat_service=app/src/main/java/com/iumrah/beta/data/chat/ChatService.kt
grep -Fq 'withTimeoutOrNull(6_000) { chatService.refreshRevision.first { it != seenRevision } }' "$chat_screen" || { echo 'STOP: chat must refresh on push or six-second timeout'; exit 1; }
grep -Fq 'chatService.loadChat(bookingID, headers)' "$chat_screen" || { echo 'STOP: chat must load messages on refresh'; exit 1; }
grep -Fq 'ChatDelta.path(bookingID, after, unread)' "$chat_service" || { echo 'STOP: chat must fetch incremental messages'; exit 1; }
grep -Fq 'chatService.cachedChat(bookingID, headers)' "$chat_screen" || { echo 'STOP: chat must restore scoped message cache'; exit 1; }
grep -q '/client/notifications/devices' app/src/main/java/com/iumrah/beta/data/notification/ClientNotificationStore.kt
grep -q 'pushState.deviceToken' app/src/main/java/com/iumrah/beta/ui/notifications/NotificationsScreen.kt || { echo 'STOP: notifications sync must use the real FCM token'; exit 1; }
if grep -q 'deviceToken = null' app/src/main/java/com/iumrah/beta/ui/notifications/NotificationsScreen.kt; then
 echo 'STOP: notification sync must not hard-code a null device token'; exit 1
fi
grep -q 'POST_NOTIFICATIONS' app/src/main/AndroidManifest.xml
# Security gate: reject actual credential/signing files committed inside the repository.
# Do not match arbitrary filenames merely because they contain the word "keystore".
if find . -type f \
  \( -iname 'google-services.json' \
     -o -iname 'key.properties' \
     -o -iname '*.jks' \
     -o -iname '*.keystore' \
     -o -iname '*.p12' \
     -o -iname '*.pfx' \) \
  -not -path './.git/*' | grep -q .; then
  echo 'STOP: credentials/signing material must not be shipped in update'; exit 1
fi
grep -q 'packageEngine.packageQuote(state)' app/src/main/java/com/iumrah/beta/domain/pricing/PackageGenerator.kt

# Stable API 36 dependency matrix: keep libraries below their API 37 compileSdk cutovers.
grep -q 'compileSdk = 36' app/build.gradle.kts
grep -q 'targetSdk = 36' app/build.gradle.kts
grep -q 'androidx.compose:compose-bom:2026.06.01' app/build.gradle.kts
grep -q 'androidx.core:core-ktx:1.17.0' app/build.gradle.kts
grep -q 'io.coil-kt.coil3:coil-compose:3.5.0' app/build.gradle.kts
grep -q 'io.coil-kt.coil3:coil-network-okhttp:3.5.0' app/build.gradle.kts
test -s ANDROID_BUILD_HOTFIX_009_STABLE_API36_DEPS_APPLIED.txt

echo 'Stage 008 care/notification + stable API 36 dependency checks passed.'
