# 遷移報告：Kotlin 2.1.0 + android-youtube-player 13.0.0

**日期：** 2026-04-12  
**動機：** `android-youtube-player:core:13.0.0` 以 Kotlin 2.1.0 編譯，與現有 Kotlin 1.9.24 toolchain 不相容  
**風險等級：中**

---

## 目錄

1. [現況 vs 目標](#現況-vs-目標)
2. [必要變更（依序執行）](#必要變更依序執行)
3. [建議遷移：kapt → KSP](#建議遷移kapt--ksp)
4. [主要風險點](#主要風險點)
5. [測試清單](#測試清單)
6. [版本回退指引](#版本回退指引)

---

## 現況 vs 目標

| 元件 | 現在 | 目標 | 破壞性 |
|------|------|------|--------|
| Kotlin | `1.9.24` | `2.1.0` | 是 |
| Compose Compiler | 獨立外掛 `1.5.14` | 內建於 Kotlin 2.1.0 | 是 |
| AGP | `8.13.2` | `8.13.2`（相容） | 否 |
| compileSdk | `35` | `35`（相容） | 否 |
| annotation 處理器 | `kapt` | `KSP`（建議） | 是（若遷移） |
| android-youtube-player | `12.1.0` | `13.0.0` | 否 |
| Java 相容性 | `1.8` | `1.8`（相容） | 否 |

---

## 必要變更（依序執行）

### Step 1 — `build.gradle`（root）：升級 Kotlin

```gradle
// ext.versions 內：
kotlin_version = '2.1.0'
kotlin: '2.1.0',

// resolutionStrategy 內（三行）：
force "org.jetbrains.kotlin:kotlin-stdlib:2.1.0"
force "org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.0"
force "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.0"

// plugins 區塊：
id 'org.jetbrains.kotlin.android' version '2.1.0' apply false
id 'org.jetbrains.kotlin.jvm'     version '2.1.0' apply false
id 'org.jetbrains.kotlin.plugin.compose' version '2.1.0' apply false  // 新增
```

### Step 2 — `app/build.gradle`：切換 Compose Compiler 處理方式

```gradle
// plugins 區塊新增：
id 'org.jetbrains.kotlin.plugin.compose'

// 刪除整個 composeOptions 區塊：
// composeOptions {
//     kotlinCompilerExtensionVersion "1.5.14"
// }
```

> **說明：** Kotlin 2.0+ 起，Compose compiler 已內建於 Kotlin，不再透過
> `kotlinCompilerExtensionVersion` 控制。刪除此區塊後，compiler 版本由 Kotlin
> plugin 版本決定。

### Step 3 — `app/build.gradle`：升級 android-youtube-player

```gradle
implementation "com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0"
```

### Step 4 — 驗證基礎編譯

```bash
./gradlew clean :app:compileDebugKotlin
```

---

## 建議遷移：kapt → KSP

Kotlin 2.x 官方建議以 KSP 取代 kapt。Hilt `2.51.1` 已完整支援 KSP。此步驟**可延後**，不影響功能。

### root `build.gradle` — plugins 區塊新增

```gradle
id 'com.google.devtools.ksp' version '2.1.0-1.0.29' apply false
```

### `app/build.gradle`

```gradle
// plugins 區塊：將 'kotlin-kapt' 改為：
id 'com.google.devtools.ksp'

// dependencies 區塊：
// FROM:
kapt "com.google.dagger:hilt-android-compiler:$versions.hilt"
// TO:
ksp  "com.google.dagger:hilt-android-compiler:$versions.hilt"
```

> **注意：** 若專案內有 Room，Room 的 annotation processor 亦須改為 `ksp`。
> 確認 Room 版本 ≥ 2.5.0（最早支援 KSP 的版本）。

---

## 主要風險點

| 變更 | 影響 | 說明 |
|------|------|------|
| 移除 `composeOptions` | 高 | 若遺留舊設定可能與新 plugin 衝突，導致 compile 失敗 |
| Kotlin 2.x 更嚴格型別推斷 | 中 | 複雜 lambda 可能出現 compile error，需補上明確型別標注 |
| kapt → KSP（若執行） | 中 | 需確認所有 annotation processor 均有 KSP 版本 |
| `resolutionStrategy` force | 低 | 強制 stdlib 版本需同步更新，否則 classpath 衝突 |

---

## 測試清單

完成變更後依序驗證：

- [ ] `./gradlew clean build` 無錯誤
- [ ] Hilt 注入正常（開啟 app 無 crash）
- [ ] Compose UI 正常顯示（首頁、討論串頁）
- [ ] YouTube player 正常播放影片
- [ ] Release build 通過 ProGuard：`./gradlew :app:assembleRelease`
- [ ] `./gradlew lint` 無新增 error

---

## 版本回退指引

升級後若出現以下狀況，請依此指引完成回退：

- Compose UI 出現大量 compile error 無法快速修復
- Hilt 注入失敗或 KSP 相容問題無法解決
- 其他 Kotlin 2.x 嚴格型別推斷導致的廣泛破壞

### 回退目標版本

| 元件 | 回退至 |
|------|--------|
| Kotlin | `1.9.24` |
| Compose Compiler | `1.5.14`（獨立外掛） |
| android-youtube-player | `12.1.0` |
| annotation 處理器 | `kapt`（還原） |

### 回退步驟

**Step R1 — `build.gradle`（root）：還原 Kotlin 版本**

```gradle
// ext.versions 內：
kotlin_version = '1.9.24'
kotlin: '1.9.24',

// resolutionStrategy 內（三行）：
force "org.jetbrains.kotlin:kotlin-stdlib:1.9.24"
force "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.24"
force "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.24"

// plugins 區塊：
id 'org.jetbrains.kotlin.android' version '1.9.24' apply false
id 'org.jetbrains.kotlin.jvm'     version '1.9.24' apply false
// 移除這行（若有加）：
// id 'org.jetbrains.kotlin.plugin.compose' version '2.1.0' apply false
```

**Step R2 — `app/build.gradle`：還原 Compose Compiler 設定**

```gradle
// plugins 區塊移除（若有加）：
// id 'org.jetbrains.kotlin.plugin.compose'

// 還原 composeOptions 區塊：
composeOptions {
    kotlinCompilerExtensionVersion "1.5.14"
}
```

**Step R3 — `app/build.gradle`：還原 youtube player 版本**

```gradle
implementation "com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0"
```

**Step R4（若已執行 KSP 遷移）— 還原 kapt**

```gradle
// root build.gradle plugins 移除：
// id 'com.google.devtools.ksp' version '2.1.0-1.0.29' apply false

// app/build.gradle plugins 改回：
id 'kotlin-kapt'

// dependencies 改回：
kapt "com.google.dagger:hilt-android-compiler:$versions.hilt"
```

**Step R5 — 清除 Gradle cache 並驗證**

```bash
./gradlew clean
rm -rf ~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin
./gradlew :app:compileDebugKotlin
```

> **說明：** 移除 Kotlin cache 是必要步驟。Gradle 有時會使用殘留的 2.1.0 artifacts，
> 即使 `build.gradle` 已改回 1.9.24，仍可能出現版本衝突。

### 快速回退（使用 git）

所有變更皆已 commit，最快的回退方式是直接還原 git 狀態：

```bash
# 查看遷移前的最後一個 commit hash
git log --oneline

# 還原特定 commit 之後的所有變更（不刪除 commit history）
git revert <commit-hash>

# 或直接 checkout 遷移前的 commit（會丟失之後的 commit）
git checkout <pre-migration-commit-hash> -- app/build.gradle build.gradle
```

> **建議：** 升級前先建立一個 tag 作為回退錨點：
> ```bash
> git tag pre-kotlin2-migration
> ```
> 回退時：`git checkout pre-kotlin2-migration -- app/build.gradle build.gradle`
