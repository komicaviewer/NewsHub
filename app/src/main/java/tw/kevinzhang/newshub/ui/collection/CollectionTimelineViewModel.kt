package tw.kevinzhang.newshub.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import tw.kevinzhang.collection.CollectionRepository
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.extension_loader.ExtensionLoader
import javax.inject.Inject

@HiltViewModel
class CollectionTimelineViewModel @Inject constructor(
    private val collectionRepo: CollectionRepository,
    private val extensionLoader: ExtensionLoader,
) : ViewModel() {

    fun getTimelinePager(collectionId: String): Flow<PagingData<ThreadSummary>> {
        return collectionRepo.observeSubscriptions(collectionId)
            .flatMapLatest { subs ->
                Pager(PagingConfig(pageSize = 20)) {
                    MergedTimelinePagingSource(
                        subscriptions = subs,
                        sourceResolver = { extensionLoader.getSource(it) },
                    )
                }.flow
            }
            .cachedIn(viewModelScope)
    }
}
