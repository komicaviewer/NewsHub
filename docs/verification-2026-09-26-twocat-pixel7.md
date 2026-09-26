# 2026-09-26：2cat issues #41／#42 Pixel 7 驗證

## 結果與限制

在使用者指定的 `Pixel_7` AVD，以已發布 Komica／Komica2 APK 執行原健康檢查的 Source → Binder → Host network broker 路徑，兩來源皆為 business status `PASS`。本次未重現歷史 `get_thread_summaries / parser-contract`，未修改擴充套件 parser，也未關閉 issue 或宣稱已修復歷史問題。

| Issue | Source | 看板 | 列表 | 首串內容 | Source 耗時 |
| --- | --- | --- | --- | --- | --- |
| extensions-source #41 | `tw.kevinzhang.komica.twocat` | PASS | PASS，8 筆 | PASS，25 則 | 12.072 秒 |
| extensions-source #42 | `tw.kevinzhang.komica2.twocat` | PASS | PASS，15 筆 | PASS，20 則 | 6.159 秒 |

各 profile 執行 3 個 Source 操作；此數字不等於底層 HTTP request 數（例如 redirect）。單操作沿用 25 秒上限，外層每次 instrumentation 有 240 秒 hard timeout。沒有登入、發文、資料刪除、雲端建置或部署。

這是實際裝置的 instrumentation 整合驗證，不是人工 UI 點選驗收。測試附帶的畫面截圖不作為列表或內文已渲染的證據。尚未取得 9 月 17 日的失敗 HTML／具體例外，也未重跑原雲端環境；不能由今日本機 PASS 推論歷史根因或雲端恢復。今日裝置三步成功之可靠度分數為 98%；歷史根因仍無法判定。

## 環境與版本

- AVD：`Pixel_7`，serial `emulator-5554`，Android 17／API 37，`arm64-v8a`。
- 實體 Pixel 7 未操作；所有 adb 命令均指定 emulator serial。
- NewsHub 基底：`0614109949d0fd45e2dd68dfdadd605c089ada75`，加本次精準 health profile 與 test fixture 調整。
- extensions-source 本機：`2cd496664c54a775af936e1ac47e9a35f5696fdb`；兩 twocat 模組相對 issue 指定的 `dd632c15993417f385dea856c599fea8f528f24f` 無程式差異。
- 裝置擴充套件來自本機 distribution，兩個 SHA-256 皆與其 `index.json` 相符；未以新編譯的 extension 取代 baseline。

| Artifact | Version | SHA-256 |
| --- | --- | --- |
| `newshub-komica-v0.3.8.apk` | code 11 | `8f8f975ed321c733edfde6a98199a9f64d048a4e8ca5666ab28bc0fdf2678a50` |
| `newshub-komica2-v0.4.7.apk` | code 11 | `8d92cca5d3e0df26512f3d72c3d65102f33f2765e47e621b791e3511b80c5097` |
| `app-debug.apk` | local debug | `88cf07c9fc99060564ac0eea948aa2ecf5b55d5d29ac276a3ccdbe35e3a5b265` |
| `app-debug-androidTest.apk` | local test | `eea82212055457ea408ee84a4bf73c15ec1091681a52ca79f6afa574e0ec0362` |

## 可重跑的測試入口

新增封閉 profile `candidate-komica-twocat-v1` 與 `candidate-komica2-twocat-v1`，各只驗指定 twocat Source。測試信任 fixture 仍依 Host 固定 catalog 展開完整 APK Source 集合，滿足現有完整性驗證；不從 APK 自行推導信任，也未放寬 production verifier。僅實際 probe 被限縮為單一 Source。

執行前確認 `/sdcard/Download/newshub-private/session-snapshot.json` 不存在：live harness 會刪除此固定 session 輸入檔。本次確認不存在。執行範例（須由呼叫者設定有限的程序 timeout）：

```sh
adb -s emulator-5554 shell am instrument -w \
  -e class tw.kevinzhang.newshub.extension.ExtensionLiveHealthInstrumentedTest \
  -e extensionHealthProfile candidate-komica-twocat-v1 \
  -e extensionHealthReportName issue-41-baseline.json \
  tw.kevinzhang.newshub.test/androidx.test.runner.AndroidJUnitRunner
```

#42 換成 `candidate-komica2-twocat-v1` 與 `issue-42-baseline.json`。

## 其他驗證

- NewsHub profile unit tests：10 tests，零失敗。
- Pixel 7 `partialHealthSelectionExpandsOnlyToItsTrustedPackageSources`：1 test，PASS。
- Pixel 7 live health：2 tests，皆 PASS，報告各為 PASS。
- extensions-source 既有測試及 `/tmp` 臨時完整 parser／真 Source HTML replay：26 tests，零失敗。兩板各讀取一次公開 HTML；回放驗證每筆 sourceId、id、boardUrl、title／preview 及 allowed host。這些臨時 fixture 未提交，不能替代歷史失敗 fixture。
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest` 與 `git diff --check` 通過。

原始本機結果位於 ignored 的 `build/issue-41-42-pixel7/`：artifact pins、兩份 JSON report、instrumentation 輸出及 fixture test 輸出。沒有把 raw 站台內容或截圖提交到 Git。

## 收尾

測試後 force-stop NewsHub，清除 process-local 測試信任狀態；未清除 App data。依使用者要求保留 Pixel 7 emulator 開啟，測試 APK 仍安裝於該 AVD。沒有新增持續計費的雲端資源。

後續若 monitor 再失敗，應固定 Host／extension APK digest、看板與 page，取得脫敏 response hash／HTML 及失敗條件，再建立可重現回歸測試；不得只因 `parser-contract` 分類就修改 selector 或放寬健康契約。
