package tw.kevinzhang.newshub.ui.savedposts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tw.kevinzhang.collection.SavedPostRepository
import javax.inject.Inject

@HiltViewModel
class SavedPostDetailViewModel @Inject constructor(
    repository: SavedPostRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val sourceId: String = checkNotNull(savedStateHandle["sourceId"])
    private val threadId: String = checkNotNull(savedStateHandle["threadId"])

    private val pathListType = object : TypeToken<List<String>>() {}.type
    private val gson = Gson()

    val entity = repository.observeSavedPost(sourceId, threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val screenshotPaths = entity
        .map { e ->
            if (e == null) emptyList()
            else try {
                gson.fromJson<List<String>>(e.screenshotPaths, pathListType) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
