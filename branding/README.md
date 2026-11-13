# Branding

`branding/` is the source of truth for Cue brand assets.

- `icons/source/`: editable vector sources
- `icons/exports/`: exported assets by destination
- `screenshots/source/`: editable composition notes and inputs
- `screenshots/exports/`: exported store-ready outputs by store

Official store creative is governed by `BRAND_POLICY.md`.

The screenshot generator is maintained outside this repository and used only
when refreshing store images. This repository keeps the capture inputs and
exported store assets.

To capture fresh app scenes and regenerate `screenshots/exports/`, explicitly
provide the external generator directory:

```bash
CUE_SCREENSHOT_STUDIO_DIR=/path/to/cue-screenshot-studio make screenshot-refresh
```

The external directory must contain the generator's `package.json` and
`export-assets.mjs`. The command installs its dependencies there, captures the
app scenes, and exports the store images. The generator is not required for
app builds, tests, or uploading existing store assets.
