# NewsHub Extension Marketplace — Design Spec

**Date:** 2026-03-25
**Status:** Approved
**Branch:** gamer/feat/comment

---

## Overview

NewsHub is being redesigned from a hardcoded multi-forum reader into an extensible platform where:

1. Users install **Extensions** from a GitHub-based marketplace. Each Extension scrapes one domain (one site = one Extension).
2. Users create **Collections** by subscribing to specific boards from one or more Extensions.
3. When browsing a Collection, all subscribed boards are merged into a single **timeline** sorted by post time.

The architecture is modelled after [Mihon](https://github.com/mihonapp/mihon)'s APK-based extension system.

---

## Goals

- Replace hardcoded Gamer/Komica scrapers with a pluggable `Source` interface
- Allow third-party developers to publish Extensions as installable APKs
- Let users compose their own reading experience via Collections of boards
- Keep Gamer, Sora (komica), and _2cat (komica) as built-in sources — no install required

## Non-Goals

- Global sort across page boundaries in the merged timeline (accepted trade-off)
- User authentication / login per source (out of scope for this iteration)
- Search across sources

---

## Module Structure

```
:extension-api          ← Kotlin JVM library; Source interface + shared data models
:extensions-builtin     ← hub-server refactored; GamerSource, SoraSource, _2catSource
:extension-loader       ← Android library; unified Source registry (built-in + installed APKs)
:collection             ← Android library; Room DB for Collection & BoardSubscription
:marketplace            ← Android library; GitHub index fetch + APK download/install
:app                    ← Android app; Compose UI, ViewModels, Hilt DI
```

### Dependency graph

```
:extension-api
      ↓                    ↓                ↓
:extensions-builtin  :extension-loader  :marketplace
                           ↓                ↓ (PackageManager via Context, NOT :extension-loader)
                       :collection
                           ↓
                          :app
```

`:extension-api` is a pure Kotlin JVM library so it can be published independently in the future for third-party extension developers.

**Note on `:marketplace` boundaries:** `:marketplace` checks install state by querying `PackageManager` directly via Android `Context`. It does **not** depend on `:extension-loader`. The loader's responsibility is loading and instantiating `Source` objects from APKs; the marketplace's responsibility is discovery, download, and install lifecycle only.

---

## Extension API Contract (`:extension-api`)

### Source Interface

```kotlin
interface Source {
    val id: String          // unique reverse-domain id, e.g. "tw.kevinzhang.gamer"
    val name: String        // display name, e.g. "Gamer 巴哈姆特"
    val language: String    // BCP-47, e.g. "zh-TW"
    val version: Int        // integer version; used for update checks
    val iconUrl: String?

    suspend fun getBoards(): List<Board>
    suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary>
    suspend fun getThread(summary: ThreadSummary): Thread
}
```

All methods are `suspend fun`. Paging 3 integration is handled in `:extension-loader`, not in Source implementations.

### Data Models

```kotlin
data class Board(
    val sourceId: String,
    val url: String,
    val name: String,
    val description: String? = null,
)

// Used in board list / Collection timeline
data class ThreadSummary(
    val sourceId: String,
    val boardUrl: String,
    val id: String,
    val title: String?,
    val author: String?,
    val createdAt: Long?,
    val replyCount: Int?,
    val thumbnail: String?,
    val previewContent: List<Paragraph>,
)

// Full thread — returned by getThread()
data class Thread(
    val id: String,
    val title: String?,
    val posts: List<Post>,   // posts[0] is the OP
)

// A single post within a thread
data class Post(
    val id: String,
    val author: String?,
    val createdAt: Long?,
    val thumbnail: String?,
    val content: List<Paragraph>,
    val comments: List<Comment>,
)

// A reply attached to a Post
data class Comment(
    val id: String,
    val author: String?,
    val createdAt: Long?,
    val content: List<Paragraph>,
)
```

`Paragraph` (sealed class) is moved from `hub-server` into `:extension-api` unchanged. It includes `Paragraph.ReplyTo(targetId: String)` for intra-thread post references.

### Data Hierarchy

```
Board
 └── ThreadSummary × N     ← shown in board list and Collection timeline
       └── Thread
             └── Post × N  ← posts[0] = OP
                   └── Comment × N
```

---

## Extension Loader (`:extension-loader`)

Provides a single registry for all Sources — built-in and installed APKs — so the rest of the app never needs to distinguish between them.

```kotlin
interface ExtensionLoader {
    fun getAllSources(): List<Source>
    fun getSource(id: String): Source?
}

class ExtensionLoaderImpl @Inject constructor(
    private val builtInSources: List<Source>,  // injected via Hilt multibinding
    private val context: Context,
) : ExtensionLoader {

    override fun getAllSources() = builtInSources + loadInstalledApkSources()

    private fun loadInstalledApkSources(): List<Source> =
        context.packageManager
            .getInstalledPackages(GET_META_DATA)
            .filter { it.hasExtensionMetaData() }
            .mapNotNull { loadSourceFromApk(it) }  // PathClassLoader
}
```

**Extension APK identification:** An installable Extension declares `<meta-data android:name="newshub.extension"/>` in its `AndroidManifest.xml`. The Loader uses this to distinguish Extension APKs from unrelated installed packages.

**Built-in sources** (injected via Hilt):
- `GamerSource`
- `SoraSource` (sora.komica.org)
- `_2catSource` (2cat.komica.org)

---

## Collection (`:collection`)

Replaces the existing `Topic` + Board subscription model.

### Room Entities

```kotlin
@Entity
data class CollectionEntity(
    @PrimaryKey val id: String,   // UUID
    val name: String,
    val sortOrder: Int,
)

@Entity
data class BoardSubscriptionEntity(
    @PrimaryKey val id: String,   // UUID
    val collectionId: String,
    val sourceId: String,
    val boardUrl: String,
    val boardName: String,        // cached for display without loading the Source
    val sortOrder: Int,
)
```

### Room Migration

No data migration required. Version number is incremented and `fallbackToDestructiveMigration()` is used — all old tables are dropped and recreated.

### Merged Timeline (MergedTimelinePagingSource)

When a user opens a Collection, the ViewModel builds a `MergedTimelinePagingSource`:

1. Load all `BoardSubscription`s for the Collection.
2. For each subscription, resolve the `Source` via `ExtensionLoader.getSource(sourceId)`.
3. On each page load, call `Source.getThreadSummaries(board, page)` concurrently for all boards.
4. Merge the results and sort by `createdAt` **within the batch only**.
5. Deliver as `PagingData<ThreadSummary>` to the UI.

**Sorting trade-off:** Only per-batch (per-page) sorting is applied. A `ThreadSummary` whose source returns it on page 2 will not be promoted to page 1 even if its timestamp is newer than items on page 1 from other sources. This is an accepted trade-off — attempting global sort would require pre-loading all data, defeating pagination.

---

## Marketplace (`:marketplace`)

### GitHub Repo Structure

```
newshub-extensions/
 ├── index.json
 ├── komica-sora-1.0.0.apk
 ├── komica-2cat-1.0.0.apk
 └── ...
```

### index.json Format

```json
{
  "extensions": [
    {
      "id": "tw.kevinzhang.komica-sora",
      "name": "Komica Sora",
      "version": 2,
      "versionName": "1.0.2",
      "language": "zh-TW",
      "iconUrl": "...",
      "apkUrl": "https://github.com/.../komica-sora-1.0.2.apk"
    }
  ]
}
```

### States

| State | Logic |
|-------|-------|
| Not installed | id not found in PackageManager |
| Installed, up-to-date | installed version == index version |
| Update available | installed version < index version |

### Install / Update / Remove

- **Install / Update:** Download APK → trigger Android system package installer.
- **Remove:** Trigger Android system uninstall flow. Any `BoardSubscription` referencing this `sourceId` is automatically marked invalid (UI shows a warning).

---

## App Navigation

Bottom navigation with three tabs:

```
⊞ Collections            ⬡ Extensions           ⚙ Settings
│                        │                       │
├── Collection list      ├── Installed list      └── Theme, Marketplace URL, etc.
│   (Drawer/Dropdown)    │   └── Browse boards
│                        │       └── Add board to Collection
└── Collection Timeline  │
    (merged ThreadSummary)└── Marketplace
        │                    (discover & install)
        └── Thread detail
              └── Post list
                    └── Comment list
```

### Paragraph.ReplyTo behaviour

When a user taps `Paragraph.ReplyTo(targetId)` inside a Thread view, the `ThreadViewModel` looks up the target `Post` locally:

```kotlin
fun onReplyToClick(targetId: String) {
    val found = _thread.value?.posts?.find { it.id == targetId }
    previewPost.value = found
}
```

The matched `Post` is shown in a Dialog rendered with the existing `ParagraphBlock` composable. No additional network request or Source method is required.

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| APK-based extensions (Mihon model) | Proven pattern; full Kotlin power; Android package system handles trust |
| Built-in Gamer/Sora/_2cat as Sources | Avoids forcing users to install extensions for main use cases |
| Board-level Collection granularity | Finer control than source-level; users mix boards across sites freely |
| Merged timeline: per-batch sort only | Global sort requires pre-loading all pages; impractical for pagination |
| `suspend fun` in Source, not Flow | Paging 3 integration belongs in the loader layer, not source implementations |
| Destructive Room migration | Old Topic/subscription data is not worth migrating; simpler code |
| `boardName` cached in BoardSubscription | Avoids loading the Source just to display board name in Collection settings |
