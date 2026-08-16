---
meta:
  title: "How do I preserve trust and rotate repository keys?"
  navLabel: "Rotating Repository Keys"
  category: "Extensions"
  contentType: "How-to"
  contentPlan: "docs/plans/third-party-extension-docs-plan.md"
---

# How do I preserve trust and rotate repository keys?

This guide explains the repository trust boundary and rotates root keys without asking devices to trust a new repository. It assumes you still control enough old root keys to meet the current threshold.

## Understand the four signing roles

Each role limits the impact of one compromised key set:

- `root`: authorizes keys and thresholds for every role
- `targets`: authorizes APK identities, signers, Sources, and network policies
- `snapshot`: binds one targets version, length, and hash
- `timestamp`: publishes the current snapshot and bounds freeze time

NewsHub pins the initial `root.json` through trust on first use (TOFU). It accepts a new root only when the version increases by exactly one and both the old and new root thresholds sign the new document.

## Prepare a root rotation

Back up the current public repository and private key inventory. Confirm that the available old keys meet the existing root threshold.

Set `NEWSHUB_REPO_KEY_PASSWORD` when the config uses production mode. Run the rotation command:

```bash
newshub-extension-repo rotate-root \
  --config repository-config.json \
  --new-key-name root-v2-1 \
  --new-key-name root-v2-2 \
  --new-key-name root-v2-3 \
  --threshold 2
```

The command generates new P-256 root keys, writes `2.root.json`, replaces `root.json`, and updates the config. The new root contains old-threshold and new-threshold signatures.

## Validate before upload

Verify fingerprints and the complete repository before changing hosted bytes:

```bash
newshub-extension-repo fingerprints repository/metadata/2.root.json
newshub-extension-repo validate repository --config repository-config.json
```

Confirm that the new threshold and key IDs match the rotation record. Upload `2.root.json` before metadata that requires it. Keep `root.json` equal to the newest root for new devices.

## Rotate online roles

To rotate targets, snapshot, or timestamp keys, change that role's `keyNames` and threshold, then rotate the root. The new root authorizes the online keys.

Increase the affected metadata version before publishing. A signature from an unlisted key never grants authority, even if the key exists in the root `keys` object.

## Recover from key loss or compromise

If remaining old root keys cannot meet the current threshold, you cannot perform an in-domain root rotation. Publish a new repository trust domain and require explicit user trust.

If a targets key is compromised but root authority remains intact, rotate the targets role in a new root. Then publish higher targets, snapshot, and timestamp versions. Remove unauthorized APKs and previous artifacts from signed targets.

Never lower a threshold to work around missing keys. Never replace metadata bytes without increasing the signed version.
