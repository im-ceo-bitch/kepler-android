#!/usr/bin/env bash
# Test only: real BookingStore/models with mock API, vault, and serialization boundary.
set -euo pipefail
cd "$(dirname "$0")/.."
S=app/src/main/java/com/iumrah/beta
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
KOTLIN_HOME="$(cd "$(dirname "$(readlink -f "$(command -v kotlinc)")")/.." && pwd)"
CP="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
sed 's/ListSerializer(StoredBookingSession.serializer())/ListSerializer(StoredBookingSession::class)/' "$S/data/booking/BookingStore.kt" > "$TMP/BookingStore.kt"
kotlinc -cp "$CP" "$TMP/BookingStore.kt" scripts/qa/v7-store/*.kt scripts/qa/Stage10SerializationStubs.kt scripts/qa/sync02/Serializers.kt \
  "$S/models/booking/BookingSharedModels.kt" "$S/models/booking/BusinessOfferModels.kt" "$S/models/account/IumrahAccountModels.kt" "$S/models/account/IumrahFriendsModels.kt" "$S/models/hotel/HotelModels.kt" \
  "$S/models/nuitee/NuiteeModels.kt" "$S/domain/booking/BookingAuthFallbackPolicy.kt" "$S/domain/booking/BookingSessionPolicy.kt" \
  "$S/domain/trip/TransferModels.kt" "$S/domain/trip/TripModels.kt" "$S/models/flight/AirportModels.kt" "$S/models/flight/FlightSearchModels.kt" -d "$TMP/store.jar"
kotlin -cp "$CP:$TMP/store.jar" TestKt
