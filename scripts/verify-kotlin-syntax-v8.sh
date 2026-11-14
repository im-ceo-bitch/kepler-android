#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
KOTLIN_HOME="$(cd "$(dirname "$(readlink -f "$(command -v kotlinc)")")/.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
kotlinc -cp "$KOTLIN_HOME/lib/kotlin-compiler.jar" scripts/qa/KotlinPsiSyntaxCheck.kt -d "$TMP/syntax.jar"
java -cp "$TMP/syntax.jar:$KOTLIN_HOME/lib/*" KotlinPsiSyntaxCheckKt app/src/main/java app/src/test/java
