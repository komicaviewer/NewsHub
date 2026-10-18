# GitHub 分步介面實作計畫

使用者已確認方案一及開始實作，於目前 main 分支執行。

目標：管理來源只顯示來源與新增入口；GitHub 分成連接、選取、檢查與信任確認。
架構：沿用現有 session、picker ViewModel 及 ManageReposViewModel；Compose 本地導覽狀態處理畫面，不更動 OAuth 與信任邊界。

- [x] 更新 UI 測試：帳號操作改為選單，選取列表不再折疊，搜尋名稱改為儲存庫。
- [x] ManageReposScreen 新增來源入口與返回導覽；accountMode 以選取階段及確認階段分開呈現。
- [x] GitHubRepositoryPicker 提供固定帳號、搜尋區與獨立 lazy 列表；登入與授權碼分階段顯示。
- [x] 進階設定折疊，保留錯誤重試及根金鑰確認。
- [x] 使用 dev init script 建置、執行相關測試、驗證 APK package 與簽章；可用裝置時視覺驗證。
- [x] 檢查 diff 並依中文 Conventional Commits 提交。

驗收重點：返回保留搜尋、切換帳號使選取失效、授權取消停止輪詢、清單未完整不誤報無結果、確認期間不能改選來源。

## 驗證結果

- dev assembleDebug / assembleDebugAndroidTest 成功，使用指定 dev init script。
- APK package：tw.kevinzhang.newshub.debug；APK 與既有 App 均為指定 debug.keystore 的 SHA-256。覆蓋安裝成功，保留既有帳號與資料。
- GitHub 與 marketplace 相關單元測試 22 項通過。
- GitHubRepositoryPickerInstrumentedTest 實機通過，涵蓋搜尋、選取預設分支、返回保留搜尋、帳號選單登出、清除結果。使用 fake HTTP/session，沒有登出真實帳號。
- 實機既有帳號 smoke：管理→新增→已登入清單→komicaviewer/extensions 確認頁→返回清單；目視檢查截圖。未重新授權或新增信任。
- 完整 app 單元測試：196 項，195 通過、1 失敗：ExtensionHealthOutputDirectoryTest.acceptsOnlyNormalizedDescendantsOfFirebasePullRoots。未修改的測試使用 /sdcard 等 Android 路徑，Windows File.isAbsolute / normalize 檢查失敗；未擴大本次範圍修改。
- 初次測試啟動受本機 Gradle Java 路徑阻擋，命令改用既有 JDK；未完成修改前的 red-green 驗證，不宣稱已完成測試先行證明。
- 唯讀獨立審查無重大發現；git diff --check 通過。
- 截圖：.tmp/github-login/redesign-login.png、redesign-review.png（本機暫存、不提交）。
- 未新增雲端資源，未啟動 dev server。
