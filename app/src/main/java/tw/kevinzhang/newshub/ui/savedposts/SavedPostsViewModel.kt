package tw.kevinzhang.newshub.ui.savedposts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kevinzhang.collection.SavedPostRepository
import javax.inject.Inject

@HiltViewModel
class SavedPostsViewModel @Inject constructor(
    private val repository: SavedPostRepository,
) : ViewModel() {
    val savedPosts = repository.observeSavedPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteAll() {
        viewModelScope.launch {
            repository.deleteAllSavedPosts()
        }
    }
}
