#!/usr/bin/env bash
set -euo pipefail

APK_PATH="${1:-}"
PACKAGE_NAME="${2:-com.utkudemir.cue}"
LOG_DIR="${ANDROID_VERIFY_LOG_DIR:-$PWD/build/rc-verify/android/manual}"
LOG_FILE="$LOG_DIR/verify.log"

mkdir -p "$LOG_DIR"
exec > >(tee "$LOG_FILE") 2>&1

if [[ -z "$APK_PATH" || ! -f "$APK_PATH" ]]; then
  echo "Android APK not found: ${APK_PATH:-<missing>}" >&2
  exit 1
fi

resolve_device_id() {
  local device_id="${ANDROID_SERIAL:-}"

  if [[ -n "$device_id" ]]; then
    printf '%s\n' "$device_id"
    return 0
  fi

  device_id="$(adb devices | awk 'NR > 1 && $2 == "device" && $1 ~ /^emulator-/ { print $1; exit }')"
  if [[ -z "$device_id" ]]; then
    return 1
  fi

  printf '%s\n' "$device_id"
}

DEVICE_ID="$(resolve_device_id)"
echo "Using Android device: $DEVICE_ID"

adb -s "$DEVICE_ID" wait-for-device

for attempt in {1..30}; do
  if adb -s "$DEVICE_ID" shell pm path android >/dev/null 2>&1; then
    break
  fi
  echo "Waiting for package manager on $DEVICE_ID (${attempt}/30)"
  sleep 2
done

echo "Installing APK: $APK_PATH"
adb -s "$DEVICE_ID" install -r "$APK_PATH"

LAUNCH_COMPONENT="$(adb -s "$DEVICE_ID" shell cmd package resolve-activity --brief "$PACKAGE_NAME" 2>/dev/null | tr -d '\r' | awk 'NF { line = $0 } END { print line }')"
if [[ -z "$LAUNCH_COMPONENT" || "$LAUNCH_COMPONENT" == *"No activity"* ]]; then
  echo "Could not resolve launcher activity for $PACKAGE_NAME" >&2
  exit 1
fi

echo "Resolved launcher activity: $LAUNCH_COMPONENT"
adb -s "$DEVICE_ID" shell am start -W -n "$LAUNCH_COMPONENT"

APP_PID=""
for attempt in {1..15}; do
  APP_PID="$(adb -s "$DEVICE_ID" shell pidof "$PACKAGE_NAME" 2>/dev/null | tr -d '\r' | tr -d '\n')"
  if [[ -n "$APP_PID" ]]; then
    break
  fi
  echo "Waiting for app process (${attempt}/15)"
  sleep 1
done

if [[ -z "$APP_PID" ]]; then
  echo "Could not find a running process for $PACKAGE_NAME" >&2
  exit 1
fi

echo "Android smoke passed. Process id: $APP_PID"
