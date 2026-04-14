package tw.kevinzhang.newshub.ui.savedposts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import tw.kevinzhang.collection.SavedPostRepository
import javax.inject.Inject

@HiltViewModel
class SavedPostsViewModel @Inject constructor(
    repository: SavedPostRepository,
) : ViewModel() {
    val savedPosts = repository.observeSavedPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
