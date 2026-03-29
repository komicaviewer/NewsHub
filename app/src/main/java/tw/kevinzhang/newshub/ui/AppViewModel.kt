package tw.kevinzhang.newshub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kevinzhang.newshub.data.PreferenceStore
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferenceStore: PreferenceStore,
) : ViewModel() {

    val defaultCollectionId: StateFlow<String?> = preferenceStore.observable
        .map { it.defaultCollectionId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectCollection(id: String) {
        viewModelScope.launch {
            preferenceStore.setDefaultCollectionId(id)
        }
    }
}
