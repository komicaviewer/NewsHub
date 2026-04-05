package tw.kevinzhang.newshub.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kevinzhang.collection.CollectionRepository
import tw.kevinzhang.collection.data.CollectionEntity
import javax.inject.Inject

@HiltViewModel
class ManageCollectionsViewModel @Inject constructor(
    private val collectionRepo: CollectionRepository,
) : ViewModel() {

    val collections: StateFlow<List<CollectionEntity>> = collectionRepo.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteCollection(id: String) {
        viewModelScope.launch { collectionRepo.deleteCollection(id) }
    }

    fun reorderCollections(orderedIds: List<String>) {
        viewModelScope.launch { collectionRepo.reorderCollections(orderedIds) }
    }
}
