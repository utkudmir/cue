#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_PATH="${1:-}"
OUTPUT_PATH="${2:-}"

usage() {
  cat <<'EOF'
Usage:
  scripts/redact-shareable-report.sh <input-path> [output-path]

Examples:
  scripts/redact-shareable-report.sh build/rc-verify/20260418T184126Z
  scripts/redact-shareable-report.sh build/rc-verify build/rc-verify-redacted

The script copies the input file or directory and redacts local paths,
usernames, adb public keys, signing identities, team identifiers, and common
token-like values from shareable text artifacts.
EOF
}

if [[ -z "$INPUT_PATH" ]]; then
  usage >&2
  exit 1
fi

resolve_path() {
  local value="$1"
  if [[ "$value" = /* ]]; then
    printf '%s\n' "$value"
  else
    printf '%s\n' "$ROOT_DIR/$value"
  fi
}

INPUT_PATH="$(resolve_path "$INPUT_PATH")"
if [[ ! -e "$INPUT_PATH" ]]; then
  echo "Input path does not exist: $INPUT_PATH" >&2
  exit 1
fi

if [[ -z "$OUTPUT_PATH" ]]; then
  OUTPUT_PATH="${INPUT_PATH%/}-redacted"
else
  OUTPUT_PATH="$(resolve_path "$OUTPUT_PATH")"
fi

if [[ "$INPUT_PATH" == "$OUTPUT_PATH" ]]; then
  echo "Output path must be different from input path." >&2
  exit 1
fi

copy_tree() {
  local source="$1"
  local destination="$2"
  rm -rf "$destination"
  if [[ -d "$source" ]]; then
    mkdir -p "$destination"
    cp -R "$source/." "$destination/"
  else
    mkdir -p "$(dirname "$destination")"
    cp "$source" "$destination"
  fi
}

copy_tree "$INPUT_PATH" "$OUTPUT_PATH"

python3 - "$OUTPUT_PATH" <<'PY'
from __future__ import annotations

import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])

text_suffixes = {
    ".log", ".txt", ".json", ".tsv", ".env", ".md", ".xml", ".plist",
    ".yml", ".yaml", ".html", ".xcresult", ".pbxproj"
}

patterns = [
    (re.compile(r"/Users/[^/\s]+"), "/Users/REDACTED"),
    (re.compile(r"/tmp/android-[^/\s]+"), "/tmp/android-REDACTED"),
    (re.compile(r"(?m)^USER=.*$"), "USER=REDACTED"),
    (re.compile(r"(?m)^HOME=.*$"), "HOME=/Users/REDACTED"),
    (re.compile(r"(?m)(TeamIdentifierPrefix=)[A-Z0-9.]+"), r"\1REDACTED."),
    (re.compile(r"(?m)(VERSION_INFO_BUILDER=).*$"), r"\1REDACTED"),
    (re.compile(r'(?m)(Signing Identity:\s+").*(")'), r"\1REDACTED\2"),
    (re.compile(r"adb public key \[[^\]]+\]\s+[^\s]+"), "adb public key [REDACTED] REDACTED@unknown"),
    (re.compile(r"androidboot\.qemu\.adb\.pubkey=[^\s]+(?:\s+[^\s]+)?"), "androidboot.qemu.adb.pubkey=REDACTED"),
    (re.compile(r"\b[^\s@]+@unknown\b"), "REDACTED@unknown"),
    (re.compile(r"(?i)([?&](?:code|token|secret|client_secret|refresh_token|access_token|device_code|user_code)=)[^&\s]+"), r"\1***REDACTED***"),
    (re.compile(r'(?i)("?(?:access_token|refresh_token|client_secret|device_code|user_code)"?\s*[:=]\s*"?)[^",\s]+("?)'), r"\1***REDACTED***\2"),
    (re.compile(r"(?i)(\b(?:access_token|refresh_token|client_secret|device_code|user_code)\b\s*[:=]\s*)[^,\s]+"), r"\1***REDACTED***"),
]

def should_treat_as_text(path: pathlib.Path) -> bool:
    if path.suffix in text_suffixes:
      return True
    try:
      data = path.read_bytes()[:1024]
    except OSError:
      return False
    return b"\x00" not in data

paths = [root] if root.is_file() else [p for p in root.rglob("*") if p.is_file()]

for path in paths:
    if not should_treat_as_text(path):
        continue
    try:
        raw = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        continue
    redacted = raw
    for pattern, replacement in patterns:
        redacted = pattern.sub(replacement, redacted)
    if redacted != raw:
        path.write_text(redacted, encoding="utf-8")

print(root)
PY

echo "Redacted shareable copy written to: $OUTPUT_PATH"
