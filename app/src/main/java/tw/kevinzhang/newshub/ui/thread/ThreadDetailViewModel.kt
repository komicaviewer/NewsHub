package tw.kevinzhang.newshub.ui.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.extension_loader.ExtensionLoader
import javax.inject.Inject

@HiltViewModel
class ThreadDetailViewModel @Inject constructor(
    private val extensionLoader: ExtensionLoader,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val threadId: String = checkNotNull(savedStateHandle["threadId"]) {
        "ThreadDetailViewModel requires 'threadId' in SavedStateHandle"
    }
    private val sourceId: String = checkNotNull(savedStateHandle["sourceId"]) {
        "ThreadDetailViewModel requires 'sourceId' in SavedStateHandle"
    }
    private val boardUrl: String = checkNotNull(savedStateHandle["boardUrl"]) {
        "ThreadDetailViewModel requires 'boardUrl' in SavedStateHandle"
    }
    private val threadTitle: String? = savedStateHandle["threadTitle"]

    private val _thread = MutableStateFlow<Thread?>(null)
    val thread = _thread.asStateFlow()

    val threadUrl: StateFlow<String?> = _thread
        .map { it?.url }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val previewPost = MutableStateFlow<Post?>(null)

    init {
        viewModelScope.launch {
            val source = extensionLoader.getSource(sourceId) ?: return@launch
            val summary = ThreadSummary(
                sourceId = sourceId,
                boardUrl = boardUrl,
                id = threadId,
                title = threadTitle,
                author = null,
                createdAt = null,
                replyCount = null,
                thumbnail = null,
                previewContent = emptyList(),
            )
            _thread.value = source.getThread(summary)
        }
    }

    fun onReplyToClick(targetId: String) {
        previewPost.value = _thread.value?.posts?.find { it.id == targetId }
    }

    fun dismissPreview() {
        previewPost.value = null
    }
}
