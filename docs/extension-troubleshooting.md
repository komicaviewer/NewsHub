---
meta:
  title: "How do I debug a rejected NewsHub extension?"
  navLabel: "Troubleshooting Extensions"
  category: "Extensions"
  contentType: "Troubleshooting"
  contentPlan: "docs/plans/third-party-extension-docs-plan.md"
---

# How do I debug a rejected NewsHub extension?

This guide maps publisher and NewsHub rejection messages to concrete checks. Start with offline validation because it does not change a device or hosted repository.

## The publisher cannot find Android SDK tools

The validator needs `apksigner` and `apkanalyzer`. Put them on `PATH`, set `ANDROID_SDK_ROOT`, or keep a `local.properties` file in a parent directory of the config.

Run these checks:

```bash
apksigner --version
apkanalyzer --version
```

Install Android SDK Build Tools and Command-line Tools when either command is missing.

## The APK identity does not match targets metadata

Compare package, version, signer, and service values:

```bash
apkanalyzer manifest print extension-release.apk
apksigner verify --print-certs extension-release.apk
```

Update `repository-config.json` only after confirming the APK is the intended release. Do not change signed metadata to authorize an unexplained signer or package.

## NewsHub reports a policy hash mismatch

Do not reorder or reformat a manually calculated hash. Remove any hand-written `policyHash` from your workflow and run `publish` again.

The publisher derives the hash from the full v2 policy. Arrays preserve their order, while object keys use lexical order.

## A broker request is denied

Compare the exact request with `request.rules`:

- Host after Internationalized Domain Names (IDN) conversion
- Method, limited to `GET` or `HEAD`
- Path prefix
- `credentialed` value
- `source_read` operation name

Add only the required host and path. Do not use a wildcard or `/` when a stable narrower prefix exists.

## Images or external links do not open

Add image hosts to `resource.exactHosts` and the `resource_read` capability. Add browser destinations to `external.exactHosts` and the `external_link` capability.

Returning a URL does not bypass these scopes. NewsHub converts authorized values to session-bound handles and rejects unscoped destinations.

## Login opens but the session remains signed out

Confirm that `AuthSpec.WebCookie` includes the login host, cookie origins, and required cookie domains. Set `javaScriptEnabled` only when the login page needs it.

Call `validateSession` against a protected endpoint. If the website binds cookies to User-Agent, implement `WebLoginUserAgentProvider` and use that value for Source requests.

## A newly published repository appears frozen

Check each role's expiry and version. Then verify that `timestamp.json` describes the exact version, length, and hash of the versioned snapshot.

Run the offline validator against downloaded bytes:

```bash
newshub-extension-repo validate downloaded_repository
```

Do not reuse a version with different bytes. Increase targets, snapshot, and timestamp versions after targets change.

## An installed previous version is quarantined

Inspect `acceptedArtifacts` in the current signed targets. The list must include the installed version's exact version code, byte length, and SHA-256 digest.

The publisher does not retain old artifacts implicitly. Add the intended previous artifact before rollout, or leave it absent when immediate revocation is required.

## Root rotation fails

Confirm that available old private keys meet the current root threshold. Confirm that the next version is exactly one greater and the new root also meets its new threshold.

If old authority is unavailable, create a new repository trust domain. Existing devices must confirm the new trust root.
