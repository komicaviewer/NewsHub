package tw.kevinzhang.newshub.ui.savedposts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kevinzhang.data.SavedPostRepository
import tw.kevinzhang.data.SavedPostAssetStore
import tw.kevinzhang.data.SourceIdentityRepository
import tw.kevinzhang.data.domain.CanonicalSourceIdentities
import tw.kevinzhang.data.domain.SourceResolution
import tw.kevinzhang.extension_api.HostResourceProvider
import tw.kevinzhang.extension_loader.ExtensionLoader
import javax.inject.Inject

@HiltViewModel
class SavedPostDetailViewModel @Inject constructor(
    private val repository: SavedPostRepository,
    private val savedPostAssetStore: SavedPostAssetStore,
    private val sourceIdentityRepository: SourceIdentityRepository,
    private val extensionLoader: ExtensionLoader,
    internal val resourceProvider: HostResourceProvider,
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

    /** Resolves the current trusted Source and asks it for a new single-use Host link handle. */
    fun requestWebLink(onReady: (String) -> Unit, onRejected: () -> Unit) {
        viewModelScope.launch {
            try {
                val record = entity.value
                val storedIdentity = sourceIdentityRepository.getByKey(sourceKey)
                if (record == null || storedIdentity == null) {
                    onRejected()
                    return@launch
                }
                if (
                    storedIdentity.resolution != SourceResolution.OFFICIAL ||
                    storedIdentity.sourceId != record.sourceIdentity.sourceId
                ) {
                    onRejected()
                    return@launch
                }
                val source = extensionLoader.getSource(storedIdentity.sourceId)
                val runtimeIdentity = source?.sourceIdentity
                if (source == null || runtimeIdentity == null) {
                    onRejected()
                    return@launch
                }
                val runtimeKey = CanonicalSourceIdentities.fromRuntimeIdentity(runtimeIdentity).sourceKey
                if (runtimeKey != sourceKey) {
                    onRejected()
                    return@launch
                }
                source.getWebUrl(record.toThreadSummary())?.let(onReady) ?: onRejected()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                onRejected()
            }
        }
    }
}
