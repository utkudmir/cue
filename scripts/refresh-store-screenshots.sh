#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IOS_BUNDLE_ID="com.utkudemir.cue"
ANDROID_PACKAGE="com.utkudemir.cue"
ANDROID_APK_PATH="$ROOT_DIR/androidApp/build/outputs/apk/debug/androidApp-debug.apk"

IOS_CAPTURE_DIR="$ROOT_DIR/branding/screenshots/source/captures/ios/iphone"
ANDROID_CAPTURE_DIR="$ROOT_DIR/branding/screenshots/source/captures/android/phone"
STUDIO_DIR="$ROOT_DIR/tools/screenshot-studio"

resolve_booted_ios_udid() {
  xcrun simctl list devices booted | awk '/\(Booted\)/ { if (match($0, /\(([0-9A-F-]+)\)/)) { print substr($0, RSTART + 1, RLENGTH - 2); exit } }'
}

resolve_android_device_id() {
  local device_id="${ANDROID_SERIAL:-}"
  if [[ -n "$device_id" ]]; then
    printf '%s\n' "$device_id"
    return 0
  fi
  device_id="$(adb devices | awk 'NR > 1 && $2 == "device" && $1 ~ /^emulator-/ { print $1; exit }')"
  printf '%s\n' "$device_id"
}

resolve_android_component() {
  local component
  component="$(adb -s "$1" shell cmd package resolve-activity --brief "$ANDROID_PACKAGE" 2>/dev/null | tr -d '\r' | awk 'NF { line = $0 } END { print line }')"
  printf '%s\n' "$component"
}

capture_ios_scene() {
  local udid="$1"
  local scene="$2"
  local output="$IOS_CAPTURE_DIR/$scene.png"

  xcrun simctl launch --terminate-running-process "$udid" "$IOS_BUNDLE_ID" --cue-screenshot-scene "$scene" >/dev/null
  sleep 3
  xcrun simctl io "$udid" screenshot "$output" >/dev/null
  echo "Captured iOS scene: $scene -> $output"
}

grant_ios_notifications() {
  local udid="$1"
  xcrun simctl privacy "$udid" grant notifications "$IOS_BUNDLE_ID" >/dev/null 2>&1 || true
}

capture_android_scene() {
  local device_id="$1"
  local component="$2"
  local scene="$3"
  local output="$ANDROID_CAPTURE_DIR/$scene.png"

  adb -s "$device_id" shell am start -W -n "$component" --es cue_screenshot_scene "$scene" >/dev/null
  sleep 2
  adb -s "$device_id" exec-out screencap -p > "$output"
  echo "Captured Android scene: $scene -> $output"
}

grant_android_notifications() {
  local device_id="$1"
  adb -s "$device_id" shell pm grant "$ANDROID_PACKAGE" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  adb -s "$device_id" shell cmd appops set "$ANDROID_PACKAGE" POST_NOTIFICATION allow >/dev/null 2>&1 || true
}

mkdir -p "$IOS_CAPTURE_DIR"
mkdir -p "$ANDROID_CAPTURE_DIR"

make -C "$ROOT_DIR" ios-run
make -C "$ROOT_DIR" android-debug

IOS_UDID="${IOS_SIMULATOR_UDID:-$(resolve_booted_ios_udid)}"
if [[ -z "$IOS_UDID" ]]; then
  echo "No booted iOS simulator found. Set IOS_SIMULATOR_UDID or run make ios-run first." >&2
  exit 1
fi

ANDROID_DEVICE_ID="$(resolve_android_device_id)"
if [[ -z "$ANDROID_DEVICE_ID" ]]; then
  echo "No Android emulator found. Start an emulator or set ANDROID_SERIAL." >&2
  exit 1
fi

if [[ ! -f "$ANDROID_APK_PATH" ]]; then
  echo "Android debug APK not found at $ANDROID_APK_PATH" >&2
  exit 1
fi

adb -s "$ANDROID_DEVICE_ID" install -r "$ANDROID_APK_PATH" >/dev/null
grant_ios_notifications "$IOS_UDID"
grant_android_notifications "$ANDROID_DEVICE_ID"

ANDROID_COMPONENT="$(resolve_android_component "$ANDROID_DEVICE_ID")"
if [[ -z "$ANDROID_COMPONENT" || "$ANDROID_COMPONENT" == *"No activity"* ]]; then
  echo "Unable to resolve launcher activity for $ANDROID_PACKAGE" >&2
  exit 1
fi

for scene in onboarding demo-home demo-trust; do
  capture_ios_scene "$IOS_UDID" "$scene"
done

for scene in onboarding demo-home demo-trust; do
  capture_android_scene "$ANDROID_DEVICE_ID" "$ANDROID_COMPONENT" "$scene"
done

npm --prefix "$STUDIO_DIR" ci
CUE_REPO_ROOT="$ROOT_DIR" npm --prefix "$STUDIO_DIR" run export-assets

echo "Store screenshots refreshed under branding/screenshots/exports/."
