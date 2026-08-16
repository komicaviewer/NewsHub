---
meta:
  title: "How do I build a third-party NewsHub extension?"
  navLabel: "Building an Extension"
  category: "Extensions"
  contentType: "Tutorial"
  contentPlan: "docs/plans/third-party-extension-docs-plan.md"
---

# How do I build a third-party NewsHub extension?

This tutorial starts with the protocol v2 starter, runs parser and broker-request tests, builds an APK, and prepares it for a signed repository. It targets Android developers who can run Gradle with Java 17 and Android SDK 35.

## Prepare the starter and API dependency

Copy `samples/extension-starter` outside the NewsHub checkout. The starter contains one `Source`, one `IsolatedSourceService`, a Host-broker adapter, and local tests.

Choose one API dependency source:

1. For local protocol work, build the Android Archive (AAR):

   ```bash
   cd path_to_newshub
   ./gradlew :extension-api:assembleDebug
   ```

2. Set `newshubDir` in the starter's `gradle.properties` to that checkout
3. For releases, remove `newshubDir` and keep the reviewed `newshubApiPin`

The verified JitPack coordinate is `com.github.komicaviewer.NewsHub:extension-api:6a94c4879ebbf052007dc6fa6374deade2428e57`. Never pin `main`, a moving tag, or a version range.

## Change the extension identity

Change the application ID, namespace, package names, Source ID, service name, display name, language, base URL, version code, and version name. Keep the same values in the Kotlin source, manifest, and repository config.

Source IDs must match `[A-Za-z0-9._-]{1,160}`. Package names must use dotted Java identifiers. Base URLs must use HTTPS.

## Implement parsing without Android state

Keep parsing functions independent from Android and the network. Accept bytes or text, then return `extension-api` models.

The starter's `ExampleParserTest` checks board, summary, thread, and paragraph mapping. Add fixtures for malformed pages, missing fields, empty results, and source-specific pagination before connecting a live endpoint.

## Send requests through the Host broker

Implement `SessionAwareSource.onAttach` and store an adapter around `runtime.network`. The starter's `BrokerNetworkAdapter` creates a `SourceNetworkRequest` with `source_read`.

Do not add OkHttp, `HttpURLConnection`, raw sockets, a WebView, or Android network permissions. The isolated process cannot use them as an alternative path.

Match each request with one signed v2 rule. A request rule specifies exact hosts, `GET` or `HEAD`, path prefixes, and whether NewsHub may attach Host-owned credentials.

## Declare authentication when required

Implement `AuthenticatedSource` when the Source uses web-cookie login. Return `AuthSpec.WebCookie` with the login URL, exact allowed hosts, cookie origins, cookie domains, and JavaScript setting.

Implement `validateSession` against a protected endpoint. Cookie presence alone does not prove a valid session. Throw `AuthenticationRequiredException` after the source identifies an unauthenticated response.

If the site binds cookies to a User-Agent value, implement `WebLoginUserAgentProvider`. Use the same value for login and credentialed Source requests.

## Register the isolated service

Copy the service declaration from `samples/extension-starter/extension/src/main/AndroidManifest.xml`. Use protocol `2` and a unique private process for every Source.

Do not request permissions or add Android components. NewsHub admits only isolated Source services.

## Run tests and build the APK

Run the local test and build tasks from the starter directory:

```bash
path_to_newshub/gradlew testDebugUnitTest assembleDebug
```

Sign a release APK with your long-lived Android signing lineage. Store the keystore outside the repository and continuous integration logs.

```bash
./gradlew :extension:assembleRelease
apksigner verify --print-certs extension/build/outputs/apk/release/extension-release.apk
```

Copy the lowercase SHA-256 certificate digest into `apkSignerPins`. Use the oldest authorized certificate digest as `lineageRootSha256`.

## Publish through a trusted repository

Follow [How do I host a NewsHub extension repository?](self-host-extension-repository.md). The publisher validates the APK, service manifest, Source metadata, network policy, signer, hashes, lengths, expiry, and signature thresholds before it writes public output.

The implementation contract is complete when these checks pass:

- Parser and broker-request tests pass
- The release APK contains only isolated Source services
- `newshub-extension-repo publish` succeeds
- `newshub-extension-repo validate` succeeds on a clean repository copy
- NewsHub displays the expected root fingerprints before trust-on-first-use confirmation
- Installation, binding, boards, thread content, login, and update work on a disposable emulator
