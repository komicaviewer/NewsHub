package tw.kevinzhang.newshub.ui.topic

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import tw.kevinzhang.newshub.interactor.*
import javax.inject.Inject

@HiltViewModel
class TopicListViewModel @Inject constructor(
    private val topicInteractor: TopicInteractor,
) : ViewModel() {
    val topicList = topicInteractor.getAll()
}