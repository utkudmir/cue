# Governance

Cue follows a maintainer-led governance model.

## Decision Model

- Day-to-day project decisions are made by maintainers.
- Changes that affect product scope, compliance boundaries, or release policy
  require explicit maintainer approval.
- Contributors are encouraged to propose improvements through issues and pull
  requests.

## Scope Guardrail

The project intentionally stays within a narrow integration boundary described in
`docs/compliance.md`.

Proposals that add `/unrestrict/*`, `/downloads/*`, `/torrents/*`,
`/streaming/*`, delegated account management, or backend token sync require
explicit compliance review before implementation.

## Conflict Resolution

- Start with discussion on the relevant issue or pull request.
- If unresolved, maintainers make the final decision and document rationale.

## Security and Conduct

- Community behavior is governed by `CODE_OF_CONDUCT.md`.
- Vulnerability handling follows `SECURITY.md`.
