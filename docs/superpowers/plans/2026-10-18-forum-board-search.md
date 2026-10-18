# Forum Board Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 單一論壇頁與收藏選板頁都能搜尋完整可用看板，Mobile01 不再受固定 16 板限制。

**Architecture:** 沿用 board page IPC，增加相容的刷新與目錄資訊。Host 共用查詢控制器負責取消、分頁及權限情境隔離；各來源負責遠端搜尋或有界限的目錄快取。依協議、Host、來源、整合順序交付，兩個 repo 分別提交。

**Tech Stack:** Kotlin、Coroutines／StateFlow、Compose、Hilt、Gson、OkHttp、Jsoup、JUnit、Gradle Android modules。

**Spec:** [已核准規格](../specs/2026-10-18-forum-board-search-design.md)

## Global Constraints

- 空白查詢顯示少量推薦看板；非空查詢搜尋完整可用目錄。
- 輸入延遲 300ms 後查詢、清除後回到推薦、支援分頁。
- 目錄有效期 24 小時，提供手動更新；更新失敗可用舊目錄並標示時間。
- 登入、登出及切換帳號時重新取得符合目前權限的目錄。
- 可用範圍包含公開看板、目前帳號有權存取的登入限定看板；排除交易、商城等不能以一般討論串閱讀的區域。
- `pageSize` 預設 30，接受 1–100；超出回傳明確 invalid request。
- 單次目錄刷新額外限制總計 60 秒、最多 50 次 HTTP request、最多同時 2 次 request，無自動循環重試。
- 固定目前兩個 `main` 分支；不建立／切換 worktree 或 branch。保留 NewsHub 的 `.tmp/`、`.codex-remote-attachments/`，不得納入提交。
- 不部署、不發布 APK、不修改遠端信任目錄，不建立雲端資源。若需 dev server，交付前依既定規則停止；本計畫無需 dev server。
- 所有路徑除明示外相對 `C:/Projects/NewsHub`；`EXT/` 代表 `C:/Projects/extensions-source`。

## Review Focus

1. 相同 SignedIn 狀態下換帳號：session generation 必須改變，舊結果與下一頁 token 必須失效（Task 2、3）。
2. 網站頁面大於 UI pageSize：頁面串接不得遺漏剩餘項目（Task 6）。
3. 部分分類抓取失敗或刷新達上限：不能把部分結果標成完整或無結果（Task 5、7）。
4. 不識別新欄位的舊 extension：不能虛構刷新成功或完整性（Task 1、4）。
5. 搜尋完成前登出／來源被隔離：晚到回應不可再出現，也不可恢復受限快取（Task 2、3、8）。

## 檔案與責任

| 範圍 | 修改／新增檔案 | 責任 |
| --- | --- | --- |
| 協議 | `extension-api/.../model/BoardCatalog.kt`、`ExtensionProtocol.kt`；`extension-loader/.../RemoteSource.kt` | 新欄位、wire 正規化、情境識別 |
| 登入情境 | `app/.../auth/SourceSessionManager.kt`；新增 `BoardCatalogContext.kt` | canonical identity 與非機密 session generation |
| 查詢控制器 | 新增 `app/.../ui/boards/BoardCatalogController.kt` | 單一來源查詢、取消、分頁、刷新及回退 |
| Host 快取 | `app/.../ui/collection/BoardCatalogCache.kt` | 情境隔離、舊資料清理與時間資訊 |
| UI | `BoardsScreen.kt`、`BoardsViewModel.kt`、`BoardPickerScreen.kt`、`BoardPickerViewModel.kt` | 兩個入口共用契約與狀態 |
| 來源共用 | 新增 `EXT/shared/broker-http/.../BoardCatalogSnapshotCache.kt` | 24 小時記憶體目錄、single flight、刷新預算與 token |
| Mobile01 | `EXT/src/mobile01-source/.../Mobile01BoardCatalog.kt`、`Mobile01Source.kt`、`Mobile01RequestBuilder.kt`；新增 `Mobile01CatalogParser.kt` | 動態 leaf board 目錄、登入、可信網址及開板 |
| Gamer | `EXT/src/gamer/.../gamer/GamerSource.kt`、`gamer_api/interactor/GetBoardPage.kt` | 遠端搜尋、無漏項分頁及分類語意 |
| EYNY | `EXT/src/eyny-source/.../EynySource.kt`、`EynyParser.kt`、`EynyGateway.kt` | 目錄刷新與部分結果 |

表中的 `...` 是既有 package 前綴；各 Task 下列明新增檔案完整路徑。不得為方便而把來源網頁解析移入 Host。

## Task 1：相容的 board page 協議

**Files:** 修改 `extension-api/src/main/java/tw/kevinzhang/extension_api/model/BoardCatalog.kt`、`extension-api/src/main/java/tw/kevinzhang/extension_api/ExtensionProtocol.kt`、`extension-loader/src/main/java/tw/kevinzhang/extension_loader/RemoteSource.kt`。新增 `extension-api/src/test/java/tw/kevinzhang/extension_api/BoardCatalogContractTest.kt`。

**Interfaces:** 保留既有 `Source.getBoardPage(request): BoardPage`。新增 `BoardPageRequest.refresh: Boolean = false`、`contextEpoch: String? = null`，以及 `BoardPage.catalogInfo: BoardCatalogInfo? = null`。`contextEpoch` 是規格要求的非機密登入情境識別；Host 產生，來源僅比較，不作為授權。

`BoardCatalogInfo(mode: BoardCatalogMode, updatedAtMillis: Long?, isStale: Boolean, coverage: BoardCatalogCoverage)`；mode 為 REMOTE_SEARCH／CATALOG／UNKNOWN，coverage 為 AVAILABLE／PARTIAL／UNKNOWN。UNKNOWN 是 wire 相容 fallback，不是新增資料取得策略。

- [ ] 寫 failing tests：舊 request 缺省 refresh 為 false、舊 page metadata 為 null；顯式 null 與未知 enum 安全正規化；新欄位往返保留；pageSize 0、101 拒絕，1、30、100 接受；refresh 加非空 token 拒絕。
- [ ] 執行 `./gradlew.bat :extension-api:testDebugUnitTest --tests '*BoardCatalogContractTest'`，確認失敗原因是尚未實作的契約。
- [ ] 實作模型與 boundary decode 驗證。Gson adapter 明確套用缺省與範圍檢查；舊 page 沒有 metadata 時保留未知，不補現在時間。舊來源忽略 contextEpoch／refresh 仍可回應，Host 不宣稱其支援刷新。
- [ ] 同一指令須成功，再執行 `./gradlew.bat :extension-api:assembleDebug :extension-loader:testDebugUnitTest`；預期 BUILD SUCCESSFUL。
- [ ] 只提交此 Task 檔案：`feat(extension-api): 擴充看板搜尋更新與目錄資訊契約`。

## Task 2：登入情境與安全快取

**Files:** 修改 `app/src/main/java/tw/kevinzhang/newshub/auth/SourceSessionManager.kt`、`app/src/main/java/tw/kevinzhang/newshub/ui/collection/BoardCatalogCache.kt`。新增 `app/src/main/java/tw/kevinzhang/newshub/ui/boards/BoardCatalogContext.kt`、`app/src/test/java/tw/kevinzhang/newshub/ui/boards/BoardCatalogContextTest.kt`、`app/src/test/java/tw/kevinzhang/newshub/ui/collection/BoardCatalogCacheTest.kt`。

**Interfaces:** `BoardCatalogContext(identityKey: String, epoch: String, persistAllowed: Boolean)`。`SourceSessionManager.catalogContexts: StateFlow<Map<String, BoardCatalogContext>>`；身分 key 使用既有 canonical identity hash。cache 改成 `get(context, query): CachedBoardResult?`、`put(context, query, result)`、`invalidate(identityKey)`；`CachedBoardResult(boards, catalogInfo, storedAtMillis)`。

- [ ] 寫 tests：相同 Source ID 不同 signer／repository 不共用；SignedIn→SignedIn 的新 WebView cookie 登入完成事件換 epoch；logout、expired、rebind 換 epoch；普通內容請求的 cookie 更新不反覆換 epoch。
- [ ] 寫 cache tests：受限情境只記憶體保存；舊裸 Source ID persisted entries 清除；query／category 隔離；回退不更新原時間；維持既有最多 20 筆、每筆 90 boards 邊界。
- [ ] 執行 `./gradlew.bat :app:testDebugUnitTest --tests '*BoardCatalogContextTest' --tests '*BoardCatalogCacheTest'` 確認紅燈，再實作事件與快取。
- [ ] epoch 用程序啟動唯一值加遞增世代，不能僅用 AuthState 或帳號名稱。對需要登入或具 auth capability 的來源，首版 conservatively 將整個登入情境結果視為不可持久化；無法辨識單一 board 的公開性時不猜測。
- [ ] 重跑同指令成功，確認 credentials 沒有寫入 cache key／log，提交：`fix(boards): 依來源身分與登入情境隔離看板快取`。

## Task 3：共用查詢與分頁狀態

**Files:** 新增 `app/src/main/java/tw/kevinzhang/newshub/ui/boards/BoardCatalogController.kt`、`app/src/test/java/tw/kevinzhang/newshub/ui/boards/BoardCatalogControllerTest.kt`；修改 `BoardsViewModel.kt` 與 `BoardPickerViewModel.kt` 的既有完整 package 路徑。

**Interfaces:** `BoardCatalogController(source: Source, context: BoardCatalogContext, cache: BoardCatalogCache, scope: CoroutineScope)`，提供 `state: StateFlow<BoardCatalogState>`、`setQuery(query: BoardQuery)`、`refresh()`、`loadMore()`、`retry()`、`close()`。state 包含 query、boards、catalogInfo、firstPageLoading、moreLoading、firstPageFailure、moreFailure、nextPageToken、isRecentFallback。一個 controller 對應一個 source/context。

- [ ] 用 fake Source、test scheduler 寫 tests：299ms 不查、300ms 查；空白立即推薦；a→ab 晚到 a 不覆蓋；清除取消；loadMore single flight；後頁失敗保留結果／原 token；refresh=true 只出現在第一頁。
- [ ] 寫 tests：context 更換 close 舊 controller，舊 reply 不寫 cache；source quarantine 清空；舊 token 失效顯示重新載入；新 page 以 URL 去重但不以去重後筆數猜測 next token。
- [ ] 執行 `./gradlew.bat :app:testDebugUnitTest --tests '*BoardCatalogControllerTest'` 確認失敗，再實作控制器。
- [ ] `BoardsViewModel` 與收藏 picker 各持有每來源 controller，改用 contexts flow 決定生命週期。收藏保持跨來源；單一來源失敗不取消其他來源。免登入判定使用 needsLogin，不能僅以 `source is AuthenticatedSource` 阻擋公開查詢。
- [ ] 先送出含目前 contextEpoch 的第一頁，再取得該情境的分類；禁止沿用現有先讀分類才查頁面的順序，避免來源尚未清掉舊帳號目錄就回傳分類。情境更換時 Host 分類快取同時失效。
- [ ] 重跑新 tests 及既有 `*BoardsViewModelCancellationTest`、`*BoardPickerViewModelTest` 成功；提交：`feat(boards): 共用可取消的看板搜尋與分頁狀態`。

## Task 4：單一論壇搜尋介面與收藏選板整合

**Files:** 修改 `app/src/main/java/tw/kevinzhang/newshub/ui/boards/BoardsScreen.kt`、`app/src/main/java/tw/kevinzhang/newshub/ui/collection/BoardPickerScreen.kt`、`app/src/test/java/tw/kevinzhang/newshub/ui/boards/BoardsScreenTest.kt`。新增 `app/src/androidTest/java/tw/kevinzhang/newshub/ui/boards/BoardSearchInteractionTest.kt`。

**Interfaces:** 既有 `BoardGroupDetailScreen` 消費 Task 3 state 與 actions。只搜尋當前 source；固定標題／搜尋區下方使用 LazyColumn。沿用現有開網站與收藏 callbacks，不繞過 scoped handles。

- [ ] 寫 tests：輸入、clear、推薦／搜尋標題、首／後頁錯誤、載入更多按鈕、PARTIAL／stale timestamp、舊來源未知 metadata；概覽文案改為「查看推薦看板」，不可把載入數當全站總數。
- [ ] 按核准 ASCII 配置搜尋欄、清除、更新按鈕及結果。用 saveable query／列表狀態保留返回位置，但 context 變更清除；目前查詢結果不可因鍵盤出現丟失。
- [ ] 執行 `./gradlew.bat :app:testDebugUnitTest --tests '*BoardsScreenTest'`，預期 PASS；以本機可用裝置執行 `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=tw.kevinzhang.newshub.ui.boards.BoardSearchInteractionTest`，測手機、寬視窗與 IME。
- [ ] 無裝置時保留測試、回報裝置驗證尚未執行；不得宣稱視覺驗收完成。提交：`feat(boards): 新增論壇看板搜尋與更新介面`。

## Task 5：目錄快取與 Mobile01 動態看板

**Files:** 新增 `EXT/shared/broker-http/src/main/kotlin/tw/kevinzhang/newshub/extension/runtime/BoardCatalogSnapshotCache.kt` 及同 package test。修改 `EXT/src/mobile01-source/src/main/kotlin/tw/kevinzhang/newshub/extension/mobile01/` 下的 `Mobile01BoardCatalog.kt`、`Mobile01Source.kt`、`Mobile01RequestBuilder.kt`、`Mobile01UrlPolicy.kt`；新增 `Mobile01CatalogParser.kt`。擴充既有 `Mobile01BoardCatalogTest.kt`、`Mobile01SourceTest.kt`、`Mobile01SourceContractTest.kt` 及 sanitized HTML fixtures。

**Interfaces:** `BoardCatalogSnapshotCache<T>(nowMillis: () -> Long)` 提供 `suspend get(epoch: String?, refresh: Boolean, loader: suspend (CatalogRefreshBudget) -> CatalogSnapshot<T>): CatalogSnapshot<T>` 與 `invalidate()`。`CatalogSnapshot<T>(value, version: String, updatedAtMillis: Long, coverage: BoardCatalogCoverage, isStale: Boolean)`。`CatalogRefreshBudget` 的 `suspend fetch(block: suspend () -> T): T` 統計每個 HTTP request（含 redirect），總 deadline 60 秒、50 requests、Semaphore(2)。保留完整度不低於目前資料的 snapshot；失敗回退標 stale，不覆寫時間。相同 epoch single flight，不同 epoch 立即失效。

- [ ] 先以既有 fixtures／唯讀少量網站請求確認導覽、分類、登入入口與可閱讀 leaf board 結構。儲存去機密 fixture，不存 cookie／帳號。若只有登入後才可取得完整目錄，須明確顯示登入要求，不以擴大公開清單充當登入支援。
- [ ] 寫 cache tests：24h 邊界、refresh、單次更新、不同 epoch、取消、50／51 request、60 秒 deadline、PARTIAL 與 stale 回退。寫 parser tests：至少兩個原 16 板之外的合法看板可搜尋；非 leaf／交易／商城排除；多分類重複去重；缺失分類標 PARTIAL。
- [ ] 執行 `./gradlew.bat -PnewshubDir=C:/Projects/NewsHub :shared:broker-http:testDebugUnitTest :src:mobile01-source:testDebugUnitTest`（cwd EXT），確認失敗後實作。
- [ ] 將原 16 板降為推薦種子，從可信目錄搜尋 name／description；安全保留 canonical board URL 與收藏重開能力，移除固定 16 板門檻。token 編碼 query／category／snapshot version／epoch／offset，變更後拒絕舊 token。
- [ ] Mobile01 改成 AuthenticatedSource、needsLogin=false，登入入口／cookie origins／credentialed rules 僅採前一步驗證的精確網站範圍。更新 Host code-owned Mobile01 policy 與對應 `ExtensionIsolationE2ETest.kt` 測試；禁止修改遠端 signed targets 或放寬任意 host。新增登入情境切換與匿名公開查詢測試；遇 challenge 沿用明確錯誤，不新增繞過能力。
- [ ] 重跑上述 tests 成功；分 repo 提交 Mobile01 與必要 Host policy，訊息：`feat(mobile01): 支援動態看板目錄與登入情境搜尋`。登入真站驗證無合法 session 時仍列待驗證，不視為已完成該驗收。

## Task 6：Gamer 無漏項遠端搜尋

**Files:** 修改 `EXT/src/gamer/src/main/kotlin/tw/kevinzhang/newshub/extension/gamer/GamerSource.kt`、`EXT/src/gamer/src/main/kotlin/tw/kevinzhang/gamer_api/interactor/GetBoardPage.kt`；擴充 `EXT/src/gamer/src/test/kotlin/tw/kevinzhang/gamer_api/interactor/GetBoardPageTest.kt`、`.../newshub/extension/gamer/GamerSourceContractTest.kt`。

**Interfaces:** 保留 `GamerApi.searchBoards(query, page)`。來源 token 改為 query／category／epoch／remotePage／offset；BoardPage metadata mode=REMOTE_SEARCH、coverage=AVAILABLE（僅限網站成功回應），時間為這批遠端資料實際取得時間。

- [ ] fake API 固定回傳每頁 30 筆；測 UI pageSize=1、30、100 的完整遍歷結果恰等於遠端序列，沒有遺漏／重複；第二頁失敗重試不能跳過剩餘項目。
- [ ] 測 query+category 無網站支援時明確 UnsupportedOperationException（映射既有錯誤），不得靜默忽略；可正確實作分類交集時才提供成功回應。測 epoch／query 變更拒絕舊 token。
- [ ] 執行 `./gradlew.bat -PnewshubDir=C:/Projects/NewsHub :src:gamer:testDebugUnitTest` 確認紅燈；實作有界限取數、保留遠端頁 offset，單次回傳不超過要求大小，不以本頁 UI 筆數判斷遠端結尾。
- [ ] 重跑成功；提交：`fix(gamer): 保留看板搜尋分頁剩餘項目與條件語意`。

## Task 7：EYNY 目錄刷新與權限切換

**Files:** 修改 `EXT/src/eyny-source/src/main/kotlin/tw/kevinzhang/newshub/extension/eyny/EynySource.kt`、`EynyParser.kt`、`EynyGateway.kt`；擴充同模組 `EynySourceContractTest.kt`、`EynyParserTest.kt`、`EynyGatewayTest.kt` 及 fixtures。

**Interfaces:** 使用 Task 5 snapshot cache。首頁與必要分類的每次網路請求均通過同一刷新 budget；查詢結果提供 CATALOG metadata 與 snapshot 綁定的 token。

- [ ] 寫 tests：首頁可見看板搜尋、分類展開、部分分類 IOException、刷新成功復原、24h 過期、手動更新、query／category 交集、同名不同 URL、session 更換與 token 失效。
- [ ] 執行 `./gradlew.bat -PnewshubDir=C:/Projects/NewsHub :src:eyny-source:testDebugUnitTest` 確認紅燈；替換無期限記憶體 catalog。分類失敗保留部分資料但標 PARTIAL，空資料與 HTML challenge 不當正常空結果。
- [ ] AuthRequired／Expired 清除受限目錄並上拋，不能走 stale 受限回退；非授權失敗才回退同 epoch 目錄。
- [ ] 重跑成功；提交：`feat(eyny): 加入看板目錄更新與部分結果狀態`。

## Task 8：版本、相容性與整合交付

**Files:** 更新 `EXT/src/mobile01/build.gradle.kts`、`EXT/src/gamer/build.gradle.kts`、`EXT/src/eyny/build.gradle.kts` 的既有 versionCode 各加 1，對應 versionName 與 Source.version 遵循現行規則。檢查 `EXT/common.gradle`、`EXT/jvm-library.gradle`、`EXT/bundle.gradle` 的 extension-api 依賴。更新 `docs/extension-bundles.md`、`docs/third-party-extension.md`；新增 `docs/verification-2026-10-18-board-search.md`。

- [ ] 重新產生 Host API AAR，再用 `-PnewshubDir=C:/Projects/NewsHub` 建三個本機 debug APK；記錄兩個 repo SHA 與 AAR／APK hash。不能把過時本機 AAR 當作成功證據。
- [ ] 生產依賴先記錄需更新的 immutable Host commit；本次不推送／發布 JitPack。只有該 commit artifact 確實可讀時才更新三個 Gradle 腳本的 pin，否則清楚列為發布前必要條件，不宣稱來源 CI 已通過。本機覆寫參數可驗證產品變更，不等於發布完成。
- [ ] Host 執行 `./gradlew.bat :extension-api:testDebugUnitTest :extension-loader:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`；EXT 執行 `./gradlew.bat -PnewshubDir=C:/Projects/NewsHub :shared:broker-http:testDebugUnitTest :src:mobile01-source:testDebugUnitTest :src:mobile01:testDebugUnitTest :src:gamer:testDebugUnitTest :src:eyny-source:testDebugUnitTest :src:eyny:testDebugUnitTest :src:mobile01:assembleDebug :src:gamer:assembleDebug :src:eyny:assembleDebug`。每次命令設有限 15 分鐘 deadline、失敗不自動重試；先處理具體錯誤才重跑受影響範圍。
- [ ] 舊 Host＋新 extension、新 Host＋舊 extension、新 Host＋新 extension wire fixture 測試通過；不相容時須在發布前修正，不能依賴所有使用者同時更新。檢查 Source quarantine／session 切換時 UI、cache、token 三者共同失效。
- [ ] 完成本機測試後，真站最小 smoke 每來源最多兩個 query、兩頁、一次開板，總計 5 分鐘／50 requests，無自動重試；遇 rate limit／challenge 即停止。記錄 Mobile01 兩個新增 leaf board 的實際 ID；登入測試僅用使用者已有合法 session，不輸出 session 資料。
- [ ] 如裝置未安裝／信任本機 debug extension，不修改正式 trust root；沿用現有測試 fixture 或 instrumented 測試。需要簽章／裝置設定而無法完成時回報精確缺口，勿宣稱端到端完成。
- [ ] 按規格 10 項逐一紀錄 PASS／FAIL／NOT RUN 及證據。檢查兩 repo `git diff --check`、status 與提交內容，完成各 repo 中文 Conventional Commits。交付包含 commit、驗證結果、尚待發布／裝置核對事項；無新增持續計費資源。

## 自我檢查與執行順序

規格驗收對應：1→Task 5／8；2→Task 6；3→Task 7；4→Task 3／4；5→Task 3／4；6→Task 5；7→Task 2／3／5／7；8→Task 1／8；9→Task 5／6／7；10→Task 4／8。

依 Task 1→2→3→4→5→6→7→8 執行；Task 5 的共用快取先於 EYNY。使用者已核准由主代理在目前 session 依序實作；完成一次獨立唯讀審查並修正四項 Important。

### 執行結果（2026-10-18）

原始 checkbox 保留作為逐項驗收清單，未將外部條件未完成的整體任務誤標為完成；實際狀態如下，證據見 [驗證紀錄](../../verification-2026-10-18-board-search.md)。

| 任務 | 實際狀態 |
| --- | --- |
| 1 協議 | 已實作與本機測試通過；追加 response contextEpoch acknowledgement 及更新提示 |
| 2 身分／快取 | 已實作與本機測試通過；所有近期結果採有界限記憶體快取 |
| 3 共用控制器 | 已實作與本機測試通過；新增 pool 僅替換受影響來源 |
| 4 搜尋介面 | 已實作，控制項裝置測試通過；整頁寬視窗／真站驗收尚未完成 |
| 5 Mobile01 | 公開動態目錄與離線推薦已實作、fixture 通過；真站 DOM／登入限定範圍未完成 |
| 6 Gamer | 已實作與本機測試通過，含交錯查詢與不漏項分頁 |
| 7 EYNY | 已實作與本機測試通過，含部分分類、更新與情境切換 |
| 8 整合 | 本機 API／三來源 APK 建置通過；正式 artifact pin、簽章、政策與發布未執行 |

審查裁定：缺乏 response epoch 確認的舊版登入來源須更新，不能為相容而發布可能屬於前一帳號的資料。null epoch 查詢不重用／回退目錄，也不覆寫有效帳號 snapshot。這項裁定優先滿足規格的權限隔離，舊 APK 實機組合仍須在發布前驗證。
