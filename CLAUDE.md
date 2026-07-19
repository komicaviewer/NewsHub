# NewsHub — Agent Onboarding Guide

A multi-module Android app (Kotlin + Jetpack Compose + Hilt) that aggregates forum/imageboard content via a pluggable Source system. Think "Mihon for forums."

---

## Module Map

```
NewsHub/
├── app/                   # Main Android app (UI, navigation, ViewModels, DI wiring)
├── extension-api/         # Source interface + data models (pure Kotlin, no Android)
├── extension-loader/      # ExtensionManager + ExtensionLoaderImpl (loads APK extensions)
├── marketplace/           # Extension repo parsing, APK download, install state
├── collection/            # Room DB: user collections, board subscriptions, reading history, saved posts
├── gamer-api/             # HTTP client for Bahamut Gamer (being phased out as built-in)
└── komica-api/            # HTTP client for Komica boards
```

---

## Key Abstractions

### Source (`extension-api`)
`tw.kevinzhang.extension_api.Source` — the plugin interface every news source must implement.
- `id`, `name`, `language`, `version`, `iconUrl`
- `getBoardCategories()`, `getBoardPage(request)`, `getThreadSummaries(board, page)`, `getThread(summary)`
- Board catalogs are source-owned and paged. An empty query returns the source's popular
  boards; non-empty queries search remotely or within that source's catalog.
- `onAttach(SourceContext)` — injected by host app for auth callbacks
- `requiresLogin`, `loginUrl`, `loginPageLoadJs` — WebView auth support

### ExtensionLoader (`extension-loader`)
`ExtensionLoader` interface exposes:
- `sourcesFlow: StateFlow<List<Source>>` — **reactive**, auto-updates on APK install/uninstall
- `getAllSources()` / `getSource(id)` — convenience synchronous wrappers

`ExtensionLoaderImpl` combines:

1. APK extension sources from `ExtensionManager.installedExtensions`

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

## Extension APK Registry Contract

| Key | Value |
|-----|-------|
| `newshub.extension` | `"true"` (marker) |
| `newshub.extension.registry` | Registry asset filename, normally `newshub-extension.json` |

The registry uses schema version 1 and lets one APK expose multiple Sources:

```json
{
  "schemaVersion": 1,
  "sources": [
    {
      "className": "tw.kevinzhang.extension.example.ExampleSource",
      "id": "tw.kevinzhang.example",
      "name": "Example",
      "lang": "zh-TW",
      "baseUrl": "https://example.com"
    }
  ]
}
```

The loader validates the registry metadata against every instantiated Source.
See [`docs/extension-bundles.md`](docs/extension-bundles.md) for the complete
manifest, registry, and marketplace index contract.

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

| Module              | Provides                                                                                       |
|---------------------|------------------------------------------------------------------------------------------------|
| `ExtensionModule`   | `ExtensionLoader`                                                                              |
| `MarketplaceModule` | `Gson`, `MarketplaceRepository`                                                                |
| `CollectionModule`  | `CollectionRepository`, `ReadingHistoryRepository`, `SavedPostRepository`, Room DB (version 4) |
| `AppModule`         | `authDataStore`, `@Named("repoDataStore")`, `ImageLoader`, `ApplicationScope`                  |
| `RepoModule`        | `RepoRepository`                                                                               |
| `AuthModule`        | `SourceContext` (→ `AndroidSourceContext`)                                                     |
| `NetworkModule`     | `OkHttpClient`                                                                                 |

---

## UI Screens & Navigation

Navigation lives in `AppScreen.kt` + `AppNavigation.kt`. Bottom nav routes:

| Route | Screen | ViewModel |
|-------|--------|-----------|
| `collections` | CollectionTimelineScreen | CollectionTimelineViewModel |
| `boards` | BoardsScreen | BoardsViewModel |
| `settings` (nested graph) | SettingsScreen | — |

`settings` nested graph (startDestination: `settings_home`):

| Route | Screen | ViewModel |
|-------|--------|-----------|
| `settings_home` | SettingsScreen | — |
| `reading_history` | ReadingHistoryScreen | ReadingHistoryViewModel |
| `saved_posts` | SavedPostsScreen | SavedPostsViewModel |
| `saved_post_detail` | SavedPostDetailScreen | SavedPostDetailViewModel |

Sub-screens (pushed on stack):
- `BoardPickerScreen` — paged, cross-source board search grouped by source, with source-owned
  categories, source filter chips, selected/popular landing content, and recent-result fallback
- `ThreadDetailScreen` — renders posts, comments, images; records reading history on load; supports save/unsave post (screenshots)
- `AuthWebViewScreen` — WebView login for sources that require auth
- `CreateCollectionScreen`, `EditCollectionScreen`

`ThreadSummaryCard` — extracted to `ui/component/`, shared by `CollectionTimelineScreen`, `ReadingHistoryScreen`, and `SavedPostsScreen`.

---

## External Repos

| Repo | Purpose |
|------|---------|
| `komicaviewer/extensions-source` | Source code for third-party extensions (modelled after keiyoushi, **flat `src/<name>/`**, no language subdirs) |
| `komicaviewer/extensions` | Distribution repo: `repo.json`, `index.json`, `apk/`, `icon/` |

CI/CD: `extensions-source` builds APKs via GitHub Actions (`build_push.yml`), runs `scripts/generate_index.py` (uses `aapt`), commits result to `extensions` repo.

Required GitHub secrets in `extensions-source`: `SIGNING_KEY`, `KEY_STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `EXTENSIONS_REPO_TOKEN`.

---

## collection Module — Room DB Schema

`CollectionDatabase` version 4. Tables:

| Table | Entity | Purpose |
|-------|--------|---------|
| `collections` | `CollectionEntity` | User-created named collections |
| `board_subscriptions` | `BoardSubscriptionEntity` | Boards subscribed per collection |
| `reading_history` | `ReadingHistoryEntity` | Thread read history (composite PK: sourceId+threadId) |
| `saved_posts` | `SavedPostEntity` | Bookmarked threads with screenshot paths |

`ReadingHistoryEntity` and `SavedPostEntity` both mirror `ThreadSummary` fields. `previewContent` stored as JSON via `ParagraphListConverter` (Gson). Both expose `toThreadSummary()` for direct use with `ThreadSummaryCard`.

`CollectionRepositoryImpl` implements all three repository interfaces (`CollectionRepository`, `ReadingHistoryRepository`, `SavedPostRepository`).

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
