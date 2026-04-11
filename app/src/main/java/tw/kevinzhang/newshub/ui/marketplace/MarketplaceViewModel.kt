package tw.kevinzhang.newshub.ui.marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tw.kevinzhang.extension_loader.ExtensionManager
import tw.kevinzhang.marketplace.MarketplaceRepository
import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import tw.kevinzhang.marketplace.data.InstallStep
import tw.kevinzhang.marketplace.data.RepoMetadata
import tw.kevinzhang.newshub.repo.RepoRepository
import javax.inject.Inject

data class RepoGroup(
    val repoUrl: String,
    val metadata: RepoMetadata,
    val extensions: List<ExtensionInfo>,
)

@HiltViewModel
class MarketplaceViewModel @Inject constructor(
    private val marketplaceRepository: MarketplaceRepository,
    private val repoRepository: RepoRepository,
    private val extensionManager: ExtensionManager,
) : ViewModel() {

    val repoUrls = repoRepository.getRepoUrls()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _repoGroups = MutableStateFlow<List<RepoGroup>>(emptyList())
    val repoGroups = _repoGroups.asStateFlow()

    /** pkg → install step */
    private val _installSteps = MutableStateFlow<Map<String, InstallStep>>(emptyMap())
    val installSteps = _installSteps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        viewModelScope.launch {
            repoUrls.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _repoGroups.value = emptyList()
            try {
                val urls = repoUrls.value
                if (urls.isEmpty()) return@launch
                val groups = urls.mapNotNull { url ->
                    try {
                        val metadata = marketplaceRepository.fetchRepoMetadata(url)
                        val extensions = marketplaceRepository.fetchExtensions(url)
                        RepoGroup(repoUrl = url, metadata = metadata, extensions = extensions)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _error.value = "Failed to load $url: ${e.message}"
                        null
                    }
                }
                _repoGroups.value = groups
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Failed to load marketplace: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getInstallState(info: ExtensionInfo): InstallState =
        marketplaceRepository.getInstallState(info)

    fun install(info: ExtensionInfo) {
        viewModelScope.launch {
            setStep(info.id, InstallStep.DOWNLOADING)
            try {
                val apkFile = marketplaceRepository.downloadApk(info.apkUrl, info.sha256)
                setStep(info.id, InstallStep.INSTALLING)
                extensionManager.installExtension(apkFile)
                // Step resets to IDLE after system dialog; installer broadcast updates state
                setStep(info.id, InstallStep.IDLE)
            } catch (e: CancellationException) {
                setStep(info.id, InstallStep.IDLE)
                throw e
            } catch (e: Exception) {
                setStep(info.id, InstallStep.ERROR)
                _error.value = "Failed to install ${info.name}: ${e.message}"
            }
        }
    }

    fun uninstall(pkg: String) {
        extensionManager.uninstallExtension(pkg)
    }

    fun addRepo(url: String) {
        viewModelScope.launch {
            try {
                marketplaceRepository.fetchRepoMetadata(url)
                repoRepository.addRepoUrl(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Invalid repo URL: ${e.message}"
            }
        }
    }

    fun removeRepo(url: String) {
        viewModelScope.launch { repoRepository.removeRepoUrl(url) }
    }

    fun clearError() { _error.value = null }

    private fun setStep(pkg: String, step: InstallStep) {
        _installSteps.update { it + (pkg to step) }
    }
}
