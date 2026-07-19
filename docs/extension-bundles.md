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

## Distribution index

The release repository's `index.json` remains the download index. Its
`sources[]` metadata is generated from the signed APK's registry asset instead
of from Kotlin source files or single-Source manifest fields. This keeps runtime
discovery, Marketplace display, and published metadata aligned.
