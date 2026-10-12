#!/usr/bin/env bash
# Runs Gradle with --continue. On failure, turns compiler and Gradle errors into workflow
# annotations, so they show on the run page and through the API without opening the logs.
set -o pipefail
log="${RUNNER_TEMP:-/tmp}/gradle-$$.log"
./gradlew --continue --stacktrace "$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
  python3 "$(dirname "$0")/annotate.py" "$log" "$*"
fi
exit "$status"
