# NewsHub Agent Instructions

## 安卓開發環境

- 所有開發變動的建置、安裝、執行與驗證，除非使用者明確要求其他環境，否則一律使用專案既有的 dev App 環境。
- 既有 dev App package 為 `tw.kevinzhang.newshub.debug`；沿用其開發資料及設定。不得因為 Gradle task 名稱含 `Debug`，就假設輸出的 APK 已使用 dev package；必須確認實際 application ID。
- 未經使用者明確要求，不得安裝、更新或操作正式 App，也不得自行建立另一個 package 代替既有 dev App 進行交付或驗收。
- 覆蓋安裝前必須確認 APK 與目標 dev App 的 package 及實際簽章相符。預設使用使用者指定的 `~/.android/debug.keystore`；遵守全域規則的憑證指紋檢查。簽章不符時立即停止覆蓋，不得擅自卸載、清除資料、換 package 或更換金鑰來繞過檢查。
- 只有使用者明確指定其他環境時，才可在其授權範圍內例外執行；不得由「開始實作」、「驗證」或「直到完成」等一般授權推定可使用正式環境。
