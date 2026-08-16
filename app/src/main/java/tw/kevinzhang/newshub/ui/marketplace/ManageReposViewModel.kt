package tw.kevinzhang.newshub.ui.marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kevinzhang.marketplace.MarketplaceRepository
import tw.kevinzhang.marketplace.RepositoryDomainState
import tw.kevinzhang.marketplace.RepositoryRootPreview
import tw.kevinzhang.marketplace.RepositoryTrustDomain
import tw.kevinzhang.newshub.repo.RepoRepository
import javax.inject.Inject

sealed class AddRepoValidationState {
    data object Idle : AddRepoValidationState()
    data object InspectingRoot : AddRepoValidationState()
    data class AwaitingTrustConfirmation(val preview: RepositoryRootPreview) : AddRepoValidationState()
    data class Confirming(val preview: RepositoryRootPreview) : AddRepoValidationState()
    data class Error(val message: String) : AddRepoValidationState()
    data class Success(val domain: RepositoryTrustDomain) : AddRepoValidationState()
}

@HiltViewModel
class ManageReposViewModel @Inject constructor(
    private val repoRepository: RepoRepository,
    private val marketplaceRepository: MarketplaceRepository,
) : ViewModel() {
    val repositoryDomains = repoRepository.getRepositoryDomains()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _addRepoUrl = MutableStateFlow("")
    val addRepoUrl = _addRepoUrl.asStateFlow()

    private val _validationState = MutableStateFlow<AddRepoValidationState>(AddRepoValidationState.Idle)
    val validationState = _validationState.asStateFlow()
    private var inspectionJob: Job? = null

    fun onAddRepoUrlChanged(url: String) {
        cancelCurrentPreview()
        inspectionJob?.cancel()
        _addRepoUrl.value = url
        _validationState.value = AddRepoValidationState.Idle
        if (url.isBlank()) return
        inspectionJob = viewModelScope.launch {
            delay(600)
            inspectRoot(url)
        }
    }

    fun inspectNow() {
        inspectionJob?.cancel()
        val url = _addRepoUrl.value
        if (url.isBlank()) return
        inspectionJob = viewModelScope.launch { inspectRoot(url) }
    }

    fun confirmTrust(onSuccess: () -> Unit = {}) {
        val state = _validationState.value as? AddRepoValidationState.AwaitingTrustConfirmation ?: return
        _validationState.value = AddRepoValidationState.Confirming(state.preview)
        viewModelScope.launch {
            try {
                val domain = marketplaceRepository.confirmRepositoryRoot(state.preview.confirmationToken)
                repoRepository.addRepositoryDomain(domain)
                _addRepoUrl.value = ""
                _validationState.value = AddRepoValidationState.Success(domain)
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _validationState.value = AddRepoValidationState.Error(
                    "無法加入來源：${error.message ?: "安全驗證失敗"}",
                )
            }
        }
    }

    fun cancelTrustConfirmation() {
        cancelCurrentPreview()
        _validationState.value = AddRepoValidationState.Idle
    }

    fun suspendRepository(domainId: String) = changeState(domainId, RepositoryDomainState.SUSPENDED)
    fun resumeRepository(domainId: String) = changeState(domainId, RepositoryDomainState.ACTIVE)
    fun revokeRepository(domainId: String) = changeState(domainId, RepositoryDomainState.REVOKED)

    private suspend fun inspectRoot(url: String) {
        _validationState.value = AddRepoValidationState.InspectingRoot
        try {
            val preview = marketplaceRepository.inspectRepositoryRoot(url)
            _validationState.value = AddRepoValidationState.AwaitingTrustConfirmation(preview)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _validationState.value = AddRepoValidationState.Error(
                "無法檢查來源：${error.message ?: "安全驗證失敗"}",
            )
        }
    }

    private fun changeState(domainId: String, state: RepositoryDomainState) {
        viewModelScope.launch {
            runCatching { repoRepository.setRepositoryDomainState(domainId, state) }
                .onFailure { error ->
                    _validationState.value = AddRepoValidationState.Error(
                        "無法更新來源：${error.message ?: "未知錯誤"}",
                    )
                }
        }
    }

    private fun cancelCurrentPreview() {
        val preview = when (val current = _validationState.value) {
            is AddRepoValidationState.AwaitingTrustConfirmation -> current.preview
            is AddRepoValidationState.Confirming -> current.preview
            else -> null
        }
        preview?.let { marketplaceRepository.cancelRepositoryRootInspection(it.confirmationToken) }
    }

    override fun onCleared() {
        cancelCurrentPreview()
        super.onCleared()
    }
}
