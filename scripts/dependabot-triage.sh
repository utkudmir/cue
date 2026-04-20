#!/usr/bin/env bash
set -euo pipefail

REPO="${1:-utkudmir/debrid-hub}"

echo "[dependabot-triage] repository: ${REPO}"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required." >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "gh auth is not ready. Run: gh auth login" >&2
  exit 1
fi

prs_json="$(gh pr list --repo "${REPO}" --author app/dependabot --state open --limit 200 --json number,title,url,labels,reviews)"

count="$(python3 -c 'import json,sys; print(len(json.loads(sys.stdin.read())))' <<<"${prs_json}")"

if [[ "${count}" == "0" ]]; then
  echo "[dependabot-triage] no open Dependabot PRs."
  exit 0
fi

echo "[dependabot-triage] found ${count} open Dependabot PR(s)."

while IFS=$'\t' read -r number title url has_copilot_review; do
  [[ -z "${number}" ]] && continue

  echo
  echo "[dependabot-triage] PR #${number}: ${title}"
  echo "[dependabot-triage] URL: ${url}"

  if [[ "${has_copilot_review}" == "0" ]]; then
    echo "[dependabot-triage] requesting Copilot review"
    if gh pr edit "${number}" --repo "${REPO}" --add-reviewer copilot >/dev/null 2>&1; then
      echo "[dependabot-triage] Copilot review request sent"
    else
      echo "[dependabot-triage] Copilot review request skipped (not available in this repo)"
    fi
  else
    echo "[dependabot-triage] Copilot review already present"
  fi

  echo "[dependabot-triage] closing PR with maintenance note"
  gh pr close "${number}" --repo "${REPO}" --comment "Automated dependency triage: closing this individual Dependabot PR to reduce update noise. We now accept grouped minor/patch updates and reopen selectively when needed." >/dev/null
done < <(
  python3 -c 'import json,sys
data=json.loads(sys.stdin.read())
for pr in data:
    has_copilot=0
    for review in pr.get("reviews", []):
        author=(review.get("author") or {}).get("login", "")
        if author.lower()=="copilot":
            has_copilot=1
            break
    print("{}\t{}\t{}\t{}".format(pr["number"], pr["title"], pr["url"], has_copilot))' <<<"${prs_json}"
)

echo
echo "[dependabot-triage] done."
