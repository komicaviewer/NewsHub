# Reading History — Design Spec

**Date:** 2026-04-14  
**Status:** Approved

---

## Overview

When a user enters `ThreadDetailScreen`, the app records the thread in Room DB. A new `SettingsScreen` exposes two items: 「閱讀紀錄」(navigates to reading history list) and 「收藏貼文」(disabled placeholder). The reading history list displays previously read threads using the same `ThreadSummaryCard` as the collection timeline, and clicking an item navigates back to that thread.

---

## Section 1 — Data Layer (`collection` module)

### `ReadingHistoryEntity`

Composite primary key: `(sourceId, threadId)`. Stores a complete snapshot of `ThreadSummary` fields needed for display and navigation.

| Field | Type | Notes |
|-------|------|-------|
| `sourceId` | String | PK |
| `threadId` | String | PK |
| `boardUrl` | String | Required for navigation |
| `title` | String? | |
| `author` | String? | |
| `createdAt` | Long? | |
| `commentCount` | Int? | |
| `replyCount` | Int? | |
| `thumbnail` | String? | |
| `rawImage` | String? | |
| `previewContent` | String (JSON) | Serialized via `ParagraphListConverter` (Gson) |
| `sourceIconUrl` | String? | |
| `readAt` | Long | epoch ms, used for DESC ordering |

`previewContent` is stored as a JSON string via a Room `@TypeConverter`. A convenience method `ReadingHistoryEntity.toThreadSummary(): ThreadSummary` enables direct reuse of `ThreadSummaryCard`.

**Gson dependency** added to `collection/build.gradle`.

### `ReadingHistoryRepository` interface

```kotlin
interface ReadingHistoryRepository {
    fun observeReadingHistory(): Flow<List<ReadingHistoryEntity>>
    suspend fun recordRead(summary: ThreadSummary)
}
```

`recordRead` upserts with `OnConflictStrategy.REPLACE` — re-reading a thread updates `readAt` to the latest timestamp.

`CollectionRepositoryImpl` implements both `CollectionRepository` and `ReadingHistoryRepository`. DI binds them separately.

### `CollectionDatabase`

Version bumped **2 → 3**. `ReadingHistoryEntity` added to `@Database(entities = [...])`. `fallbackToDestructiveMigration()` is already configured — no migration SQL required.

---

## Section 2 — ViewModel Layer

### `ThreadDetailViewModel` (modified)

Injects `ReadingHistoryRepository`. After `loadThread()` succeeds, calls `recordRead()` with a `ThreadSummary` built from the loaded thread:

- `previewContent` = first post's content, capped at 3 paragraphs
- `author` / `createdAt` / `replyCount` = from `thread.posts.firstOrNull()`
- `sourceIconUrl` = `source.iconUrl`
- `title` = `thread.title ?: threadTitle` (nav arg fallback)

### `ReadingHistoryViewModel` (new)

```kotlin
@HiltViewModel
class ReadingHistoryViewModel @Inject constructor(
    repository: ReadingHistoryRepository,
) : ViewModel() {
    val history: StateFlow<List<ReadingHistoryEntity>> =
        repository.observeReadingHistory()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

---

## Section 3 — UI & Navigation

### Shared component: `ThreadSummaryCard`

Extracted from `CollectionTimelineScreen.kt` (currently `private`) to  
`app/src/main/java/tw/kevinzhang/newshub/ui/component/ThreadSummaryCard.kt`.  
Both `CollectionTimelineScreen` and `ReadingHistoryScreen` use it.

### `SettingsScreen` (new)

A simple `LazyColumn` (or `Column`) with two `ListItem`s:

| Item | Behaviour |
|------|-----------|
| 閱讀紀錄 | `onClick` → navigate to `reading_history` |
| 收藏貼文 | `enabled = false`, greyed out — placeholder for future feature |

### `ReadingHistoryScreen` (new)

- `TopAppBar`: title "閱讀紀錄", back button
- `LazyColumn`: each item renders `ThreadSummaryCard(summary = entity.toThreadSummary(), ...)`
- Item click → navigate to `thread_detail?threadId=...&sourceId=...&boardUrl=...&threadTitle=...`
- Empty state: centred text "尚無閱讀紀錄"

### Navigation (`AppScreen.kt`)

`settings` becomes a **nested navigation graph**:

```
settings  (nested graph, MainNavItems.Settings.route)
  ├── settings_home    ← SettingsScreen  (startDestination)
  └── reading_history  ← ReadingHistoryScreen
```

The bottom bar `Settings` tab already points to `MainNavItems.Settings.route = "settings"`, so no `NavItems` changes are needed — the nested graph is transparent to the bottom bar.

---

## Out of Scope

- 收藏貼文 implementation (future)
- Reading history pagination or size cap (can be added later if list grows large)
- `alwaysUseRawImage` per-source flag in history (history screen always uses `thumbnail`, falls back to `rawImage`)
