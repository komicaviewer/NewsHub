package tw.kevinzhang.newshub.ui.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private val _selectedBoards = MutableStateFlow<Set<SelectedBoard>>(emptySet())
    val selectedBoards = _selectedBoards.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved = _saved.asSharedFlow()

    // Captured once at init time. Safe because navigation creates a new ViewModel instance
    // per destination, so originalSubscriptions won't be stale across navigation cycles.
    private var originalSubscriptions: List<BoardSubscriptionEntity> = emptyList()

    init {
        viewModelScope.launch {
            val collection = collectionRepo.getCollectionById(collectionId) ?: return@launch
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
