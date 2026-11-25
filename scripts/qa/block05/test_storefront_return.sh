#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
kotlinc app/src/main/java/com/iumrah/beta/data/hotel/StorefrontReturnPolicy.kt scripts/qa/block05/StorefrontReturnSmoke.kt -include-runtime -d "$T/test.jar"
java -jar "$T/test.jar"
