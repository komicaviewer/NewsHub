# Extension bundle contract

NewsHub uses a clean-break isolated-service extension contract. An APK may own
multiple Sources, but every Source must have a separate Android Service,
isolated UID, process, Binder session, and Host capability. Extension code is
never loaded into the NewsHub process.

## Manifest

Extension APKs declare no permissions. Every Source Service must be exported,
use `android:isolatedProcess="true"`, require
`tw.kevinzhang.newshub.permission.BIND_EXTENSION`, and use a unique private
`android:process`. Discovery uses this fixed action:

```xml
<service
    android:name=".ExampleExtensionService"
    android:exported="true"
    android:isolatedProcess="true"
    android:permission="tw.kevinzhang.newshub.permission.BIND_EXTENSION"
    android:process=":source_example">
    <intent-filter>
        <action android:name="tw.kevinzhang.newshub.extension.SERVICE" />
    </intent-filter>
    <meta-data android:name="newshub.extension.protocol" android:value="1" />
    <meta-data android:name="newshub.extension.source_id" android:value="example" />
    <meta-data android:name="newshub.extension.source_name" android:value="Example" />
    <meta-data android:name="newshub.extension.source_lang" android:value="en" />
    <meta-data android:name="newshub.extension.source_base_url" android:value="https://example.com" />
</service>
```

The Host verifies the official package and signing lineage, permission owner,
Service flags, action, protocol, and destination-owned Source metadata before
an explicit bind. Duplicate Source ownership quarantines the conflicting set.
The old application marker, class name metadata, registry asset,
`QUERY_ALL_PACKAGES`, and in-process class loading are forbidden.

## Runtime boundary

`IsolatedSourceService` exposes bounded asynchronous AIDL operations. Payloads
use one-shot pipes with byte and deadline limits. Each bind receives a
source-scoped Host broker capability; the extension never receives an ambient
socket client, raw cookies, filesystem paths, or another Source's session.

Network requests are limited by Host policy to named operations, exact HTTPS
hosts, allowed methods and path prefixes, safe headers, globally routable DNS,
bounded response size, concurrency, and timeouts. Credentials are injected by
the Host only after policy validation and are never returned over IPC.

Media URLs returned by an extension are replaced with unguessable,
session-generation-bound `newshub-resource` handles. Coil resolves only valid
handles through the same broker; naked URL, file, content, external-link, and
video sinks fail closed.

## Pagination

`Source.getThreadPage(summary, pageToken)` owns its opaque `nextPageToken`.
NewsHub passes it back unchanged. `ThreadPage.posts` contains only the fetched
page and preserves stable post IDs. The default adapter supports only a null
token; paginated Sources override the method.

## Distribution

The destination repository owns the exact seven-package/thirteen-Source
metadata and a signer pin set per package. Admission rejects legacy registry
assets, source metadata drift, foreign signers, downgrades, same-version APK
replacement, or incomplete distributions. Empty production pins deliberately
block publishing until the per-package key ceremony is complete.

Remote Marketplace installation remains disabled until threshold-authenticated
repository metadata with expiry, rollback, and freeze protection is embedded.
An APK hash supplied by the same unsigned index is not a trust anchor.
