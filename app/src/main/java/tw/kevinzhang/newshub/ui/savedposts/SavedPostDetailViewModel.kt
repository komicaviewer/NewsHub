package tw.kevinzhang.newshub.ui.savedposts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kevinzhang.data.SavedPostRepository
import tw.kevinzhang.data.SavedPostAssetStore
import javax.inject.Inject

@HiltViewModel
class SavedPostDetailViewModel @Inject constructor(
    private val repository: SavedPostRepository,
    private val savedPostAssetStore: SavedPostAssetStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val sourceKey: String = checkNotNull(savedStateHandle["sourceKey"])
    private val threadId: String = checkNotNull(savedStateHandle["threadId"])

    val entity = repository.observeSavedPost(sourceKey, threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val screenshotFiles = entity
        .map { e ->
            if (e == null) emptyList()
            else savedPostAssetStore.resolveReferences(e.savedPost.screenshotAssetRefs)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteCurrentPost() {
        viewModelScope.launch {
            repository.unsavePost(sourceKey, threadId)
        }
    }
}
