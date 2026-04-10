package tw.kevinzhang.newshub.ui.marketplace

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kevinzhang.marketplace.MarketplaceRepository
import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import tw.kevinzhang.newshub.repo.RepoRepository
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MarketplaceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MarketplaceRepository,
    private val repoRepository: RepoRepository,
) : ViewModel() {

    val repoUrls = repoRepository.getRepoUrls()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _extensions = MutableStateFlow<List<Pair<ExtensionInfo, InstallState>>>(emptyList())
    val extensions = _extensions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        // Auto-refresh when repo list changes
        viewModelScope.launch {
            repoUrls.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _extensions.value = emptyList()
            try {
                val urls = repoUrls.value
                if (urls.isEmpty()) return@launch
                val all = urls.flatMap { url ->
                    try {
                        repository.fetchExtensions(url)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _error.value = "Failed to load $url: ${e.message}"
                        emptyList()
                    }
                }
                _extensions.value = all.map { it to repository.getInstallState(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Failed to load marketplace: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addRepo(url: String) {
        viewModelScope.launch {
            try {
                // Validate by fetching metadata
                repository.fetchRepoMetadata(url)
                repoRepository.addRepoUrl(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Invalid repo URL: ${e.message}"
            }
        }
    }

    fun removeRepo(url: String) {
        viewModelScope.launch {
            repoRepository.removeRepoUrl(url)
        }
    }

    fun install(info: ExtensionInfo) {
        viewModelScope.launch {
            try {
                context.cacheDir.listFiles { f -> f.extension == "apk" }?.forEach { it.delete() }
                val apkFile = repository.downloadApk(info.apkUrl, info.sha256)
                installExtension(apkFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Failed to install ${info.name}: ${e.message}"
            }
        }
    }

    fun clearError() { _error.value = null }

    // Keep backward compat name
    fun clearInstallError() = clearError()

    private fun installExtension(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
