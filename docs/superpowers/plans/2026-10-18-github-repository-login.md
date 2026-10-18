# GitHub Repository Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在管理來源加入單一 GitHub 帳號登入、可讀 repo 搜尋與選取，以及登出暫停／重新登入自動恢復更新。

**Architecture:** Device Flow 與帳號加密 session 放在 app 的獨立 github package；Marketplace 僅認識 credential binding、來源分支／目錄及可取消的存取資格。沿用現有 GitHub Contents transport 與根金鑰確認，不把 OAuth token 複製成每個來源的 PAT。

**Tech Stack:** Kotlin、coroutines／Flow、OkHttp、Gson、Compose、Hilt、DataStore、現有 EncryptedJsonStore 與 JVM／Android 測試。

**Spec:** `docs/superpowers/specs/2026-10-18-github-repository-login-design.md`

## Global Constraints

- 同時一個 GitHub 帳號；Device Flow，不在 APK 放 client secret、不新增登入後端。
- 保留公開 HTTPS 與手動 GitHub private／PAT 流程。
- 登出後保留來源及已安裝 extensions，登入來源停止更新；重新登入後確認讀取權限，有權限者自動恢復。
- 不把使用者手動停用或撤銷的來源改回 ACTIVE。
- 優先使用 API 回傳的 default_branch 與根目錄；檢查失敗可修改分支、子目錄後重試。
- 只在目前 `C:/Projects/NewsHub` 的 `main` branch 工作；不建立、切換或 reset branch／worktree。每次改檔前確認 status。
- 無雲端資源、無 app 發布、無 extensions 發布。使用者本人完成實際 GitHub 登入授權。
- 目前未追蹤的 `.tmp/`、`.codex-remote-attachments/` 不納入提交。

## Review Focus

1. repo default branch 包含 `/`、非 ASCII 子目錄與百分比編碼：一次正確編碼，來源邊界不得逃逸（Task 1）。
2. 私有 repo 名稱或 token 意外出現在錯誤／saved state：secret redaction，登出清除搜尋內容（Tasks 2、3、5）。
3. 第 N 頁才有搜尋結果或組織政策排除 repo：不提前顯示「無結果」，不以公開全站搜尋補列（Task 3）。
4. 登出同時完成根確認或 APK 下載：舊 generation 不得提交來源或產出可安裝檔案（Task 4）。
5. 同 repo 已透過 PAT 加入、或曾被手動撤銷：不靜默換綁、不復活舊來源（Tasks 4、5）。

## 檔案責任與共同型別

實作前讀取 spec、相關既有測試及各目錄適用規則。下列 app 新檔根路徑為 `app/src/main/java/tw/kevinzhang/newshub`；測試使用相同 package，位於 `app/src/test/java`。

- `github/GitHubOAuthClient.kt`：固定官方 endpoints、Device Flow 回應與有限輪詢。
- `github/GitHubAccountSession.kt`：登入生命週期、單一帳號與 generation。
- `github/GitHubAccountVault.kt`：帳號 token 加密保存及清除。
- `github/GitHubRepositoryCatalog.kt`：帳號 repo 分頁、搜尋與 API 錯誤。
- `repo/AccountRepositoryCredentialStore.kt`：路由 PAT／OAuth、存取資格及 generation lease。
- `ui/marketplace/GitHubRepositoryPicker.kt`：登入與 repo 選取 Compose 內容。
- 既有 `ManageReposViewModel.kt`／`ManageReposScreen.kt`：串接新增入口、根預覽、分支／路徑及來源狀態。
- `di/AppModule.kt` 與新增 `di/GitHubRepositoryModule.kt`：綁定；`app/build.gradle`：公開 Client ID 設定。
- marketplace 的 `RepositoryAccess.kt`／`RepositoryContentTransport.kt`：credential binding、子目錄及 request lease。
- `MarketplaceRepositoryImpl.kt`：inspection、確認、metadata、download 全路徑套用 lease。
- `RepoRepositoryImpl.kt`：descriptor 持久化與舊 PAT migration。

共同新型別：`GitHubAccount(id: Long, login: String)`、`GitHubRepo(id: Long, owner: String, name: String, defaultBranch: String, private: Boolean, description: String?)`、`GitHubRepoPage(items: List<GitHubRepo>, nextPage: Int?)`。Secret 使用既有 redacted `RepositoryAccessCredential`，不可用 data class 裸字串 token。

Marketplace descriptor 新增 `credentialBinding: RepositoryCredentialBinding`（`NONE`、`MANUAL_PAT`、`GITHUB_ACCOUNT`）與 `repositoryPath: String`（預設空）。OAuth 與 PAT 仍使用 `GITHUB_CONTENTS`，避免混淆 transport 與登入方式。

## Task 1: 來源 descriptor、目錄邊界與舊資料相容

**Files:** 修改 `marketplace/src/main/java/tw/kevinzhang/marketplace/RepositoryAccess.kt`、`RepositoryContentTransport.kt`，app 的 `repo/RepoRepositoryImpl.kt`；擴充 `RepositoryContentTransportTest.kt` 與 app 的 RepoRepositoryImpl 對應測試。

**Interfaces:** 保留 `githubContents(revision: String = "main")` 相容入口；擴充為 `githubContents(revision: String = "main", repositoryPath: String = "", credentialBinding: RepositoryCredentialBinding = MANUAL_PAT)`。解碼舊 public 為 NONE、舊 GitHub 為 MANUAL_PAT。

- [ ] 寫失敗測試：舊 GitHub JSON 解碼後 `credentialBinding == MANUAL_PAT` 且 path 空；default branch `release/v1` 維持單一 ref；`套件/repo` 子目錄的 metadata URL 正確編碼；`../`、`%2e%2e`、反斜線與外部 URL 被拒絕。
- [ ] 執行 `:marketplace:testDebugUnitTest --tests '*RepositoryContentTransportTest'` 與 app RepoRepositoryImpl 定向測試，確認新增 assertions 因功能缺失而失敗。
- [ ] 實作 descriptor、明確欄位編解碼與 repo 內 path 拼接。所有 URL 仍是固定 GitHub repo 邊界；root、metadata、APK 都使用同一 repositoryPath，不改信任驗證流程。
- [ ] 重跑定向測試，確認既有 manual PAT／public transport assertions 仍通過。
- [ ] 提交：`feat(github): 擴充來源憑證綁定與子目錄設定`。

## Task 2: Device Flow 與單一加密帳號 session

**Files:** 新增上述三個 OAuth／session／vault 檔與 `GitHubOAuthClientTest.kt`、`GitHubAccountSessionTest.kt`；修改 `app/build.gradle`、新增 `di/GitHubRepositoryModule.kt`。檢查現有 backup rules，排除新 vault。

**Interfaces:** `GitHubOAuthClient.start(): GitHubDeviceChallenge`（userCode、verificationUri、expiresInSeconds、intervalSeconds，device code 為 redacted secret）；`awaitAuthorization(challenge): RepositoryAccessCredential`；`GitHubAccountSession.state: StateFlow<GitHubSessionState>`；`login(): Unit`、`cancelLogin(): Unit`、`logout(): Unit` 為 suspend；`currentCredential(): RepositoryAccessCredential?`、`generation: Long` 供 Task 4 使用。session 成功前以 `/user` 驗證帳號 identity。

- [ ] 寫失敗測試：pending→success；slow_down 延長間隔；denied／expired 停止；取消不再請求；登出後遲到的成功回應不保存；vault 解密失敗 fail closed；錯誤及 toString 不含 secret。
- [ ] 執行 `:app:testDebugUnitTest --tests '*GitHubOAuthClientTest' --tests '*GitHubAccountSessionTest'`，確認新增功能尚未存在。
- [ ] 核對官方 Device Flow 文件；依 response interval／expires_in 與 coroutine clock 限時輪詢，每個 HTTP call 設 timeout，禁止 redirects；scope 使用已核准的 `repo`。權杖有 expiry 時安全到期需重新登入；本次不實作依賴 client secret 的 refresh。
- [ ] 以 EncryptedJsonStore 加密保存單一帳號與 credential；logout 清除 token、取消工作並遞增 generation。UI state 不含 device code 或 access token。新增 `GITHUB_OAUTH_CLIENT_ID` Gradle／environment 公開設定；空值呈現未設定狀態，不需任何 secret。
- [ ] 重跑上述測試，確認假時間與 fake HTTP 不需真實登入；提交 `feat(github): 新增裝置授權與單一帳號登入`。

## Task 3: 可讀 repo 分頁搜尋

**Files:** 新增 `github/GitHubRepositoryCatalog.kt` 與對應測試，必要時新增 `GitHubRepositoryPickerViewModel.kt` 封裝可取消的清單狀態。

**Interfaces:** `GitHubRepositoryCatalog.getAccount(credential): GitHubAccount`；`listRepositories(credential, page: Int): GitHubRepoPage`；`getRepository(credential, owner: String, name: String): GitHubRepo`。使用 `/user/repos`、`per_page=100`、owner/collaborator/organization_member affiliation；next page 只接受固定官方 endpoint 的已驗證 pagination。

- [ ] 寫失敗測試：搜尋項目在第二頁才出現；最後一頁前 `complete == false`，完成後才允許無結果；登出清空；重複 repo 依 id 去重；rate limit 保留已取得項目與 retry 提示；401 不退回匿名搜尋；description 為 null、非 main 分支解析成功。
- [ ] 執行 `:app:testDebugUnitTest --tests '*GitHubRepositoryCatalogTest' --tests '*GitHubRepositoryPickerViewModelTest'`，確認缺少功能造成失敗。
- [ ] 實作順序分頁與 session 內目錄快取；搜尋 owner/name／description，不對 GitHub 全站 search 發 query。瀏覽時手動載入更多；非空 query 可取消地逐頁掃描，對 query 變更重用已取目錄；只容許一個掃描工作，不自動重試 rate limit。
- [ ] 重跑定向測試；提交 `feat(github): 提供帳號可讀來源清單與搜尋`。

## Task 4: OAuth 綁定與更新生命週期

**Files:** 新增 `repo/AccountRepositoryCredentialStore.kt`；修改 `di/AppModule.kt`、`ManageReposViewModel.kt`、`MarketplaceRepositoryImpl.kt`、`RepositoryContentTransport.kt`、必要的 `RepositoryAccessStatusStore.kt`／MarketplaceViewModel 安裝交接；新增 app credential routing 測試與 marketplace logout race 測試。

**Interfaces:** `RepositoryCredentialLease` 提供 `credential`、`ensureCurrent(): Unit`、`cancelled: Job`；credential store 的 OAuth account-bound 路徑透過 lease 解析，不呼叫 PAT vault 的 saveCredential。若現有 provider 需擴充，為 manual/public 提供相容實作。`AccountRepositoryCredentialStore.revalidateRepositories(): Unit` 驗證各 account-bound 來源，輸出可用／缺授權／待重試，與 domain.state 分開。

- [ ] 寫失敗測試：logout 後 PAT 仍可讀、OAuth 不能開始請求；下載中登出取消 HTTP 並刪除未完成檔案；下載完成與安裝交接間 logout 不交付可安裝結果；根確認途中登出不新增 domain；不同帳號有權限可恢復，無權限仍暫停；403 rate limit 不當作永久拒絕；手動 SUSPENDED／REVOKED 不恢復。
- [ ] 執行定向 app credential tests 與 marketplace 對應測試，確認未有 lease 的競態測試失敗。
- [ ] 路由 manual 與 account credential；將 session job／generation 檢查延伸到 metadata、root inspection、confirmation、APK read 與最終檔案／安裝結果交接。單次 request 前後均檢查，非僅 UI disable。失敗 confirmation 清理不刪除整個帳號 token。
- [ ] 重新登入與 app session 恢復後逐一有限速率 revalidate，404 顯示不存在或不可存取；網路與 policy 狀態保持可重試。保存使用者原有 trust state。
- [ ] 重跑所有新增競態、既有根金鑰與下載驗證測試；提交 `feat(github): 綁定登入來源並處理登出與恢復更新`。

## Task 5: 管理來源 UI、repo 選取與金鑰確認

**Files:** 新增 `ui/marketplace/GitHubRepositoryPicker.kt`，修改 `ManageReposScreen.kt`、`ManageReposViewModel.kt` 與其現有測試；必要時新增 Android Compose instrumentation 測試。

**Interfaces:** 選取 callback 傳 `GitHubRepo`，ViewModel 產生以 defaultBranch、空 repositoryPath、GITHUB_ACCOUNT 綁定的 draft；不把 token 放進 UI。分支與目錄編輯會取消舊 inspection preview。既有 `confirmTrust()` 為唯一新增受信來源入口。

- [ ] 寫失敗測試：第三入口與設定缺失狀態；授權碼／取消／開啟官方頁；搜尋進度／載入更多；repo 預設分支；格式無效不保存且可修改 path；重複來源提示；金鑰確認前不能加入；登出後來源保留但停止更新；手動 PAT 不換綁。
- [ ] 執行 `:app:testDebugUnitTest --tests '*ManageRepos*' --tests '*GitHubRepositoryPicker*'`，確認新增 UI 狀態缺失造成失敗。
- [ ] 實作 UI 依 spec 草圖，按鈕允許窄螢幕換行；清楚顯示 OAuth repo scope 較廣、程式僅讀取；外部頁面使用系統瀏覽器，device challenge 只開啟已驗證官方 URI。401／組織政策／無效 metadata 使用不含憑證的訊息。
- [ ] 通過定向測試與最小 Compose device smoke；提交 `feat(github): 新增登入與來源選取介面`。

## Task 6: 註冊 OAuth App、整合驗收與交付

**Files:** `app/build.gradle` 的公開 Client ID 設定（若採固定公開預設值）；新增 `docs/github-repository-login.md`、`docs/verification-2026-10-18-github-repository-login.md`。

**Interfaces:** 實際 OAuth App 設定產出公開 Client ID，供 Task 2 配置；不產出或保存 client secret。

- [ ] 先執行完整本機受影響測試與 `:app:assembleDebug`；記錄既有環境失敗，不把其他測試失敗宣稱為本功能通過。修復本功能問題後才外部 smoke。
- [ ] 使用 browser 查看 komicaviewer 註冊頁與當下官方規則，填入 Name=`NewsHub`、Homepage=`https://github.com/komicaviewer/NewsHub`（先確認實際專案公開網址，若不同採已驗證網址）、Enable Device Flow；callback 使用相同已驗證 homepage（Device Flow 不使用 callback）。只有頁面允許的必要欄位，不設定新後端、webhook 或組織 repo 寫入操作。
- [ ] 使用者已明確授權建立 App；確認表單與 owner 後提交註冊，保存公開設定與 App 管理 URL，不記錄 secret。權限不足時報明確限制，繼續其餘本機驗收，不虛構完成註冊。
- [ ] 配置 Client ID 後執行最小真實 Device Flow；顯示登入碼及使用者授權入口，由使用者本人授權。驗收 private repo、組織限制與登出恢復；沒有授權時標記真實 smoke 尚待完成，local fake tests 與 build 可照常完成。
- [ ] 執行 `git diff --check`、定向 regression、最終 assembly；人工檢視 secret 邊界與 session race。執行方法若選 Native，依 executing-plans 技能做一次獨立最終 review；不派其他 agents 執行付費或外部修改。
- [ ] 將實際結果、無法完成的測試、OAuth 管理方式與撤銷入口寫入文件；提交 `docs(github): 記錄登入設定與整合驗收`。不 push／發布，無 dev server 與持續計費資源。

## 本機驗證指令與環境

在 PowerShell 設定 task 專用 Java／SDK 環境；不讀出全域 gradle.properties（含其他機器與 signing 設定）。

```powershell
$env:JAVA_HOME = 'C:/Projects/NewsHub/.superpowers/tools/java/jdk-17.0.20.1+1'
$env:ANDROID_HOME = 'C:/Projects/NewsHub/.superpowers/tools/sdk'
./gradlew.bat '-Dorg.gradle.java.home=C:/Projects/NewsHub/.superpowers/tools/java/jdk-17.0.20.1+1' :marketplace:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

每一 task 使用此環境執行自己的定向 task 與 `--tests`，單次命令設定有限 timeout；長測試以進度及完成狀態判斷，不盲目重跑。Android 測試依可用已啟動 emulator／device 執行，不覆蓋使用者正式 app 資料。

## 自我審查與執行方式

- Spec 的登入、repo 搜尋、分支／路徑、信任、登出／恢復、PAT 相容、秘密保護及驗收分別有 Tasks 1–6 對應。
- Review Focus 五類均已在 owning task 列出測試；共同 descriptor、session、catalog 與 lease 接口名稱一致。
- 本計畫完成不代表產品功能已實作或 OAuth App 已註冊。
- 建議 Native：由目前 agent 依序實作，共用 session／credential 接口跨任務相依密切；最後一次獨立 review，減少每個任務重建上下文。待使用者審閱計畫並選定執行方式後開始產品改動。
