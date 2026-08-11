# Firebase Test Lab 零機密 Hacker News 冒煙測試

`ExtensionLiveHealthInstrumentedTest` 內建兩個由 NewsHub host 管理的 profile：

- `official-live-v1` 保留為預設的完整 13 Source profile，只能在另行核准的私有環境使用。
- `zero-secret-hackernews-v1` 只選取 Hacker News，也是定期 Firebase Test Lab 冒煙測試唯一允許的 profile。
- `official-public-v1` 以零機密檢查完整 13 Source 清單：11 個免登入 Source 實際爬取，Gamer／EYNY 明確回報 `AUTH_PENDING`，不能算成全綠。
- `candidate-<bundle>-v1` 是七個 package 的封閉清單，只檢查單一 candidate APK 內的精確 Source subset。

instrumentation argument 只能從這份封閉清單選取 profile，不能指定任意 asset 或檔案。
`health-report.json` 的結構與數量契約是成功判定依據；PNG 只作為證據，但控制面要宣告成功時仍必須取得並驗證它的 SHA-256。

## 本機建置與前置檢查

使用可能計費的 Test Lab 前，先在本機執行零機密路徑：

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r /secure/apks/newshub-hackernews.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb shell am instrument -w \
  -e class tw.kevinzhang.newshub.extension.ExtensionLiveHealthInstrumentedTest \
  -e extensionHealthProfile zero-secret-hackernews-v1 \
  tw.kevinzhang.newshub.test/androidx.test.runner.AndroidJUnitRunner
```

Hacker News APK 必須是 extension 發佈流程已准入的精確簽署 artifact。可信任的測試 snapshot 會驗證 package 內容與正式 signer pin。

## 單一且有界的 Test Lab matrix

定期控制面 wrapper 會固定所有可能放大成本的維度：只建立一個
`MediumPhone.arm`／Android 35 matrix、只安裝一個 additional APK、執行 timeout 十分鐘、
零重試、不錄影、不收集效能指標，並為每個 Cloud Build ID 使用唯一的 Cloud Storage results directory。

等效的直接呼叫如下：

```bash
PULL_ROOT=/sdcard/Download/newshub-hn-health
RESULTS_DIR="monitor/<unique-cloud-build-id>"

gcloud firebase test android run \
  --project=analog-marking-505217-n3 \
  --type=instrumentation \
  --app=app/build/outputs/apk/debug/app-debug.apk \
  --test=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --test-targets="class tw.kevinzhang.newshub.extension.ExtensionLiveHealthInstrumentedTest" \
  --device=model=MediumPhone.arm,version=35,locale=zh_TW,orientation=portrait \
  --additional-apks=/secure/apks/newshub-hackernews.apk \
  --environment-variables=extensionHealthProfile=zero-secret-hackernews-v1,extensionHealthOutputRoot=${PULL_ROOT},extensionHealthReportName=health-report.json \
  --directories-to-pull=${PULL_ROOT} \
  --results-bucket=<approved-private-results-bucket> \
  --results-dir=${RESULTS_DIR} \
  --timeout=10m \
  --num-flaky-test-attempts=0 \
  --no-auto-google-login \
  --no-record-video \
  --no-performance-metrics
```

Firebase Test Lab 最多接受七個 additional APK；本定期零機密 runner 刻意固定為一個 Hacker News APK，不能透過參數擴大。

`MediumPhone.arm` 與 Android 35 已於 2026-08-12 透過唯讀查詢確認可用。device catalog 可能變動，因此每次執行前必須重新確認：

```bash
gcloud firebase test android models describe MediumPhone.arm \
  --project=analog-marking-505217-n3
```

## 最終成功契約

只有同時符合下列條件，matrix 才算成功：

1. `gcloud firebase test android run` 以零結束，代表 instrumentation 通過。
2. report 的 profile 是 `zero-secret-hackernews-v1`、只包含 Hacker News，且 board、thread summary 與 thread post 的觀測數量都大於零。
3. 經白名單整理的 `sanitized-health.json` 只包含結構結果與安全識別資訊。
4. 必須取回恰好一張非空的 Hacker News PNG；其 SHA-256 必須同時寫入 sanitized result 與 `screenshot-sha256.json`。

拉回的輸出結構如下：

```text
newshub-hn-health/
├── health-report.json
└── screenshots/
    └── tw.kevinzhang.newshub.extension.hackernews.png
```

## Secret 與成本邊界

不得附加 credential file，也不得透過 `--other-files`、`--environment-variables`、label、
檔名或 argument 傳遞 username、password、cookie、API key、OTP seed、MFA material 或登入狀態。
此冒煙測試不載入 Gamer／EYNY APK，也不會呼叫需要登入的 Source。

完整 `official-live-v1` profile 會繼續保留，但這不代表已授權在 Firebase Test Lab 執行 13 Source credentialed suite。該流程只能放在另行核准、由 host 管理 session provisioning 的私有環境。

真實 Gamer／EYNY session 是 bearer credential，禁止透過 Firebase Test Lab 的
`--other-files` 上傳。13/13 credentialed suite 只能在可信任、自管、短命 emulator 執行；
instrumentation 會從固定 Download 路徑讀取 snapshot、在 bind extension 前刪除，並只匯入
Host-owned cookie jar。使用者仍需以專用低權限帳號親自完成 MFA／CAPTCHA；沒有 session 時
只能報告 11 個實測加 2 個 `AUTH_PENDING`，不得描述為完整成功。

Firebase Test Lab 與 Cloud Storage artifact retention 可能產生費用。沒有新的成本核准前，
不得增加固定的一台 device、十分鐘 timeout、零重試或 retention 上限。

官方參考資料：

- [gcloud firebase test android run](https://docs.cloud.google.com/sdk/gcloud/reference/firebase/test/android/run)
- [Firebase Test Lab gcloud 操作指南](https://firebase.google.com/docs/test-lab/android/command-line)
