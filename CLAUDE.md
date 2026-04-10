# NewsHub — Agent Onboarding Guide

A multi-module Android app (Kotlin + Jetpack Compose + Hilt) that aggregates forum/imageboard content via a pluggable Source system. Think "Mihon for forums."

---

## Module Map

```
NewsHub/
├── app/                   # Main Android app (UI, navigation, ViewModels, DI wiring)
├── extension-api/         # Source interface + data models (pure Kotlin, no Android)
├── extension-loader/      # ExtensionManager + ExtensionLoaderImpl (loads APK extensions)
├── extensions-builtin/    # Built-in Source implementations (Sora, 2cat, Komica2)
├── marketplace/           # Extension repo parsing, APK download, install state
├── collection/            # Room DB: user collections + board subscriptions
├── gamer-api/             # HTTP client for Bahamut Gamer (being phased out as built-in)
└── komica-api/            # HTTP client for Komica boards
```

> **GamerSource has been migrated** to the external repo `komicaviewer/extensions-source` as a third-party APK extension. It still exists in `extensions-builtin` but is scheduled for removal (Task 1-5).

---

## Key Abstractions

### Source (`extension-api`)
`tw.kevinzhang.extension_api.Source` — the plugin interface every news source must implement.
- `id`, `name`, `language`, `version`, `iconUrl`
- `getBoards()`, `getThreadSummaries(board, page)`, `getThread(summary)`
- `onAttach(SourceContext)` — injected by host app for auth callbacks
- `requiresLogin`, `loginUrl`, `loginPageLoadJs` — WebView auth support

### ExtensionLoader (`extension-loader`)
`ExtensionLoader` interface exposes:
- `sourcesFlow: StateFlow<List<Source>>` — **reactive**, auto-updates on APK install/uninstall
- `getAllSources()` / `getSource(id)` — convenience synchronous wrappers

`ExtensionLoaderImpl` combines:
1. Built-in sources from Hilt DI (`@Named("builtInSources")`)
2. APK extension sources from `ExtensionManager.installedExtensions`

### ExtensionManager (`extension-loader`)
Singleton. Scans `PackageManager` for packages with `newshub.extension` meta-data, loads `Source` via `PathClassLoader`. Exposes `installedExtensions: StateFlow<List<InstalledExtension>>`. Handles `installExtension(File)` and `uninstallExtension(pkgName)` via system intents.

### ExtensionReceiver (`app`)
`@AndroidEntryPoint` BroadcastReceiver at `tw.kevinzhang.newshub.extension.ExtensionReceiver`. Listens for `PACKAGE_ADDED/REPLACED/REMOVED`, filters to NewsHub extensions, calls `ExtensionManager.notifyPackageChanged/Removed`.

### Marketplace (`marketplace` module)
`MarketplaceRepository` interface:
- `fetchRepoMetadata(repoUrl)` — parses `repo.json`
- `fetchExtensions(repoUrl)` — parses `index.json` (flat array of `RemoteExtensionDto`)
- `downloadApk(url, sha256)` — downloads + SHA-256 verifies to cache dir
- `getInstallState(info)` — compares `PackageManager` versionCode vs index

GitHub URL → raw URL: `https://github.com/owner/repo` → `https://raw.githubusercontent.com/owner/repo/main`

### RepoRepository (`app`)
Persists user-configured repo URLs in DataStore (`repo_settings`). Interface: `getRepoUrls(): Flow<Set<String>>`, `addRepoUrl`, `removeRepoUrl`.

---

## Extension APK Manifest Metadata Keys

| Key | Value |
|-----|-------|
| `newshub.extension` | `"true"` (marker) |
| `newshub.extension.source_class` | Fully-qualified class name |
| `newshub.extension.name` | Display name |
| `newshub.extension.source_id` | Source `id` field |
| `newshub.extension.source_name` | Source display name |
| `newshub.extension.source_lang` | BCP-47 language tag |
| `newshub.extension.source_base_url` | Site base URL |

---

## index.json Format (extensions repo)

```json
[
  {
    "pkg":         "tw.kevinzhang.extension.gamer",
    "name":        "Gamer 巴哈姆特",
    "versionCode": 1,
    "versionName": "1.0",
    "lang":        "zh-TW",
    "apkName":     "tw.kevinzhang.extension.gamer.apk",
    "iconName":    "tw.kevinzhang.extension.gamer.png",
    "sha256":      "<hex>",
    "sources": [
      { "id": "tw.kevinzhang.gamer", "name": "Gamer 巴哈姆特",
        "lang": "zh-TW", "baseUrl": "https://forum.gamer.com.tw" }
    ]
  }
]
```

APK URL = `{repo.baseUrl}/apk/{apkName}` · Icon URL = `{repo.baseUrl}/icon/{iconName}`

---

## DI Wiring (Hilt)

| Module | Provides |
|--------|----------|
| `ExtensionModule` | `@Named("builtInSources") List<Source>`, `ExtensionLoader` |
| `MarketplaceModule` | `Gson`, `MarketplaceRepository` |
| `CollectionModule` | `CollectionRepository`, Room DB |
| `AppModule` | `authDataStore`, `@Named("repoDataStore")`, `ImageLoader`, `ApplicationScope` |
| `RepoModule` | `RepoRepository` |
| `AuthModule` | `SourceContext` (→ `AndroidSourceContext`) |
| `NetworkModule` | `OkHttpClient` |

---

## UI Screens & Navigation

Navigation lives in `AppScreen.kt` + `AppNavigation.kt`. Bottom nav routes:

| Route | Screen | ViewModel |
|-------|--------|-----------|
| `collections` | CollectionTimelineScreen | CollectionTimelineViewModel |
| `boards` | BoardsScreen | BoardsViewModel |
| `marketplace` | MarketplaceScreen | MarketplaceViewModel |
| `manage_collections` | ManageCollectionsScreen | ManageCollectionsViewModel |

Sub-screens (pushed on stack):
- `BoardPickerScreen` — pick boards to subscribe; observes `ExtensionLoader.sourcesFlow`
- `ThreadDetailScreen` — renders posts, comments, images
- `AuthWebViewScreen` — WebView login for sources that require auth
- `CreateCollectionScreen`, `EditCollectionScreen`

---

## External Repos

| Repo | Purpose |
|------|---------|
| `komicaviewer/extensions-source` | Source code for third-party extensions (modelled after keiyoushi, **flat `src/<name>/`**, no language subdirs) |
| `komicaviewer/extensions` | Distribution repo: `repo.json`, `index.json`, `apk/`, `icon/` |

CI/CD: `extensions-source` builds APKs via GitHub Actions (`build_push.yml`), runs `scripts/generate_index.py` (uses `aapt`), commits result to `extensions` repo.

Required GitHub secrets in `extensions-source`: `SIGNING_KEY`, `KEY_STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `EXTENSIONS_REPO_TOKEN`.

---

## Pending Work

- **Task 1-3/1-4** — CI/CD workflows need signing secrets configured; `build_push.yml` exists but hasn't run successfully yet
- **Task 1-5** — Remove `GamerSource` from `extensions-builtin` and `ExtensionModule` once the external APK is confirmed working
- **Task 4** — Redesign `MarketplaceScreen` (deferred until Tasks 1–3 are stable)

---

## Coding Conventions

- Language: Kotlin; UI: Jetpack Compose (Material 3)
- DI: Hilt (`@HiltViewModel`, `@Singleton`, `@AndroidEntryPoint`)
- Async: Kotlin Coroutines + `StateFlow` / `Flow`; no RxJava
- No mock DB in tests — integration tests use real Room in-memory DB
- `@Named` qualifiers used for multiple instances of the same type (DataStore, etc.)
- FileProvider authority: `${applicationId}.provider`
