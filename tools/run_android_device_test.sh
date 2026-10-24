#!/usr/bin/env bash
set -euo pipefail

WORKSPACE="${GITHUB_WORKSPACE:-$(git rev-parse --show-toplevel)}"
cd "$WORKSPACE"
mkdir -p "$WORKSPACE/.qa"
DEVICE_LOG="$WORKSPACE/.qa/device-checks.log"
TMP_DIR="${RUNNER_TEMP:-/tmp}"

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

{
  printf 'Workspace: %s\n' "$WORKSPACE"
  printf 'Android SDK: %s\n' "$ANDROID_HOME"
  command -v adb
  adb version
} > "$DEVICE_LOG" 2>&1

collect_evidence() {
  adb shell dumpsys window windows > "$TMP_DIR/window-state.txt" 2>&1 || true
  adb logcat -d > "$TMP_DIR/device-log.txt" 2>&1 || true
  mkdir -p "$WORKSPACE/.qa/screenshots"
  adb pull /sdcard/Android/data/dev.virtualvolume.app.debug/files/qa \
    "$WORKSPACE/.qa/screenshots" || true
}
trap collect_evidence EXIT

cd "$WORKSPACE/android"
if ./gradlew :app:connectedDebugAndroidTest --console=plain --stacktrace >> "$DEVICE_LOG" 2>&1; then
  cat "$DEVICE_LOG"
else
  status=$?
  tail -n 200 "$DEVICE_LOG"
  exit "$status"
fi
