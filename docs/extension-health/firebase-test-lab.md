# Firebase Test Lab live extension health test

`ExtensionLiveHealthInstrumentedTest` runs the trusted NewsHub host against the seven official
extension APK bundles. Structural contracts in `health-report.json` determine success; PNG files
under `screenshots/` are supporting evidence only.

## Build and preflight locally

Build the app and instrumentation APKs first, then run the same test on a local emulator before
using paid Test Lab capacity:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install-multiple -r \
  /secure/apks/eyny.apk \
  /secure/apks/gamer.apk \
  /secure/apks/hackernews.apk \
  /secure/apks/komica.apk \
  /secure/apks/komica2.apk \
  /secure/apks/mobile01.apk \
  /secure/apks/ptt.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

The seven files supplied as additional APKs must be the exact signed artifacts already admitted
by the extension release process. Do not substitute an unsigned PR build.

## Run one bounded Test Lab matrix

The following example uses one virtual device, disables retries, and sets a ten-minute hard
timeout. The embedded profile has a seven-minute run timeout, per-operation timeouts no longer
than 25 seconds, and a maximum of 48 extension requests.

```bash
PULL_ROOT=/sdcard/Download/newshub-extension-health

gcloud firebase test android run \
  --project=cellular-unity-466016-v5 \
  --type=instrumentation \
  --app=app/build/outputs/apk/debug/app-debug.apk \
  --test=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --test-targets="class tw.kevinzhang.newshub.extension.ExtensionLiveHealthInstrumentedTest" \
  --device=model=MediumPhone.arm,version=35,locale=zh_TW,orientation=portrait \
  --additional-apks=/secure/apks/eyny.apk,/secure/apks/gamer.apk,/secure/apks/hackernews.apk,/secure/apks/komica.apk,/secure/apks/komica2.apk,/secure/apks/mobile01.apk,/secure/apks/ptt.apk \
  --environment-variables=extensionHealthOutputRoot=${PULL_ROOT},extensionHealthReportName=health-report.json \
  --directories-to-pull=${PULL_ROOT} \
  --timeout=10m \
  --num-flaky-test-attempts=0 \
  --no-auto-google-login \
  --no-record-video
```

`MediumPhone.arm` with Android 35 was confirmed available through a read-only model lookup on
2026-08-12. Confirm it again immediately before running with
`gcloud firebase test android models describe MediumPhone.arm`; Test Lab availability changes over time. The CLI
accepts more additional APKs, but this workflow intentionally caps the list at the seven release
bundles.

The `extensionHealthOutputRoot` instrumentation argument is not a credential. It accepts only a
normalized descendant of `/sdcard`, `/storage`, or `/data/local/tmp`, rejects traversal and shell
syntax, and exports these files:

```text
newshub-extension-health/
├── health-report.json
└── screenshots/
    └── <source-id>.png
```

If `extensionHealthOutputRoot` is omitted, the test retains the existing behavior and writes to
the app-owned external-files `extension-health` directory.

## Credential boundary

Never pass usernames, passwords, cookies, API keys, one-time codes, or MFA material through
`--environment-variables`, `--client-details`, APK filenames, result labels, or command arguments.
Test Lab mirrors environment variables to `am instrument`, and test artifacts are uploaded to the
configured results bucket. The checked-in GCP wrapper places a credential file under
`/sdcard/Download/newshub-private/` while pulling only `/sdcard/Download/newshub-health/`; these
paths must remain disjoint.

Gamer and EYNY will report `AUTH_REQUIRED` unless an approved private mechanism has already
provisioned their Host-owned authenticated state. This command deliberately does not weaken that
boundary. Public-source checks still run, and no extension receives raw credentials.

Firebase Test Lab execution and retained Cloud Storage artifacts may incur charges. Keep the
matrix to one approved device, retain `--timeout=10m` and zero retries, and apply the project's
results-bucket lifecycle policy before increasing the device matrix or retention period.

Official references:

- [gcloud firebase test android run reference](https://docs.cloud.google.com/sdk/gcloud/reference/firebase/test/android/run)
- [Firebase Test Lab gcloud guide](https://firebase.google.com/docs/test-lab/android/command-line)
