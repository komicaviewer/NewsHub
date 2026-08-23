---
meta:
  title: "How do I host a NewsHub extension repository?"
  navLabel: "Hosting an Extension Repository"
  category: "Extensions"
  contentType: "How-to"
  contentPlan: "docs/plans/third-party-extension-docs-plan.md"
---

# How do I host a NewsHub extension repository?

This guide creates P-256 keys, configures one signed repository, publishes an APK, validates the result offline, and uploads only public files. Complete the extension tutorial first.

## Install the publisher

Create an isolated Python environment and install the pinned cryptography dependency:

```bash
python3 -m venv .venv
. .venv/bin/activate
python3 -m pip install -r path_to_newshub/tools/extension-repo/requirements.txt
```

The commands below use `path_to_newshub/tools/extension-repo/newshub-extension-repo`.

## Initialize a test repository

Use ephemeral mode for local and emulator tests:

```bash
newshub-extension-repo init \
  --mode ephemeral \
  --config repository-config.json
```

The command creates a `0700` private directory with `0600` P-256 keys. It also creates a separate public output path. Ephemeral keys are unencrypted and must never authorize a production repository.

Production mode requires a password supplied through the process environment. Read it without placing the value in shell history:

```bash
read -rs NEWSHUB_REPO_KEY_PASSWORD
export NEWSHUB_REPO_KEY_PASSWORD
newshub-extension-repo init \
  --mode production \
  --config repository-config.json
```

Do not put the password, private keys, Android keystore, or decrypted material in Git. Production root and targets keys should live offline or in an access-controlled signing system.

## Configure repository metadata

Edit the single `repository-config.json` file. Use `samples/extension-starter/repository-config.fragment.json` for the extension entry and [repository-config.schema.json](../schemas/repository-config.schema.json) for field constraints.

The starter uses network policy v2, whose resources are always cookieless. For a site whose media requires the WebView cookie session, use [network-policy-v3.schema.json](../schemas/network-policy-v3.schema.json). Version 3 requires signed exact hosts, exact paths and/or path prefixes, and a User-Agent for every credentialed resource rule, and keeps public resource rules explicitly cookieless.

The four metadata versions must increase after their signed content changes. Set short expiry periods for online roles:

- `timestamp`: publish frequently and expire in two days
- `snapshot`: expire in seven days
- `targets`: expire in 30 days
- `root`: expire in 365 days and rotate before expiration

The generated defaults use two-of-three root signatures and two-of-two targets signatures. Preserve those thresholds for production unless you document a stronger key ceremony.

## Authorize a previous APK explicitly

Add `acceptedArtifacts` only when an installed previous APK should remain usable during rollout:

```json
"acceptedArtifacts": [
  {
    "versionCode": 4,
    "length": 5123456,
    "sha256": "lowercase_sha256_of_the_exact_previous_apk_here"
  }
]
```

Each entry identifies one exact artifact. You may list two entries. Its version must be lower than the current APK version, and its length cannot exceed 64 MiB.

The publisher never infers this list from existing metadata or local files. Omitting an old artifact revokes it as soon as devices trust the new targets metadata.

## Publish and validate public files

Build signed metadata and copy APK targets into the public directory:

```bash
newshub-extension-repo publish --config repository-config.json
newshub-extension-repo validate repository --config repository-config.json
```

The validator works offline. It checks P-256 thresholds, expiry, versions, hashes, lengths, APK signer lineage, manifest components, service metadata, protocol `2`, full network policy, canonical policy hash, and `acceptedArtifacts`.

Inspect the trust values that NewsHub will display:

```bash
newshub-extension-repo fingerprints repository
```

Record the root metadata digest, root key IDs, threshold, and repository-domain UUID through a second communication channel.

## Upload only the public directory

Serve the public directory from one canonical HTTPS base URL. The server must not redirect repository requests.

Upload these paths with their exact bytes:

```text
metadata/root.json
metadata/1.root.json
metadata/timestamp.json
metadata/1.snapshot.json
metadata/1.targets.json
targets/apk/package_name-vversion_name.apk
```

Never upload the key directory. Do not publish `repo.json`, `index.json`, or an unsigned hash list.

## Test trust and installation

Add the canonical base URL in NewsHub. Compare every displayed root fingerprint with the value from your second channel before confirming trust on first use (TOFU).

Test on a disposable emulator:

1. Refresh the repository
2. Install the extension
3. Confirm service binding and runtime descriptor acceptance
4. Load boards, summaries, and a thread
5. Complete login when the Source requires it
6. Publish a higher version and verify update behavior
7. Remove an artifact from `acceptedArtifacts` and verify quarantine behavior in a separate negative test

Keep production signing and upload outside this local test flow.
