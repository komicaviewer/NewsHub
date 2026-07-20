# Extension bundle contract

NewsHub extensions are Android APK bundles. One bundle may provide multiple
`Source` implementations; the APK is the installation, signing, update, and
uninstall boundary.

This is a clean-break contract. The loader does not support the previous
single-Source `newshub.extension.source_class` metadata.

## Manifest

An extension bundle is discovered through two application metadata entries:

```xml
<meta-data android:name="newshub.extension" android:value="true" />
<meta-data
    android:name="newshub.extension.registry"
    android:value="newshub-extension.json" />
```

The registry value names an asset in the APK's `assets/` root.

## Registry schema

```json
{
  "schemaVersion": 1,
  "name": "NewsHub: Komica2",
  "sources": [
    {
      "className": "tw.kevinzhang.newshub.extension.twocat.komica2.Komica2TwocatSource",
      "id": "tw.kevinzhang.komica2.twocat",
      "name": "Komica2 Twocat",
      "lang": "zh-TW",
      "baseUrl": "https://2cat.org"
    }
  ]
}
```

Requirements:

- `schemaVersion` must be `1`.
- `name` and all Source fields must be non-blank.
- `sources` must be non-empty and Source IDs must be unique within the bundle.
- `className` must identify a public no-argument class implementing `Source`.
- Runtime `Source.id`, `Source.name`, and `Source.language` must exactly match
  `id`, `name`, and `lang` in the descriptor.

The loader rejects the entire bundle when its registry or any Source is invalid.
Valid bundles are flattened into `ExtensionLoader.sourcesFlow`, and the existing
attachment flow gives each Source its HTTP client or source-scoped runtime.

## Thread pagination

`Source.getThreadPage(summary, pageToken)` is the forward-compatible thread
pagination contract. A source owns `nextPageToken`: it is opaque to NewsHub and
must be passed back unchanged as the next `pageToken`. Do not encode assumptions
about its format in a host or another extension.

`ThreadPage.posts` contains only the posts fetched for that request. Every
`Post.id` must remain the source's stable post identifier so the host can
distinguish and de-duplicate posts when pages are appended; a page token is not
a post ID and must never be inferred from one. The optional first-page
`ThreadPage.metadata` carries the canonical thread ID, title, and URL. If later
pages repeat metadata, they must keep the same thread ID.

For an existing source that only implements `getThread(summary)`, the API's
default `getThreadPage(summary, null)` bridges to that legacy method and returns
`nextPageToken = null`. Passing a non-null token to such a source throws
`UnsupportedOperationException`. Sources with real pagination must override
`getThreadPage` and return their own opaque continuation token. They must also
keep `getThread` returning the first page for hosts built against the older API.

## Distribution index

The release repository's `index.json` remains the download index. Its
`sources[]` metadata is generated from the signed APK's registry asset instead
of from Kotlin source files or single-Source manifest fields. This keeps runtime
discovery, Marketplace display, and published metadata aligned.
