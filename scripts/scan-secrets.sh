#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required for secret scanning." >&2
  exit 1
fi

python3 - <<'PY'
import pathlib
import re
import subprocess
import sys

root = pathlib.Path.cwd()

try:
    tracked = subprocess.check_output(["git", "ls-files"], text=True).splitlines()
except subprocess.CalledProcessError as exc:
    print(f"Failed to enumerate tracked files: {exc}", file=sys.stderr)
    sys.exit(2)

patterns = [
    ("AWS Access Key", re.compile(r"AKIA[0-9A-Z]{16}")),
    ("Google API Key", re.compile(r"AIza[0-9A-Za-z\-_]{35}")),
    ("GitHub PAT", re.compile(r"gh[pousr]_[A-Za-z0-9_]{30,}")),
    ("Slack Token", re.compile(r"xox[baprs]-[A-Za-z0-9-]{10,}")),
    ("Private Key Header", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |)?PRIVATE KEY-----")),
    ("JWT", re.compile(r"eyJ[A-Za-z0-9_=-]{10,}\.[A-Za-z0-9_=-]{10,}\.[A-Za-z0-9_=-]{10,}")),
]

ignore_suffixes = {
    ".png", ".jpg", ".jpeg", ".gif", ".pdf", ".jar", ".xcworkspace", ".xcodeproj", ".xcresult"
}

findings = []
for rel in tracked:
    path = root / rel
    if path.suffix.lower() in ignore_suffixes:
        continue

    try:
        data = path.read_bytes()
    except Exception:
        continue

    if b"\0" in data:
        continue

    text = data.decode("utf-8", errors="ignore")
    for label, regex in patterns:
        match = regex.search(text)
        if match:
            findings.append((label, rel, match.group(0)[:120]))
            break

if findings:
    print("Potential secret findings detected:")
    for label, rel, snippet in findings:
        print(f"- [{label}] {rel}: {snippet}")
    sys.exit(1)

print("Secret scan passed: no high-confidence secrets found in tracked files.")
PY
