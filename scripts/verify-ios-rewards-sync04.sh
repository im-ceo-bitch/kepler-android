#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
S=app/src/main/java/com/iumrah/beta
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
kotlinc scripts/qa/sync02/Serialization.kt "$S/data/account/ReferralPolicy.kt" "$S/domain/booking/BookingRewardsPolicy.kt" "$S/models/account/IumrahFriendsModels.kt" scripts/qa/sync04/RewardsPolicyTest.kt -d "$TMP/rewards.jar"
kotlin -cp "$TMP/rewards.jar" RewardsPolicyTestKt
