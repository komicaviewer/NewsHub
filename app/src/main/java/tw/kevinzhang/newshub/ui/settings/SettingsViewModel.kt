package tw.kevinzhang.newshub.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kevinzhang.newshub.data.PreferenceStore
import tw.kevinzhang.newshub.data.ReadTrackingMode
import tw.kevinzhang.newshub.data.ReadingPreferences
import tw.kevinzhang.newshub.data.ReplyDisplayMode
import tw.kevinzhang.newshub.data.TimelineDisplayMode
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceStore: PreferenceStore,
) : ViewModel() {

    val readingPreferences: StateFlow<ReadingPreferences> = preferenceStore.observable
        .map { it.readingPreferences }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReadingPreferences(),
        )

    fun setTimelineDisplayMode(mode: TimelineDisplayMode) {
        viewModelScope.launch {
            preferenceStore.setTimelineDisplayMode(mode)
        }
    }

    fun setReplyDisplayMode(mode: ReplyDisplayMode) {
        viewModelScope.launch {
            preferenceStore.setReplyDisplayMode(mode)
        }
    }

    fun setReadTrackingMode(mode: ReadTrackingMode) {
        viewModelScope.launch {
            preferenceStore.setReadTrackingMode(mode)
        }
    }
}
