package tw.kevinzhang.newshub.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kevinzhang.data.ReadingHistoryRepository
import tw.kevinzhang.extension_loader.ExtensionLoader
import tw.kevinzhang.newshub.data.PreferenceStore
import tw.kevinzhang.newshub.data.TimelineDisplayMode
import javax.inject.Inject

@HiltViewModel
class ReadingHistoryViewModel @Inject constructor(
    private val repository: ReadingHistoryRepository,
    extensionLoader: ExtensionLoader,
    preferenceStore: PreferenceStore,
) : ViewModel() {
    val history = repository.observeReadingHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rawImageSourceIds: StateFlow<Set<String>> = extensionLoader.sourcesFlow
        .map { sources -> sources.filter { it.alwaysUseRawImage }.mapTo(mutableSetOf()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val timelineDisplayMode: StateFlow<TimelineDisplayMode> = preferenceStore.observable
        .map { it.timelineDisplayMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineDisplayMode.COMPACT)

    fun deleteAll() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
