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

Match each request with one signed rule. A request rule specifies exact hosts, `GET` or `HEAD`, path prefixes, and whether NewsHub may attach Host-owned credentials.

Resource URLs are cookieless in v1 and v2 policies. Use a v3 `resource.rules` entry only when an image or media endpoint requires the WebView session. A credentialed resource rule must sign both exact hosts and the exact login User-Agent:

```json
"resource": {
  "rules": [
    {
      "exactHosts": ["forum.example.com"],
      "exactPaths": ["/forum.php"],
      "pathPrefixes": ["/data/attachment/"],
      "credentialed": true,
      "userAgent": "NewsHub Extension Browser/1.0"
    },
    {
      "exactHosts": ["cdn.example.com"],
      "exactPaths": [],
      "pathPrefixes": ["/images/"],
      "credentialed": false,
      "userAgent": null
    }
  ]
}
```

Every credentialed resource host must also appear in `auth.exactHosts`. Rules cannot overlap, and each resource URL and redirect must remain under a signed path prefix. NewsHub follows only bounded same-origin HTTPS redirects, never adds cookies or the signed User-Agent for a public rule, and never forwards OAuth authorization headers to resource requests.

## Declare authentication when required

Implement `AuthenticatedSource` when the Source uses web-cookie login. Return `AuthSpec.WebCookie` with the login URL, exact allowed hosts, cookie origins, cookie domains, and JavaScript setting.

Implement `validateSession` against a protected endpoint. Cookie presence alone does not prove a valid session. Throw `AuthenticationRequiredException` after the source identifies an unauthenticated response.

If the site binds cookies to a User-Agent value, implement `WebLoginUserAgentProvider`. Its value must exactly match every signed credentialed resource rule; NewsHub rejects the service at bind time when they differ.

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
# 看板搜尋與目錄更新

`Source.getBoardPage(BoardPageRequest)` 是推薦及搜尋的共用入口。空白文字回傳少量推薦；非空文字搜尋該來源目前可存取的討論區目錄，不得只搜尋推薦種子。來源有網站搜尋 API 時優先使用，否則可快取動態目錄 24 小時。排除交易、商城等不支援一般讀文的區域。

- `query.text`、`query.categoryId`：文字與分類為交集；不支援的組合應明確回報。
- `pageSize`：1–100，預設 30；不可截斷網站一頁後直接跳下一頁而漏項。
- `pageToken`：來源發出的 opaque continuation，須綁定查詢、分類、資料版本及登入情境。
- `refresh`：預設 false，true 僅用於第一頁，要求重新取得資料。
- `contextEpoch`：Host 發出的非機密情境識別；變更時來源必須清掉舊目錄與 continuation，不可用作網站授權。
- `BoardPage.catalogInfo`：可缺省的 `mode`、`updatedAtMillis`、`isStale`、`coverage`。舊來源缺省時表示未知；部分目錄必須回報 PARTIAL，更新失敗回退不可更新原時間。

先取得帶目前情境的第一頁再讀分類，避免回傳上一帳號的分類。目錄刷新必須可取消、single flight，最多 60 秒、50 次 HTTP request（包含 redirect）、同時 2 次 request。禁止背景無限爬取；登入過期不得回退受限快取。來源內記憶體目錄與 Host 近期結果快取是不同資料，不得混用。

支援登入的來源必須在 `catalogInfo.contextEpoch` 回傳已套用的 request 世代。Host 在發布前檢查回應；缺乏此確認的舊版登入來源會顯示更新提示。匿名舊來源仍可使用未知 metadata。來源收到 null epoch 時不得重用／回退帳號目錄，也不得覆寫另一個消費者的有效目錄；舊 Host 需要更新後才能可靠接續這類目錄分頁。

本機跨 repo 驗證先在 NewsHub 建立 `:extension-api:assembleDebug`，來源 repo 再傳 `-PnewshubDir=C:/Projects/NewsHub`。正式發布前必須將 `common.gradle`、`jvm-library.gradle`、`bundle.gradle`、`shared/broker-http/build.gradle` 的 API pin 更新為已發布且可讀的 immutable commit；本機 AAR 驗證不等於正式依賴或簽章發布完成。
