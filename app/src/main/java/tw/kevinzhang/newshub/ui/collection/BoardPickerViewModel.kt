package tw.kevinzhang.newshub.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_loader.ExtensionLoader
import javax.inject.Inject

data class SourceWithBoards(val source: Source, val boards: List<Board>)

data class SelectedBoard(
    val sourceId: String,
    val boardUrl: String,
    val boardName: String,
) {
    val key: String get() = "$sourceId:$boardUrl"
}

@HiltViewModel
class BoardPickerViewModel @Inject constructor(
    private val extensionLoader: ExtensionLoader,
) : ViewModel() {

    private val _sourcesWithBoards = MutableStateFlow<List<SourceWithBoards>>(emptyList())
    val sourcesWithBoards = _sourcesWithBoards.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            _sourcesWithBoards.value = extensionLoader.getAllSources().map { source ->
                SourceWithBoards(
                    source = source,
                    boards = runCatching { source.getBoards() }
                        .getOrDefault(emptyList())
                        .distinctBy { it.url },
                )
            }
            _isLoading.value = false
        }
    }
}
