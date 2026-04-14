package tw.kevinzhang.newshub.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tw.kevinzhang.data.CollectionRepository
import javax.inject.Inject

@HiltViewModel
class CreateCollectionViewModel @Inject constructor(
    private val collectionRepo: CollectionRepository,
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()

    private val _description = MutableStateFlow("")
    val description = _description.asStateFlow()

    private val _emoji = MutableStateFlow("📰")
    val emoji = _emoji.asStateFlow()

    private val _selectedBoards = MutableStateFlow<Set<SelectedBoard>>(emptySet())
    val selectedBoards = _selectedBoards.asStateFlow()

    private val _createdCollectionId = MutableSharedFlow<String>()
    val createdCollectionId = _createdCollectionId.asSharedFlow()

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
            val id = collectionRepo.createCollection(
                name = currentName,
                description = _description.value.trim(),
                emoji = _emoji.value,
            )
            _selectedBoards.value.forEach { board ->
                collectionRepo.addBoardSubscription(
                    collectionId = id,
                    sourceId = board.sourceId,
                    boardUrl = board.boardUrl,
                    boardName = board.boardName,
                )
            }
            _createdCollectionId.emit(id)
        }
    }
}
