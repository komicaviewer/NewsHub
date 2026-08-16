# Build the protocol v2 starter

This project is a copyable Android extension with one isolated Source service. The full tutorial is [`docs/third-party-extension.md`](../../docs/third-party-extension.md).

The starter defaults to the reviewed JitPack commit in `gradle.properties`. Run its tests and build the APK:

```bash
../../gradlew -p . :extension:testDebugUnitTest :extension:assembleDebug
```

To test an unpublished local API change, build `:extension-api:assembleDebug` in NewsHub and set `newshubDir` to that checkout. The local AAR overrides JitPack. Change every example identity before publishing.

The starter deliberately contains no Android permissions and no direct network client. `BrokerNetworkAdapter` sends requests through `SourceRuntime.network`.
