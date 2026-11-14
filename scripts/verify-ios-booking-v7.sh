#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
S=app/src/main/java/com/iumrah/beta
kotlinc scripts/qa/BookingV7ApiStub.kt scripts/qa/BookingV7Regression.kt "$S/domain/booking/BookingAuthFallbackPolicy.kt" "$S/domain/booking/BookingSessionPolicy.kt" -d "$TMP/v7.jar"
kotlin -cp "$TMP/v7.jar" com.iumrah.beta.domain.booking.BookingV7RegressionKt
