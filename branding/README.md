# Branding

`branding/` is the source of truth for Cue brand assets.

- `icons/source/`: editable vector sources
- `icons/exports/`: exported assets by destination
- `screenshots/source/`: editable composition notes and inputs
- `screenshots/exports/`: exported store-ready outputs by store

Official store creative is governed by `BRAND_POLICY.md`.

Use `make screenshot-refresh` to capture fresh app scenes and regenerate
`screenshots/exports/` via `tools/screenshot-studio`.
