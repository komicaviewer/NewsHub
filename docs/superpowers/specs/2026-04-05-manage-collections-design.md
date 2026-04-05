# Manage Collections Feature — Design Spec

**Date:** 2026-04-05
**Status:** Approved
**Branch:** v2

---

## Overview

Add a pencil icon button (✏️) to the right of the "New Collection" button in the app drawer. Tapping it navigates to a dedicated **Manage Collections** screen where users can reorder, edit, and delete their collections. Tapping ✏️ on a collection in the management screen navigates to a full-screen **Edit Collection** screen.

---

## UI & Navigation

### AppDrawer (change)

- Replace `fillMaxWidth` on the New Collection `Button` with `weight(1f)` inside a `Row`
- Add an `IconButton` using `Icons.Default.Edit` to the right of the button
- New parameter: `onManageCollectionsClick: () -> Unit`

```
[ + New Collection (weight=1f) ] [ ✏️ IconButton ]
```

### ManageCollectionsScreen (new)

- Route: `"manage_collections"`
- `TopAppBar`: title "管理 Collections" + back button
- `LazyColumn` with one row per collection:
  - Drag handle (≡) on the left — long-press to reorder
  - Emoji + name + description (same as drawer row)
  - ✏️ `IconButton` → navigate to `"edit_collection/{collectionId}"`
  - 🗑 `IconButton` → show confirmation `AlertDialog`, then delete
- Drag-and-drop reordering via `sh.calvin.reorderable:reorderable` library
- `sortOrder` is persisted immediately on drop (`reorderCollections()`)

### EditCollectionScreen (new)

- Route: `"edit_collection/{collectionId}"`
- Layout identical to `CreateCollectionScreen`:
  - Emoji picker (tappable emoji chip opens bottom sheet)
  - Name `TextField` (required)
  - Description `TextField` (optional)
  - Board picker (shows current subscriptions pre-selected)
- `TopAppBar`: title "編輯 Collection" + back button + "儲存" action button
- On save: calls `updateCollection()` + diffs board subscriptions, then navigates back

---

## Navigation Changes (`AppScreen.kt`)

```kotlin
// AppDrawer
onManageCollectionsClick = { navController.navigate("manage_collections") }

// NavHost new destinations
composable("manage_collections") {
    ManageCollectionsScreen(
        onNavigateUp = { navController.navigateUp() },
        onEditCollection = { id -> navController.navigate("edit_collection/$id") },
    )
}
composable(
    "edit_collection/{collectionId}",
    arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
) {
    EditCollectionScreen(
        onNavigateUp = { navController.navigateUp() },
    )
}
```

---

## Data Layer

### CollectionDao additions

```kotlin
@Update
suspend fun updateCollection(entity: CollectionEntity)

@Transaction
suspend fun reorderCollections(entities: List<CollectionEntity>) {
    entities.forEach { updateCollection(it) }
}
```

### CollectionRepository interface additions

```kotlin
suspend fun updateCollection(id: String, name: String, description: String, emoji: String)
suspend fun reorderCollections(orderedIds: List<String>)
```

### CollectionRepositoryImpl

- `updateCollection`: `getById(id)` → copy with new fields → `dao.updateCollection()`
- `reorderCollections`: map `orderedIds` index to `sortOrder` → `dao.reorderCollections()`

### Board subscription diff (in EditCollectionViewModel.save())

- Compare existing subscriptions with `selectedBoards` state
- Call `addBoardSubscription()` for new entries
- Call `removeBoardSubscription()` for removed entries
- No new repository methods required

---

## ViewModels

### ManageCollectionsViewModel (new)

```kotlin
@HiltViewModel
class ManageCollectionsViewModel @Inject constructor(
    private val collectionRepo: CollectionRepository,
) : ViewModel() {
    val collections: StateFlow<List<CollectionEntity>> =
        collectionRepo.observeCollections()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteCollection(id: String) {
        viewModelScope.launch { collectionRepo.deleteCollection(id) }
    }

    fun reorderCollections(orderedIds: List<String>) {
        viewModelScope.launch { collectionRepo.reorderCollections(orderedIds) }
    }
}
```

### EditCollectionViewModel (new)

```kotlin
@HiltViewModel
class EditCollectionViewModel @Inject constructor(
    private val collectionRepo: CollectionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val collectionId: String = checkNotNull(savedStateHandle["collectionId"])

    // StateFlows: name, description, emoji (loaded from DB on init)
    // selectedBoards: pre-populated from existing subscriptions
    // BoardPickerViewModel reused as-is

    private val _saved = MutableSharedFlow<Unit>()
    val saved = _saved.asSharedFlow()

    fun save() {
        viewModelScope.launch {
            collectionRepo.updateCollection(collectionId, name, description, emoji)
            // diff and apply board subscription changes
            _saved.emit(Unit)
        }
    }
}
```

---

## Files to Create / Modify

| File | Change |
|------|--------|
| `app/.../component/AppDrawer.kt` | Add `onManageCollectionsClick` param, add Row + IconButton |
| `app/.../AppScreen.kt` | Wire new param, add 2 new composable routes |
| `app/.../collection/ManageCollectionsScreen.kt` | New file |
| `app/.../collection/ManageCollectionsViewModel.kt` | New file |
| `app/.../collection/EditCollectionScreen.kt` | New file |
| `app/.../collection/EditCollectionViewModel.kt` | New file |
| `collection/.../CollectionDao.kt` | Add `updateCollection`, `reorderCollections` |
| `collection/.../CollectionRepository.kt` | Add `updateCollection`, `reorderCollections` |
| `collection/.../CollectionRepositoryImpl.kt` | Implement new methods |

---

## Out of Scope

- Renaming a collection inline (no inline editing in the management list)
- Undo/redo for delete
- Moving boards between collections
