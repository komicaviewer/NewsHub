---
meta:
  title: "What contract must a NewsHub extension APK follow?"
  navLabel: "Extension Bundle Contract"
  category: "Extensions"
  contentType: "Reference"
  contentPlan: "docs/plans/third-party-extension-docs-plan.md"
---

# What contract must a NewsHub extension APK follow?

This reference defines the protocol v2 APK, service, runtime, and network contracts. Use it while reviewing an extension or diagnosing admission failures.

## APK contents and Android components

An extension is an Android application package (APK) that contains one or more isolated Source services. NewsHub never loads extension classes into its own process.

An extension APK must meet these rules:

- Request no Android permissions
- Declare no activities, receivers, providers, or instrumentation
- Declare one service for each `Source`
- Give every service a distinct private `android:process`
- Set `android:exported="true"` and `android:isolatedProcess="true"`
- Require `tw.kevinzhang.newshub.permission.BIND_EXTENSION`
- Omit `android:externalService` or set it to `false`

The old application marker, class-name metadata, JSON registry asset, `QUERY_ALL_PACKAGES`, and in-process class loading are forbidden.

## Source service manifest contract

Each service declares the fixed discovery action and five metadata values. These values must match the signed repository target.

```xml
<service
    android:name=".ExampleExtensionService"
    android:exported="true"
    android:isolatedProcess="true"
    android:permission="tw.kevinzhang.newshub.permission.BIND_EXTENSION"
    android:process=":example_source">
    <intent-filter>
        <action android:name="tw.kevinzhang.newshub.extension.SERVICE" />
    </intent-filter>
    <meta-data android:name="newshub.extension.protocol" android:value="2" />
    <meta-data android:name="newshub.extension.source_id" android:value="com.example.news" />
    <meta-data android:name="newshub.extension.source_name" android:value="Example News" />
    <meta-data android:name="newshub.extension.source_lang" android:value="en" />
    <meta-data android:name="newshub.extension.source_base_url" android:value="https://api.example.com" />
</service>
```

Do not duplicate login fields in the manifest. Protocol v2 obtains authentication behavior from the runtime descriptor.

## Runtime descriptor contract

NewsHub requests `OP_RUNTIME_DESCRIPTOR` immediately after binding. The service returns a `SourceRuntimeDescriptor` derived from its `Source` implementation.

The Host checks these fields before any content operation:

- `protocolVersion`
- `sourceId`, `name`, `language`, and `sourceVersion`
- `iconUrl`
- `supportsCommentPagination` and `alwaysUseRawImage`
- `needsLogin`
- The complete web-cookie authentication descriptor
- `webLoginUserAgent`

Implement `AuthenticatedSource` to declare `AuthSpec.WebCookie`. Implement `WebLoginUserAgentProvider` when the website binds cookies to a browser User-Agent value. NewsHub rejects a descriptor that conflicts with the signed policy or manifest identity.

Set `needsLogin` to `true` only when ordinary content cannot be used anonymously. An optional-login Source still implements `AuthenticatedSource` but leaves `needsLogin` false. In both cases the Source must validate protected responses and throw `AuthenticationRequiredException`; the flag is not a security boundary.

## Host broker boundary

The isolated process receives `SourceRuntime.network` for source-scoped requests. It does not receive raw sockets, a cookie jar, filesystem paths, or another Source's session.

Every request must use `source_read`. The signed network policy limits exact Hypertext Transfer Protocol Secure (HTTPS) hosts, `GET` or `HEAD`, path prefixes, and credentials. NewsHub separately enforces response-size, concurrency, and deadline bounds. Wildcards, Internet Protocol (IP) literals, and undeclared destinations fail closed; redirects are evaluated hop by hop against the same signed policy.

The v2 policy separates four scopes:

- `request`: brokered requests made by Source code
- `resource`: image and media destinations resolved by NewsHub
- `external`: browser destinations opened by direct user actions
- `auth`: login page and cookie origins

The canonical policy hash is SHA-256 over UTF-8 JSON with sorted object keys, no insignificant whitespace, and unchanged array order. Use `newshub-extension-repo publish`; do not calculate `policyHash` by hand.

## Pagination and stable identifiers

`Source.getThreadPage(summary, pageToken)` owns its opaque `nextPageToken`. NewsHub returns the token unchanged.

`ThreadPage.posts` contains one fetched page. Keep post IDs stable across refreshes. The default bridge accepts only a null token and calls `getThread`.

If `supportsCommentPagination` is `true`, return comments through `getComments`. Otherwise, include every fetched comment in each `Post.comments` value.

## Repository authorization contract

The repository signs every APK identity and Source policy. NewsHub verifies these values before installation and binding:

- Target path, byte length, and SHA-256 digest
- Package name, `versionCode`, and `versionName`
- Current signer pins and signing-lineage root
- Exact service set and manifest metadata
- Full network policy and canonical `policyHash`
- Optional `acceptedArtifacts` compatibility window

`acceptedArtifacts` contains at most two previous APKs. Each entry has exactly `versionCode`, `length`, and `sha256`. The version must be lower than the current target version. Omitting an old artifact revokes it immediately.

NewsHub quarantines duplicate Source ownership, signer drift, same-version replacement, rollback, expired metadata, and contract drift.

## Repository layout

NewsHub reads this exact layout:

```text
metadata/root.json
metadata/2.root.json
metadata/timestamp.json
metadata/4.snapshot.json
metadata/7.targets.json
targets/apk/com.example.extension-v1.0.0.apk
```

`repo.json`, `index.json`, `apk/` at the repository root, and JSON registries inside APKs are unsupported legacy formats.
# 看板目錄協議相容擴充

board page IPC operation 編號不變，新增 `BoardPageRequest.refresh`、`contextEpoch` 及 `BoardPage.catalogInfo`。wire 解碼顯式處理缺省、null、未知 enum 與頁面大小驗證；舊來源回傳沒有 catalogInfo 時，Host 保持未知狀態，不宣稱刷新或完整目錄已驗證。詳見 [第三方來源看板搜尋契約](third-party-extension.md)。

Mobile01 動態目錄需要來源政策額外允許 `https://www.mobile01.com/forumlist.php` 的 GET；其他既有 host／credential 規則不變。新的本機 policy hash 為 `6d5f1c025d40a396d73b793ce4a3ff0a23f9495531b7163d2d271b7c5f9e0cdf`。來源 repo 的 contract／release catalog 與 Host 隔離測試 fixture 已同步，但遠端 signed targets 未更新；以舊發布政策執行新來源時，目錄請求仍會被正確拒絕，必須依正式發布流程審閱並發布政策後才能使用。
