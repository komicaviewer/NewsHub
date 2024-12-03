package tw.kevinzhang.newshub.ui.topic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import tw.kevinzhang.hub_server.data.board.Board
import tw.kevinzhang.newshub.interactor.NewsInteractor
import tw.kevinzhang.newshub.interactor.BoardInteractor
import tw.kevinzhang.newshub.interactor.TopicInteractor
import javax.inject.Inject

@HiltViewModel
class NewsListViewModel @Inject constructor(
    private val newsInteractor: NewsInteractor,
    private val topicInteractor: TopicInteractor,
    private val boardInteractor: BoardInteractor,
) : ViewModel() {
    companion object {
        const val defaultTopicId = "Square"
    }

    private val _topicId = MutableStateFlow(defaultTopicId)
    private val _selectedBoards = MutableStateFlow(emptyList<Board>())

    val allBoards = boardInteractor.getAll()
    val topic = _topicId.mapLatest {
        topicInteractor.get(it)
    }
    val subscribedBoards = topic.flatMapLatest {
        val boards = boardInteractor.getSubscribed(it.id)
        _selectedBoards.update { boards.first() }
        boards
    }
    val selectedBoards = _selectedBoards.asStateFlow()
    val pagingNews = _selectedBoards.map {
        newsInteractor.getAll(it.toSet())
    }
        .flatMapLatest { it }
        .cachedIn(viewModelScope)

    fun topicId(topicId: String) {
        this._topicId.update { topicId }
    }

    fun unselectBoard(board: Board) {
        this._selectedBoards.update { it.minus(board).toSet().toList() }
    }

    fun selectBoard(board: Board) {
        this._selectedBoards.update { it.plus(board).toSet().toList() }
    }

    fun unsubscribeBoard(board: Board) {
        viewModelScope.launch {
            boardInteractor.unsubscribe(board, _topicId.value)
        }
    }

    fun subscribeBoard(board: Board) {
        viewModelScope.launch {
            boardInteractor.subscribe(board, _topicId.value)
        }
    }

    fun clearAllNews() {
        viewModelScope.launch {
            newsInteractor.clearAll(_selectedBoards.value.toSet())
        }
    }

    suspend fun readBoardName(boardUrl: String): String {
        return boardInteractor.get(boardUrl).name
    }
}