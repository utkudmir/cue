# Cue Screenshot Studio

This local-only Next.js tool composes App Store and Play Store screenshot exports from:

- app captures in `branding/screenshots/source/captures/**`
- app icon in `branding/icons/exports/app-store/cue-icon-1024.png`

The generated outputs are written to:

- `branding/screenshots/exports/app-store/`
- `branding/screenshots/exports/play-store/`

## Primary Workflow

From repo root:

```bash
make screenshot-refresh
```

This command:

1. Builds/launches iOS and Android apps
2. Captures onboarding/demo-home/demo-trust scenes
3. Syncs captures into studio input assets
4. Exports final store images

## Manual Studio Iteration

```bash
npm --prefix tools/screenshot-studio ci
npm --prefix tools/screenshot-studio run dev
```

Then open `http://localhost:3000` for visual iteration.
