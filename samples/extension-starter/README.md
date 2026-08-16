# Build the protocol v2 starter

This project is a copyable Android extension with one isolated Source service. The full tutorial is [`docs/third-party-extension.md`](../../docs/third-party-extension.md).

While this directory remains inside NewsHub, build the local API and run tests:

```bash
../../gradlew :extension-api:assembleDebug
../../gradlew -p . :extension:testDebugUnitTest :extension:assembleDebug
```

After copying the directory, set `newshubDir` to a NewsHub checkout or set `newshubApiPin` to a reviewed commit SHA. Change every example identity before publishing.

The starter deliberately contains no Android permissions and no direct network client. `BrokerNetworkAdapter` sends requests through `SourceRuntime.network`.
