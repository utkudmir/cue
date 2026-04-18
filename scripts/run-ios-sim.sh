#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DERIVED_DATA_PATH="${DERIVED_DATA_PATH:-$ROOT_DIR/build/ios-derived-data}"
APP_PATH="$DERIVED_DATA_PATH/Build/Products/Debug-iphonesimulator/DebridHub.app"
BUNDLE_ID="app.debridhub.ios"
SIMULATOR_NAME="${IOS_SIMULATOR_NAME:-}"
SIMULATOR_UDID="${IOS_SIMULATOR_UDID:-}"
IOS_SKIP_BUILD="${IOS_SKIP_BUILD:-0}"
IOS_RESOLVER_SCRIPT="$ROOT_DIR/scripts/resolve-ios-simulator.py"

find_simulator_udid() {
  xcrun simctl list devices available | awk -v target="$SIMULATOR_NAME" '
    $0 ~ target {
      if (match($0, /\(([0-9A-F-]+)\)/)) {
        value = substr($0, RSTART + 1, RLENGTH - 2)
        print value
        exit
      }
    }
  '
}

resolve_dynamic_simulator() {
  local resolver_output
  local label="${IOS_DEVICE_CLASS:-latest-phone}"

  if [[ ! -x "$IOS_RESOLVER_SCRIPT" ]]; then
    echo "resolve-ios-simulator script is missing: $IOS_RESOLVER_SCRIPT" >&2
    return 1
  fi

  if ! resolver_output="$(python3 "$IOS_RESOLVER_SCRIPT" --label "$label" --name "$SIMULATOR_NAME" 2>/dev/null)"; then
    return 1
  fi

  SIMULATOR_NAME="$(printf '%s' "$resolver_output" | awk -F '\t' '{print $1}')"
  SIMULATOR_UDID="$(printf '%s' "$resolver_output" | awk -F '\t' '{print $2}')"
  [[ -n "$SIMULATOR_NAME" && -n "$SIMULATOR_UDID" ]]
}

if [[ -z "$SIMULATOR_UDID" ]]; then
  if [[ -n "$SIMULATOR_NAME" ]]; then
    SIMULATOR_UDID="$(find_simulator_udid)"
  fi

  if [[ -z "$SIMULATOR_UDID" ]]; then
    if ! resolve_dynamic_simulator; then
      echo "No suitable iPhone simulator found and dynamic resolution failed." >&2
      exit 1
    fi
  fi
fi

if [[ -z "$SIMULATOR_UDID" ]]; then
  echo "Simulator \"$SIMULATOR_NAME\" was not found. Set IOS_SIMULATOR_NAME to an available device." >&2
  exit 1
fi

open -a Simulator
xcrun simctl boot "$SIMULATOR_UDID" >/dev/null 2>&1 || true
xcrun simctl bootstatus "$SIMULATOR_UDID" -b

if [[ "$IOS_SKIP_BUILD" != "1" ]]; then
  IOS_SIMULATOR_NAME="$SIMULATOR_NAME" IOS_SIMULATOR_UDID="$SIMULATOR_UDID" "$ROOT_DIR/scripts/build-ios-sim.sh"
fi

if [[ ! -d "$APP_PATH" ]]; then
  echo "Built app not found at $APP_PATH" >&2
  exit 1
fi

xcrun simctl install "$SIMULATOR_UDID" "$APP_PATH"
xcrun simctl launch "$SIMULATOR_UDID" "$BUNDLE_ID"
