# 論壇看板搜尋驗證紀錄

日期：2026-10-18。範圍：[規格](superpowers/specs/2026-10-18-forum-board-search-design.md)、[計畫](superpowers/plans/2026-10-18-forum-board-search.md)。

## 已執行

- NewsHub API：40 tests，0 failures；API AAR 建置成功。
- Host 看板相關 targeted suite：28 tests，0 failures；debug APK 建置成功。
- Host 完整 app suite：182 tests，181 pass；`ExtensionHealthOutputDirectoryTest.acceptsOnlyNormalizedDescendantsOfFirebasePullRoots` 在 Windows JVM 下對 Android POSIX absolute path 判定失敗。
- Loader 完整 suite：39 tests，38 pass；`InstalledExtensionVerifierTest.rejects symlink in place of installed base APK` 因 Windows 缺建立 symlink 權限而失敗。未放寬 verifier 或改動全域系統設定。
- 來源共用 broker／cache：13 tests，0 failures；Mobile01 source：37 tests，0 failures；Gamer：38 tests，0 failures；EYNY source：33 tests，0 failures。
- Mobile01、Gamer、EYNY 三個 debug APK 全部建置成功。來源編譯使用 `-PnewshubDir=C:/Projects/NewsHub` 本機 AAR。
- 來源 `source_host_contracts.py --fail-on-unresolved`：13 Sources 通過；`release_catalog.py validate`：7 APKs／13 Sources 通過；source host contracts Python tests：8 通過。
- 額外 release catalog Python tests：7 test methods，含 3 個失敗 subtests；原因為 Windows `NamedTemporaryFile` 開啟鎖定導致讀取 PermissionError，尚未調整無關測試。
- Android 裝置 ASUS_I002D／Android 12：`BoardSearchInteractionTest` 1/1 PASS，驗證輸入、清除、推薦／結果標題及更新動作。使用忽略追蹤的 Gradle init script 加 `.boardsearch` applicationId suffix，沒有卸載／覆蓋既有 App。

完整指令與 log 保存在本次 ignored workspace `.superpowers/sdd/2026-10-18-forum-board-search/`。Java／Android SDK 安裝在 `.superpowers/tools/`，不修改全域 Java／SDK 設定。未啟動 dev server。

## 規格驗收對應

| 項目 | 結果與限制 |
| --- | --- |
| 1 Mobile01 清單外看板 | 本機合成 HTML fixture 可找到原 16 板以外的 board、可通過網址／ID 驗證；真站兩板讀文與收藏尚未驗證 |
| 2 Gamer 遠端分頁 | fixture 下 UI pageSize=1／30／100 遍歷 43 筆恰為 3 次網站 API 請求，無漏項；關鍵字＋分類明確回報不支援 |
| 3 EYNY 部分目錄 | 分類 HTTP503→PARTIAL，手動更新復原→AVAILABLE，情境變更重新抓取，tests PASS |
| 4 單論壇介面 | controller debounce／late response／append retry 與裝置控制項 PASS；完整列表捲動及寬視窗視覺尚未驗證 |
| 5 收藏選板 | 共用控制器接線與既有 picker tests PASS；多來源真站整合尚未驗證 |
| 6 更新邊界 | 24h TTL、single flight、51st request 不執行、stale 原時間 tests PASS；可注入較短 deadline 的取消／禁止後續請求及 concurrency=2 測試 PASS，未等待滿 60 秒 |
| 7 登入情境 | epoch／identity 隔離與關閉後晚到回應 tests PASS；Mobile01 登入能力未加入，真帳號登入／切換尚未驗證 |
| 8 wire 相容 | 缺省／null／未知值／新資料往返 tests PASS；另有 legacy auth fake 的 epoch acknowledgement 拒絕測試及 null-epoch 不重用帳號目錄測試；歷史 APK 新舊實機組合尚未驗證 |
| 9 網址／token | Mobile01 非可信 host 排除、清單外合法 ID 接受，查詢綁定 token tests PASS；實際權限由網站與 broker 判定 |
| 10 分層驗證 | 本機 tests、三來源 debug build、控制項裝置測試已執行；真站 smoke 受阻，未放大重試 |

## 未完成的外部條件

Mobile01 首次唯讀 `forumlist.php` GET 收到 HTTP403，依規格停止真站探測，未嘗試繞過驗證。新增 parser 使用合成 directory fixture，未宣稱實際 DOM 已驗證。Mobile01 目前仍維持匿名來源；登入入口、cookie origins 及 credentialed network policy 未有可信網站證據，因此沒有猜測並新增登入權限。這是功能缺口，不能宣稱完整可用目錄及登入限定看板已交付驗收。

正式 API artifact／來源 dependency pin、簽章、signed repository policy 與 APK 發布未執行。既有已安裝 APK 不會自動取得本機變更；以舊 Mobile01 signed policy 執行新版目錄請求會被 broker 拒絕。不得把本機 debug 成功當作發布成功。

沒有建立雲端資源或新增持續計費資源。

## 獨立審查與修正

一次獨立唯讀審查提出 4 項 Important：登出發布 epoch 早於清 cookie、舊版來源忽略 epoch、後頁認證錯誤保留受限資料、Gamer 不同查詢互相撤銷 token。均補重現測試並修正；登入變更期間停用查詢，cookie／狀態完成後才發布新情境，首／後頁 auth/trust 失敗會通知 Host 清除情境。更新支援登入的來源須回傳 `catalogInfo.contextEpoch`，否則 Host 顯示更新要求並拒絕發布。

另修正 stale 標示消失、其他論壇登入時清掉目前查詢的問題；僅替換受影響的 controller。無 epoch 的健康檢查不重用或覆蓋有效帳號目錄；Gamer 以有界限的 session map 支援交錯消費者。沒有待處理的審查 minor；真站 DOM／Mobile01 登入與正式發布仍屬上列未完成條件。

## 交付版本與重跑方式

Host 產品程式提交 `f68d12a`，來源 repo 提交 `c72d257`（三個版本 Mobile01 0.1.7／Gamer 0.0.13／EYNY 0.1.7）。Mobile01 推薦頁維持原有公開種子，不依賴遠端目錄先載入；搜尋及手動更新才要求動態目錄，推薦 metadata 不宣稱完整。

在 PowerShell 設定本工作階段 `JAVA_HOME=C:/Projects/NewsHub/.superpowers/tools/java/jdk-17.0.20.1+1`、`ANDROID_HOME=C:/Projects/NewsHub/.superpowers/tools/sdk`，各 Gradle 命令加 `-Dorg.gradle.java.home=C:/Projects/NewsHub/.superpowers/tools/java/jdk-17.0.20.1+1` 以覆蓋既有其他電腦的全域路徑。

```text
# NewsHub：完整測試（有上述兩項 Windows 限制）與 debug build
./gradlew.bat :extension-api:testDebugUnitTest :extension-loader:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --continue
# NewsHub：看板相關針對性測試
./gradlew.bat :app:testDebugUnitTest --tests '*Board*Test' :app:assembleDebug
# extensions-source：本機 API 整合
./gradlew.bat -PnewshubDir=C:/Projects/NewsHub :shared:broker-http:testDebugUnitTest :src:mobile01-source:testDebugUnitTest :src:gamer:testDebugUnitTest :src:eyny-source:testDebugUnitTest :src:mobile01:assembleDebug :src:gamer:assembleDebug :src:eyny:assembleDebug
```

正式來源 API pin 預定對應 Host `f68d12a`；未確認可從遠端讀取前保留現有 pin，故本次未宣稱不帶本機覆寫的來源 CI 可通過。

本機產物 SHA-256（對應上列產品提交）：

- extension-api-debug.aar：97b3cc2f8f915deef41088c3f978d7c50fd475423101aa43b4816314afbf6615
- app-debug.apk：fa870921336d5781574edc4e0571dca8eaa9931873dfe90b2e847a792e9ecee1
- mobile01-debug.apk：f3b6be05c77df8c0b7912300106fc4c2ab379c4b4e564b5bb5c6a2d98fc909f7
- gamer-debug.apk：ca62e2ea762d386a3fa10ef9e447a12a2684be83228f1d942f059da620b418a8
- eyny-debug.apk：ff757bf098c85cbb75e4e8c08925f65b4acc895d07226cedbe6c35abae8cc7c0
