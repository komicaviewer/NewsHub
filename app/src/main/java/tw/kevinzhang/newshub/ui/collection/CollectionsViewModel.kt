package tw.kevinzhang.newshub.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import tw.kevinzhang.collection.CollectionRepository
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val collectionRepo: CollectionRepository,
) : ViewModel() {
    val collections = collectionRepo.observeCollections()

    fun createCollection(name: String) {
        viewModelScope.launch {
            collectionRepo.createCollection(name)
        }
    }
}
