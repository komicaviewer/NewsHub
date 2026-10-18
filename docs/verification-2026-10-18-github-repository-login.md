# GitHub repository login 驗收紀錄

日期：2026-10-18。工作目錄 `C:/Projects/NewsHub`，沿用 `main`；產品改動基準 `b1cedd3`。未切換 branch、未 push／發布。

## 外部設定

GitHub 組織 `komicaviewer` 已成功建立 NewsHub OAuth App `3942641`；設定頁直接確認 owner、公開 Client ID、Device Flow checked。未產生 client secret、未變更其他 Apps、未代替使用者授予 repo 權限。

App 管理頁：https://github.com/organizations/komicaviewer/settings/applications/3942641

建立證據截圖留在本機忽略目錄 `.tmp/github-login/oauth-app-created.jpg`，不提交使用者瀏覽器截圖。

## 分層驗證

- Marketplace JVM：子目錄與 ref 編碼、路徑邊界、HTTP 狀態、signed metadata、登出期間拒絕傳回 bytes、下載至 installer 的取消 lease、IO 返回前取消時刪除檔案。
- App JVM：Device Flow pending／slow_down／拒絕／到期，帳號 vault 邊界、登出後 lease 失效、遲到登入回應不保存、分頁搜尋、rate limit、session 變更清除私有結果、descriptor 舊資料 migration、來源確認與提交後取消的回滾。
- Android 12／ASUS_I002D：`GitHubRepositoryPickerInstrumentedTest` 1 PASS（fake HTTP），驗證搜尋、選取預設分支、登出清除清單；`GitHubAccountVaultInstrumentedTest` 2 PASS，驗證實際 Keystore 加密／損毀清除，以及真實 GitHub device/code endpoint 接受已配置的 App。
- 裝置測試使用臨時 Gradle init script 的 `.githublogin` applicationId suffix，沒有卸載／覆蓋原本 App。真實端點測試不輪詢 token，不進行真人授權，不記錄 device code。
- Debug APK 已成功組建。最終完整測試結果在下節記錄，不將環境失敗當作全部通過。

裝置測試真實端點必須明確加 `-Pandroid.testInstrumentationRunnerArguments.githubLiveDeviceCodeSmoke=true`；一般測試不自動呼叫此端點。

## 獨立審查與修正

一次獨立唯讀審查以 `b1cedd3..3725be0` 為範圍，找到來源確認與 APK 交接的取消問題；已新增重現測試後修正：

1. DataStore 已提交但返回前登出：在 NonCancellable 清理中移除未交付的持久來源、runtime、bootstrap 與憑證；discarded domain 防止延遲觀察事件重新註冊。
2. 已寫出 APK 但 IO 結果尚未返回：由知道目的檔案的作用域捕捉 dispatcher cancellation，刪除檔案。每次下載使用獨立檔名，避免取消一筆下載刪除另一筆的結果。
3. 沿用 DEBUG HTTP logger 可能記錄私有 URL：改用獨立 `repositoryHttpClient`。原審查列為 Minor；依 spec 對私有資料紀錄的限制提升為必要修正，一併驗證 client 無 logger。

另在全套測試找到目錄 collector 啟動時可能取消第一個 query，已以可重現排程修正。重新登入成功的來源會完成實際 metadata refresh，而非只清除 UI 警告。

## 未完成的真實帳號驗收

最後完整 JVM 驗證：Marketplace **38/38 PASS**；App **195/196 PASS**。唯一失敗是既有 `ExtensionHealthOutputDirectoryTest.acceptsOnlyNormalizedDescendantsOfFirebasePullRoots`，在 Windows 使用 POSIX `File` 路徑被判定不是 absolute；與本功能無關，前次論壇看板搜尋驗收亦已記錄。GitHub／ManageRepos／來源持久化定向測試 **23/23 PASS**。不能宣稱整個 App suite 全綠。

尚未由使用者本人完成 Device Flow 的 repo 授權。因此私有 repo／受組織政策影響的真實清單、實際加入 signed repo 與跨帳號恢復，目前由 fake HTTP／單元測試涵蓋，不能宣稱已對真人帳號端到端驗證。

使用者可安裝 Debug APK，在管理來源完成「登入 GitHub」，選擇一個有效的簽署 extensions repo，依序驗證加入、登出暫停、重新登入恢復。這不需要提供 token 或密碼給 agent。

## 成本與執行生命週期

本次無雲端部署、付費 build、dev server 或持續計費資源；只建立 GitHub OAuth App 與執行本機／連接裝置測試。沒有常駐付費容量。
