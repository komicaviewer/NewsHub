# NewsHub — Agent Onboarding Guide

A multi-module Android app (Kotlin + Jetpack Compose + Hilt) that aggregates forum/imageboard content via a pluggable Source system. Think "Mihon for forums."

---

## Module Map

```
NewsHub/
├── app/                   # Main Android app (UI, navigation, ViewModels, DI wiring)
├── extension-api/         # Source API + bounded AIDL/PFD isolated-service protocol
├── extension-loader/      # Discovers, verifies, and binds isolated Source services
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
- `onAttach(SourceRuntime)` — source-scoped network/auth capabilities supplied inside the isolated process
- `requiresLogin`, `loginUrl`, `loginPageLoadJs` — WebView auth support

### ExtensionLoader (`extension-loader`)
`ExtensionLoader` interface exposes:
- `sourcesFlow: StateFlow<List<Source>>` — **reactive**, auto-updates on APK install/uninstall
- `getAllSources()` / `getSource(id)` — convenience synchronous wrappers

`ExtensionLoaderImpl` combines:

1. APK extension sources from `ExtensionManager.installedExtensions`

### ExtensionManager (`extension-loader`)
Singleton. Queries the explicit `tw.kevinzhang.newshub.extension.SERVICE` contract, verifies the official package, signer history, Service flags, permission, and Source metadata, then binds by explicit `ComponentName`. Extension code never loads into the Host process.

### ExtensionReceiver (`app`)
`@AndroidEntryPoint` BroadcastReceiver at `tw.kevinzhang.newshub.extension.ExtensionReceiver`. Listens for `PACKAGE_ADDED/REPLACED/REMOVED`, filters to NewsHub extensions, calls `ExtensionManager.notifyPackageChanged/Removed`.

### Marketplace (`marketplace` module)
`MarketplaceRepository` interface:
- `fetchRepoMetadata(repoUrl)` / `fetchExtensions(repoUrl)` — refresh threshold-signed,
  expiring repository metadata from the one code-owned official origin
- `downloadApk(info)` — verifies signed length/hash, package/version, signing lineage,
  requested permissions, and the exact isolated-Service manifest before install
- `getInstallState(info)` — compares the installed version and current signer with
  the verified target policy

The updater is pinned to the embedded root and supports old+new threshold root
rotation, rollback/freeze protection, bounded responses, and no redirects. Custom
repository origins and the legacy unsigned `repo.json`/`index.json` path fail closed.

### RepoRepository (`app`)
Persists user-configured repo URLs in DataStore (`repo_settings`). Interface: `getRepoUrls(): Flow<Set<String>>`, `addRepoUrl`, `removeRepoUrl`.

---

## Extension APK Service Contract

| Key | Value |
|-----|-------|
| `newshub.extension.protocol` | `1` |
| `newshub.extension.source_id` | Stable Source ID |
| `newshub.extension.source_name` | Display name |
| `newshub.extension.source_lang` | Language tag |
| `newshub.extension.source_base_url` | HTTPS base URL |

Every Source is one exported Service with `android:isolatedProcess="true"`, a
unique private process, and the Host-defined signature bind permission. APKs
declare no permissions. The old registry asset and application marker are forbidden.
See [`docs/extension-bundles.md`](docs/extension-bundles.md) for the complete
manifest, broker, and marketplace contract.

---

## Trusted repository layout

```text
metadata/root.json
metadata/timestamp.json
metadata/<version>.snapshot.json
metadata/<version>.targets.json
targets/apk/<content-versioned-name>.apk
```

The root and targets roles use 2-of-2 signatures; snapshot and timestamp use
separate 1-of-1 roles. Signed target custom metadata binds package name,
version, signing-lineage root, approved current signers, exact Source services,
protocol, and Host network-policy hash.

---

## DI Wiring (Hilt)

| Module              | Provides                                                                                       |
|---------------------|------------------------------------------------------------------------------------------------|
| `ExtensionModule`   | `ExtensionLoader`                                                                              |
| `MarketplaceModule` | `Gson`, `MarketplaceRepository`                                                                |
| `CollectionModule`  | Collection/history/saved repositories, `SourceIdentityRepository`, Room DB (version 8) |
| `AppModule`         | `authDataStore`, `@Named("repoDataStore")`, `ImageLoader`, `ApplicationScope`                  |
| `RepoModule`        | `RepoRepository`                                                                               |
| `ExtensionTrustModule` | Verified repository trust snapshot → loader trust provider                                 |
| `NetworkModule`     | Host-only broker `OkHttpClient`; never passed to extension code                                |

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
| `komicaviewer/extensions` | Destination-owned signed metadata, targets, and admission policy |

CI/CD first builds/tests without secrets, then signs each bundle in its own
protected environment. A credential-isolated aggregator creates a candidate;
the destination repository validates signatures, Source ownership, canonical
Host policy hashes, threshold metadata, rollback rules, and the full catalog.

Each `extension-sign-<module>` environment owns only that bundle's signing
material. Distribution credentials are exposed only to the final publishing
step. TUF role keys are separate from Android APK signing keys.

---

## collection Module — Room DB Schema

`CollectionDatabase` version 8. Identity-bearing tables use canonical
`sourceKey`, never a naked Source ID:

| Table | Entity | Purpose |
|-------|--------|---------|
| `collections` | `CollectionEntity` | User-created named collections |
| `source_identities` | `SourceIdentityEntity` | Package + stable signing-lineage anchor + local Source ID |
| `board_subscriptions` | `BoardSubscriptionEntity` | Boards subscribed per collection and Source identity |
| `reading_history` | `ReadingHistoryEntity` | Thread read history keyed by Source identity + thread |
| `saved_posts` | `SavedPostEntity` | Bookmarked threads with contained opaque asset references |

`ReadingHistoryEntity` and `SavedPostEntity` both mirror `ThreadSummary` fields. `previewContent` stored as JSON via `ParagraphListConverter` (Gson). Both expose `toThreadSummary()` for direct use with `ThreadSummaryCard`.

`CollectionRepositoryImpl` implements all three repository interfaces (`CollectionRepository`, `ReadingHistoryRepository`, `SavedPostRepository`).

---

## Release activation gate

Release builds intentionally fail until the reviewed production threshold root
is installed at `marketplace/src/main/assets/extension-root.json`, the seven
per-package protected signing environments are provisioned, and the
destination-owned production policy pins those lineages. Debug fixtures and
ephemeral emulator keys must never be promoted to production.

---

## Coding Conventions

- Language: Kotlin; UI: Jetpack Compose (Material 3)
- DI: Hilt (`@HiltViewModel`, `@Singleton`, `@AndroidEntryPoint`)
- Async: Kotlin Coroutines + `StateFlow` / `Flow`; no RxJava
- No mock DB in tests — integration tests use real Room in-memory DB
- `@Named` qualifiers used for multiple instances of the same type (DataStore, etc.)
- FileProvider authority: `${applicationId}.provider`
