# Manage Collections Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a pencil icon button to the app drawer that navigates to a full-screen Manage Collections screen with drag-to-reorder, delete, and individual edit flows.

**Architecture:** New `ManageCollectionsScreen` + `EditCollectionScreen` with dedicated ViewModels. Data layer gains `updateCollection` and `reorderCollections` on `CollectionDao` and `CollectionRepository`. Navigation wired via two new routes in `AppScreen.kt`.

**Tech Stack:** Jetpack Compose, Room, Hilt, StateFlow, `sh.calvin.reorderable:reorderable` (drag-and-drop)

---

## File Map

| Action | File |
|--------|------|
| Modify | `app/build.gradle` |
| Modify | `collection/src/main/java/tw/kevinzhang/collection/data/CollectionDao.kt` |
| Modify | `collection/src/main/java/tw/kevinzhang/collection/CollectionRepository.kt` |
| Modify | `collection/src/main/java/tw/kevinzhang/collection/CollectionRepositoryImpl.kt` |
| Create | `app/src/main/java/tw/kevinzhang/newshub/ui/collection/ManageCollectionsViewModel.kt` |
| Create | `app/src/main/java/tw/kevinzhang/newshub/ui/collection/ManageCollectionsScreen.kt` |
| Create | `app/src/main/java/tw/kevinzhang/newshub/ui/collection/EditCollectionViewModel.kt` |
| Create | `app/src/main/java/tw/kevinzhang/newshub/ui/collection/EditCollectionScreen.kt` |
| Modify | `app/src/main/java/tw/kevinzhang/newshub/ui/component/AppDrawer.kt` |
| Modify | `app/src/main/java/tw/kevinzhang/newshub/ui/AppScreen.kt` |

---

## Task 1: Extend data layer — DAO + Repository

**Files:**
- Modify: `collection/src/main/java/tw/kevinzhang/collection/data/CollectionDao.kt`
- Modify: `collection/src/main/java/tw/kevinzhang/collection/CollectionRepository.kt`
- Modify: `collection/src/main/java/tw/kevinzhang/collection/CollectionRepositoryImpl.kt`

- [ ] **Step 1: Add `updateCollection` to `CollectionDao`**

Open `collection/src/main/java/tw/kevinzhang/collection/data/CollectionDao.kt` and add after `deleteCollection`:

```kotlin
@Update
suspend fun updateCollection(entity: CollectionEntity)
```

Final file:
```kotlin
package tw.kevinzhang.collection.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sortOrder")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(entity: CollectionEntity)

    @Delete
    suspend fun deleteCollection(entity: CollectionEntity)

    @Update
    suspend fun updateCollection(entity: CollectionEntity)

    @Query("SELECT * FROM board_subscriptions WHERE collectionId = :collectionId ORDER BY sortOrder")
    fun observeSubscriptions(collectionId: String): Flow<List<BoardSubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(entity: BoardSubscriptionEntity)

    @Delete
    suspend fun deleteSubscription(entity: BoardSubscriptionEntity)

    @Query("DELETE FROM board_subscriptions WHERE sourceId = :sourceId")
    suspend fun deleteSubscriptionsBySource(sourceId: String)

    @Query("DELETE FROM board_subscriptions WHERE id = :id")
    suspend fun deleteSubscriptionById(id: String)

    @Query("DELETE FROM board_subscriptions WHERE collectionId = :collectionId")
    suspend fun deleteSubscriptionsByCollection(collectionId: String)
}
```

- [ ] **Step 2: Add `updateCollection` and `reorderCollections` to `CollectionRepository` interface**

Replace the contents of `collection/src/main/java/tw/kevinzhang/collection/CollectionRepository.kt`:

```kotlin
package tw.kevinzhang.collection

import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.collection.data.BoardSubscriptionEntity
import tw.kevinzhang.collection.data.CollectionEntity

interface CollectionRepository {
    fun observeCollections(): Flow<List<CollectionEntity>>
    fun observeSubscriptions(collectionId: String): Flow<List<BoardSubscriptionEntity>>
    suspend fun createCollection(name: String, description: String = "", emoji: String = "📰"): String
    suspend fun deleteCollection(id: String)
    suspend fun updateCollection(id: String, name: String, description: String, emoji: String)
    suspend fun reorderCollections(orderedIds: List<String>)
    suspend fun addBoardSubscription(collectionId: String, sourceId: String, boardUrl: String, boardName: String)
    suspend fun removeBoardSubscription(subscriptionId: String)
    suspend fun removeAllSubscriptionsForSource(sourceId: String)
}
```

- [ ] **Step 3: Implement `updateCollection` and `reorderCollections` in `CollectionRepositoryImpl`**

Open `collection/src/main/java/tw/kevinzhang/collection/CollectionRepositoryImpl.kt` and add the two new override functions after `deleteCollection`:

```kotlin
override suspend fun updateCollection(id: String, name: String, description: String, emoji: String) {
    val entity = dao.getById(id) ?: return
    dao.updateCollection(entity.copy(name = name, description = description, emoji = emoji))
}

override suspend fun reorderCollections(orderedIds: List<String>) {
    orderedIds.forEachIndexed { index, id ->
        val entity = dao.getById(id) ?: return@forEachIndexed
        dao.updateCollection(entity.copy(sortOrder = index))
    }
}
```

- [ ] **Step 4: Build the `collection` module to verify no compile errors**

```bash
./gradlew :collection:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add collection/src/main/java/tw/kevinzhang/collection/data/CollectionDao.kt \
        collection/src/main/java/tw/kevinzhang/collection/CollectionRepository.kt \
        collection/src/main/java/tw/kevinzhang/collection/CollectionRepositoryImpl.kt
git commit -m "feat(collection): add updateCollection and reorderCollections to DAO and repository"
```

---

## Task 2: Add reorderable library dependency

**Files:**
- Modify: `app/build.gradle`

- [ ] **Step 1: Add the dependency**

Open `app/build.gradle` and add inside the `dependencies { }` block, after the other compose dependencies:

```groovy
implementation "sh.calvin.reorderable:reorderable:2.3.3"
```

- [ ] **Step 2: Sync and verify**

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep reorderable
```

Expected: output includes `sh.calvin.reorderable:reorderable:2.3.3`

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle
git commit -m "build(app): add sh.calvin.reorderable dependency for drag-to-reorder"
```

---

## Task 3: ManageCollectionsViewModel

**Files:**
- Create: `app/src/main/java/tw/kevinzhang/newshub/ui/collection/ManageCollectionsViewModel.kt`

- [ ] **Step 1: Create `ManageCollectionsViewModel.kt`**

```kotlin
package tw.kevinzhang.newshub.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kevinzhang.collection.CollectionRepository
import tw.kevinzhang.collection.data.CollectionEntity
import javax.inject.Inject

@HiltViewModel
class ManageCollectionsViewModel @Inject constructor(
    private val collectionRepo: CollectionRepository,
) : ViewModel() {

    val collections: StateFlow<List<CollectionEntity>> = collectionRepo.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteCollection(id: String) {
        viewModelScope.launch { collectionRepo.deleteCollection(id) }
    }

    fun reorderCollections(orderedIds: List<String>) {
        viewModelScope.launch { collectionRepo.reorderCollections(orderedIds) }
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tw/kevinzhang/newshub/ui/collection/ManageCollectionsViewModel.kt
git commit -m "feat(collection): add ManageCollectionsViewModel"
```

---

## Task 4: ManageCollectionsScreen

**Files:**
- Create: `app/src/main/java/tw/kevinzhang/newshub/ui/collection/ManageCollectionsScreen.kt`

- [ ] **Step 1: Create `ManageCollectionsScreen.kt`**

The `sh.calvin.reorderable` v2.x API requires a separate `lazyListState` passed into `rememberReorderableLazyListState`. The `onMove` callback updates local order and immediately persists via ViewModel.

```kotlin
package tw.kevinzhang.newshub.ui.collection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tw.kevinzhang.collection.data.CollectionEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCollectionsScreen(
    onNavigateUp: () -> Unit,
    onEditCollection: (collectionId: String) -> Unit,
    viewModel: ManageCollectionsViewModel = hiltViewModel(),
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    // Local order for responsive drag-and-drop; syncs from DB when collections changes
    var localOrder by remember(collections) { mutableStateOf(collections) }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localOrder = localOrder.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        viewModel.reorderCollections(localOrder.map { it.id })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理 Collections") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (localOrder.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "尚無 Collections",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(localOrder, key = { it.id }) { collection ->
                    ReorderableItem(reorderState, key = collection.id) { isDragging ->
                        CollectionManageRow(
                            collection = collection,
                            isDragging = isDragging,
                            dragModifier = Modifier.draggableHandle(),
                            onEditClick = { onEditCollection(collection.id) },
                            onDeleteClick = { pendingDeleteId = collection.id },
                        )
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        val name = localOrder.firstOrNull { it.id == id }?.name ?: ""
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("刪除 Collection") },
            text = { Text("確定要刪除「$name」嗎？此操作無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCollection(id)
                    pendingDeleteId = null
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CollectionManageRow(
    collection: CollectionEntity,
    isDragging: Boolean,
    dragModifier: Modifier,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "拖曳排序",
            modifier = dragModifier.padding(end = 12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = collection.emoji,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = collection.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onEditClick) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "編輯",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onDeleteClick) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "刪除",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tw/kevinzhang/newshub/ui/collection/ManageCollectionsScreen.kt
git commit -m "feat(collection): add ManageCollectionsScreen with drag-to-reorder and delete"
```

---

## Task 5: EditCollectionViewModel

**Files:**
- Create: `app/src/main/java/tw/kevinzhang/newshub/ui/collection/EditCollectionViewModel.kt`

- [ ] **Step 1: Create `EditCollectionViewModel.kt`**

```kotlin
package tw.kevinzhang.newshub.ui.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tw.kevinzhang.collection.CollectionRepository
import tw.kevinzhang.collection.data.BoardSubscriptionEntity
import javax.inject.Inject

@HiltViewModel
class EditCollectionViewModel @Inject constructor(
    private val collectionRepo: CollectionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val collectionId: String = checkNotNull(savedStateHandle["collectionId"])

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()

    private val _description = MutableStateFlow("")
    val description = _description.asStateFlow()

    private val _emoji = MutableStateFlow("📰")
    val emoji = _emoji.asStateFlow()

    // Tracks the IDs of subscriptions that existed when the screen was opened
    private var originalSubscriptions: List<BoardSubscriptionEntity> = emptyList()

    private val _selectedBoards = MutableStateFlow<Set<SelectedBoard>>(emptySet())
    val selectedBoards = _selectedBoards.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved = _saved.asSharedFlow()

    init {
        viewModelScope.launch {
            val collection = collectionRepo.observeCollections().first()
                .firstOrNull { it.id == collectionId } ?: return@launch
            _name.value = collection.name
            _description.value = collection.description
            _emoji.value = collection.emoji

            originalSubscriptions = collectionRepo.observeSubscriptions(collectionId).first()
            _selectedBoards.value = originalSubscriptions.map { sub ->
                SelectedBoard(
                    sourceId = sub.sourceId,
                    boardUrl = sub.boardUrl,
                    boardName = sub.boardName,
                )
            }.toSet()
        }
    }

    fun onNameChange(value: String) { _name.value = value }
    fun onDescriptionChange(value: String) { _description.value = value }
    fun onEmojiChange(value: String) { _emoji.value = value }

    fun toggleBoard(board: SelectedBoard) {
        _selectedBoards.update { if (board in it) it - board else it + board }
    }

    fun save() {
        val currentName = _name.value.trim()
        if (currentName.isBlank()) return
        viewModelScope.launch {
            collectionRepo.updateCollection(
                id = collectionId,
                name = currentName,
                description = _description.value.trim(),
                emoji = _emoji.value,
            )

            // Diff board subscriptions
            val existingKeys = originalSubscriptions.associateBy { "${it.sourceId}:${it.boardUrl}" }
            val newKeys = _selectedBoards.value.associateBy { it.key }

            // Add new subscriptions
            newKeys.keys.filter { it !in existingKeys }.forEach { key ->
                val board = newKeys.getValue(key)
                collectionRepo.addBoardSubscription(
                    collectionId = collectionId,
                    sourceId = board.sourceId,
                    boardUrl = board.boardUrl,
                    boardName = board.boardName,
                )
            }

            // Remove deleted subscriptions
            existingKeys.keys.filter { it !in newKeys }.forEach { key ->
                val sub = existingKeys.getValue(key)
                collectionRepo.removeBoardSubscription(sub.id)
            }

            _saved.emit(Unit)
        }
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tw/kevinzhang/newshub/ui/collection/EditCollectionViewModel.kt
git commit -m "feat(collection): add EditCollectionViewModel with board subscription diff"
```

---

## Task 6: EditCollectionScreen

**Files:**
- Create: `app/src/main/java/tw/kevinzhang/newshub/ui/collection/EditCollectionScreen.kt`

- [ ] **Step 1: Create `EditCollectionScreen.kt`**

Layout identical to `CreateCollectionScreen` but uses `EditCollectionViewModel` and shows title "編輯 Collection".

```kotlin
package tw.kevinzhang.newshub.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCollectionScreen(
    onNavigateUp: () -> Unit,
    viewModel: EditCollectionViewModel = hiltViewModel(),
    boardPickerViewModel: BoardPickerViewModel = hiltViewModel(),
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val emoji by viewModel.emoji.collectAsStateWithLifecycle()
    val selectedBoards by viewModel.selectedBoards.collectAsStateWithLifecycle()
    val sourcesWithBoards by boardPickerViewModel.sourcesWithBoards.collectAsStateWithLifecycle()
    val isBoardPickerLoading by boardPickerViewModel.isLoading.collectAsStateWithLifecycle()

    var showEmojiPicker by remember { mutableStateOf(false) }
    var showBoardPicker by remember { mutableStateOf(false) }
    val emojiSheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onNavigateUp() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("編輯 Collection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = name.isNotBlank(),
                    ) {
                        Text("儲存")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showBoardPicker = true }) {
                BadgedBox(
                    badge = {
                        if (selectedBoards.isNotEmpty()) {
                            Badge { Text(selectedBoards.size.toString()) }
                        }
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "選擇 Board")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .clickable { showEmojiPicker = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, fontSize = 40.sp)
                }
                Text(
                    text = "選擇 Emoji",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.onNameChange(it) },
                label = { Text("Collection 名稱") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = description,
                onValueChange = { viewModel.onDescriptionChange(it) },
                label = { Text("說明（選填）") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            if (selectedBoards.isNotEmpty()) {
                Text(
                    text = "已選擇 ${selectedBoards.size} 個 Board",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showEmojiPicker) {
        ModalBottomSheet(
            onDismissRequest = { showEmojiPicker = false },
            sheetState = emojiSheetState,
        ) {
            AndroidView(
                factory = { ctx ->
                    EmojiPickerView(ctx).apply {
                        setOnEmojiPickedListener { item ->
                            viewModel.onEmojiChange(item.emoji)
                            showEmojiPicker = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
            )
        }
    }

    if (showBoardPicker) {
        BoardPickerDialog(
            sourcesWithBoards = sourcesWithBoards,
            isLoading = isBoardPickerLoading,
            selectedBoards = selectedBoards,
            onBoardToggle = { board -> viewModel.toggleBoard(board) },
            onConfirm = { showBoardPicker = false },
            onDismiss = { showBoardPicker = false },
        )
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tw/kevinzhang/newshub/ui/collection/EditCollectionScreen.kt
git commit -m "feat(collection): add EditCollectionScreen"
```

---

## Task 7: Wire AppDrawer + AppScreen navigation

**Files:**
- Modify: `app/src/main/java/tw/kevinzhang/newshub/ui/component/AppDrawer.kt`
- Modify: `app/src/main/java/tw/kevinzhang/newshub/ui/AppScreen.kt`

- [ ] **Step 1: Update `AppDrawer` — add `onManageCollectionsClick` param and Row layout**

Replace the "New Collection button" section in `AppDrawer.kt` (lines 115–126) with:

```kotlin
// Bottom row: New Collection + Manage
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
) {
    Button(
        onClick = onCreateCollectionClick,
        modifier = Modifier.weight(1f),
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Text("New Collection")
    }
    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
    IconButton(onClick = onManageCollectionsClick) {
        Icon(
            Icons.Default.Edit,
            contentDescription = "管理 Collections",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

Also add `onManageCollectionsClick: () -> Unit` as the third parameter of `AppDrawer`:

```kotlin
@Composable
fun AppDrawer(
    onCollectionClick: (CollectionEntity) -> Unit,
    onCreateCollectionClick: () -> Unit,
    onManageCollectionsClick: () -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
)
```

Add the missing imports at the top:
```kotlin
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.IconButton
```

- [ ] **Step 2: Update `AppScreen.kt` — wire new param and add two new routes**

In `bindAppScreen`, update the `AppDrawer` call inside `drawerContent` to pass the new lambda:

```kotlin
AppDrawer(
    onCollectionClick = { collection ->
        appViewModel.selectCollection(collection.id)
        navController.navigate("collection/${collection.id}") {
            popUpTo("collection/{collectionId}") { inclusive = true }
            launchSingleTop = false
        }
        coroutineScope.launch { drawerState.close() }
    },
    onCreateCollectionClick = {
        coroutineScope.launch { drawerState.close() }
        navController.navigate("create_collection")
    },
    onManageCollectionsClick = {
        coroutineScope.launch { drawerState.close() }
        navController.navigate("manage_collections")
    },
)
```

Then add two new `composable` entries at the bottom of the `NavHost` block, after `"create_collection"`:

```kotlin
composable("manage_collections") {
    ManageCollectionsScreen(
        onNavigateUp = { navController.navigateUp() },
        onEditCollection = { id -> navController.navigate("edit_collection/$id") },
    )
}
composable(
    route = "edit_collection/{collectionId}",
    arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
) {
    EditCollectionScreen(
        onNavigateUp = { navController.navigateUp() },
    )
}
```

Add the missing imports to `AppScreen.kt`:
```kotlin
import tw.kevinzhang.newshub.ui.collection.EditCollectionScreen
import tw.kevinzhang.newshub.ui.collection.ManageCollectionsScreen
```

- [ ] **Step 3: Build the full app**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/tw/kevinzhang/newshub/ui/component/AppDrawer.kt \
        app/src/main/java/tw/kevinzhang/newshub/ui/AppScreen.kt
git commit -m "feat(collection): wire manage/edit collection routes and drawer entry point"
```

---

## Done

All tasks complete. The feature is fully implemented:
- ✏️ icon in drawer → ManageCollectionsScreen
- Drag handles for reordering (persisted on drop)
- Delete with confirmation dialog
- ✏️ per row → EditCollectionScreen (pre-filled, full board diff on save)
