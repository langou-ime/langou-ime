#!/usr/bin/env bash

set -uo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <api-level>" >&2
  exit 64
fi

api_level="$1"
workspace="${GITHUB_WORKSPACE:?GITHUB_WORKSPACE must be set}"
logcat_output="${workspace}/android-api-${api_level}-logcat.txt"
input_method_output="${workspace}/android-api-${api_level}-input-method.txt"

adb logcat -c || true

set +e
(
  cd "${workspace}/production/android"
  ./gradlew connectedDebugAndroidTest --stacktrace
)
test_status=$?
set -e

adb logcat -d -v threadtime >"${logcat_output}" || true
adb shell dumpsys input_method >"${input_method_output}" || true

exit "${test_status}"
