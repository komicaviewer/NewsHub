package tw.kevinzhang.newshub.ui.thread

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.flatMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import tw.kevinzhang.hub_server.data.Paragraph
import tw.kevinzhang.newshub.interactor.BoardInteractor
import tw.kevinzhang.newshub.interactor.ThreadInteractor
import tw.kevinzhang.newshub.model.toThumbnail
import javax.inject.Inject

@HiltViewModel
class ThreadViewModel @Inject constructor(
    private val threadInteractor: ThreadInteractor,
    private val boardInteractor: BoardInteractor,
) : ViewModel() {
    private val _threadUrl = MutableStateFlow("")
    private val _rePostId = MutableStateFlow("")

    val boardName = _threadUrl.mapLatest {
        if (it.isNotBlank()) {
            val board = boardInteractor.get(it)
            board.name
        } else {
            ""
        }
    }

    val pagingPosts = _threadUrl
        .combineTransform(_rePostId) { threadUrl, rePostId ->
            if (threadUrl.isNotBlank()) {
                threadInteractor.getAll(threadUrl, rePostId.takeIf { it.isNotBlank() })
            } else {
                emptyFlow()
            }.collect(this)
        }
        .catch { Log.e("ThreadViewModel", it.stackTraceToString()) }
        .cachedIn(viewModelScope)

    val gallery = pagingPosts.map { _pagingPosts ->
        _pagingPosts.flatMap { p ->
            p.content.mapNotNull {
                when (it) {
                    is Paragraph.ImageInfo -> it.toThumbnail()
                    is Paragraph.VideoInfo -> it.toThumbnail()
                    else -> null

               }
            }
        }
    }

    fun thread(threadUrl: String) {
        this._threadUrl.update { threadUrl }
    }

    fun rePostId(rePostId: String) {
        this._rePostId.update { rePostId }
    }

    fun clear() {
        viewModelScope.launch {
            threadInteractor.removeThread(_threadUrl.value)
        }
    }
}