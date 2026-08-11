# Firebase Test Lab 即時 Extension 健康檢測

`ExtensionLiveHealthInstrumentedTest` 會以可信任的 NewsHub host 測試七個官方 extension APK bundles。`health-report.json` 的結構契約是成功與否的判定依據；`screenshots/` 下的 PNG 只作為輔助證據。

## 先在本機建置與檢查

使用可能計費的 Test Lab 前，先建置 app 與 instrumentation APK，並在本機 emulator 執行相同測試：

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install-multiple -r \
  /secure/apks/eyny.apk \
  /secure/apks/gamer.apk \
  /secure/apks/hackernews.apk \
  /secure/apks/komica.apk \
  /secure/apks/komica2.apk \
  /secure/apks/mobile01.apk \
  /secure/apks/ptt.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

七個 additional APK 必須是 extension 發布流程已准入的精確 signed artifacts，不得以未簽署的 PR build 代替。

## 執行一個有界 Test Lab matrix

以下範例只使用一台 virtual device、停用重試，並設定 10 分鐘 hard timeout。內嵌 profile 的整體 timeout 為 7 分鐘，每個 operation 最長 25 秒，extension requests 上限為 48 次。

```bash
PULL_ROOT=/sdcard/Download/newshub-extension-health

gcloud firebase test android run \
  --project=analog-marking-505217-n3 \
  --type=instrumentation \
  --app=app/build/outputs/apk/debug/app-debug.apk \
  --test=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --test-targets="class tw.kevinzhang.newshub.extension.ExtensionLiveHealthInstrumentedTest" \
  --device=model=MediumPhone.arm,version=35,locale=zh_TW,orientation=portrait \
  --additional-apks=/secure/apks/eyny.apk,/secure/apks/gamer.apk,/secure/apks/hackernews.apk,/secure/apks/komica.apk,/secure/apks/komica2.apk,/secure/apks/mobile01.apk,/secure/apks/ptt.apk \
  --environment-variables=extensionHealthOutputRoot=${PULL_ROOT},extensionHealthReportName=health-report.json \
  --directories-to-pull=${PULL_ROOT} \
  --timeout=10m \
  --num-flaky-test-attempts=0 \
  --no-auto-google-login \
  --no-record-video
```

`MediumPhone.arm` 與 Android 35 曾在 2026-08-12 透過唯讀 device lookup 確認可用。Test Lab device catalog 會變動，執行前必須再次確認：

```bash
gcloud firebase test android models describe MediumPhone.arm \
  --project=analog-marking-505217-n3
```

CLI 雖可接受更多 additional APKs，本工作流固定只允許七個正式 release bundles。

`extensionHealthOutputRoot` instrumentation argument 不是 credential。它只接受 `/sdcard`、`/storage` 或 `/data/local/tmp` 下的 normalized descendant，並拒絕 traversal 與 shell syntax。輸出結構如下：

```text
newshub-extension-health/
├── health-report.json
└── screenshots/
    └── <source-id>.png
```

省略 `extensionHealthOutputRoot` 時，測試會維持既有行為，寫入 app-owned external-files 的 `extension-health` 目錄。

## Credential 邊界

不得透過 `--environment-variables`、`--client-details`、APK filename、result label 或 command argument 傳遞 username、password、cookie、API key、one-time code 或 MFA material。Test Lab 會將 environment variables 傳入 `am instrument`，測試 artifacts 也會上傳到指定 result bucket。

GCP wrapper 會把 credential file 放在 `/sdcard/Download/newshub-private/`，但只 pull `/sdcard/Download/newshub-health/`；兩個路徑必須永遠保持分離。

在核准的私有機制完成 host-owned authenticated state 前，Gamer 與 EYNY 會回報 `AUTH_REQUIRED`。本命令不會放寬此邊界；不需要登入的 Sources 仍會正常受測，任何 extension 都不會取得 raw credentials。

Firebase Test Lab 與保留在 Cloud Storage 的 artifacts 可能產生費用。除非重新取得費用核准，matrix 必須維持一台已核准 device、`--timeout=10m`、零重試，並在提高 device 數量或 retention 前先確認 result bucket lifecycle policy。

官方參考資料：

- [gcloud firebase test android run 指令參考](https://docs.cloud.google.com/sdk/gcloud/reference/firebase/test/android/run)
- [Firebase Test Lab gcloud 操作指南](https://firebase.google.com/docs/test-lab/android/command-line)
