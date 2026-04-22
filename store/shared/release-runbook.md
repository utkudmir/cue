# Release Runbook

## Preconditions

- RC verification passed locally and in CI
- manual sign-off recorded
- approved tag created (`vX.Y.Z`)
- signed release candidate artifacts built
- App Store review submission prepared
- Play closed testing requirement completed

## Launch Day

1. Set `site/data/launch-state.json` to `live` with store URLs.
2. Verify Pages deployment is complete.
3. Trigger App Store manual release.
4. Trigger Google Play production full rollout.
5. Publish GitHub release notes for the tagged source release.

## Hotfix Policy

Default to fix-forward with a new tag and a new signed candidate.
Only unpublish or pull a release for a severe blocker.
