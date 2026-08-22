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
import tw.kevinzhang.marketplace.RepositoryAccessCredential
import tw.kevinzhang.marketplace.RepositoryAccessDescriptor
import tw.kevinzhang.marketplace.RepositoryAccessDraft
import tw.kevinzhang.marketplace.RepositoryAccessFailureReason
import tw.kevinzhang.marketplace.RepositoryAccessKind
import tw.kevinzhang.marketplace.RepositoryAccessRequiredException
import tw.kevinzhang.marketplace.RepositoryCredentialStore
import tw.kevinzhang.marketplace.RepositoryDomainState
import tw.kevinzhang.marketplace.RepositoryRootPreview
import tw.kevinzhang.marketplace.RepositoryTrustDomain
import tw.kevinzhang.newshub.repo.RepoRepository
import tw.kevinzhang.newshub.repo.RepositoryAccessStatusStore
import javax.inject.Inject

sealed class AddRepoValidationState {
    data object Idle : AddRepoValidationState()
    data object InspectingRoot : AddRepoValidationState()
    data class AwaitingTrustConfirmation(val preview: RepositoryRootPreview) : AddRepoValidationState()
    data class Confirming(val preview: RepositoryRootPreview) : AddRepoValidationState()
    data class Error(val message: String) : AddRepoValidationState()
    data class AccessRequired(val message: String) : AddRepoValidationState()
    data class Success(val domain: RepositoryTrustDomain) : AddRepoValidationState()
}

sealed class RepositoryReauthorizationState {
    data object Idle : RepositoryReauthorizationState()
    data class Editing(
        val domainId: String,
        val token: String = "",
        val errorMessage: String? = null,
    ) : RepositoryReauthorizationState()
    data class Verifying(val domainId: String) : RepositoryReauthorizationState()
}

@HiltViewModel
class ManageReposViewModel @Inject constructor(
    private val repoRepository: RepoRepository,
    private val marketplaceRepository: MarketplaceRepository,
    private val credentialStore: RepositoryCredentialStore,
    private val accessStatusStore: RepositoryAccessStatusStore,
) : ViewModel() {
    val repositoryDomains = repoRepository.getRepositoryDomains()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _addRepoUrl = MutableStateFlow("")
    val addRepoUrl = _addRepoUrl.asStateFlow()

    private val _accessKind = MutableStateFlow(RepositoryAccessKind.PUBLIC_HTTPS)
    val accessKind = _accessKind.asStateFlow()

    private val _githubToken = MutableStateFlow("")
    val githubToken = _githubToken.asStateFlow()

    val accessRequiredDomainIds = accessStatusStore.requiredDomainIds

    private val _reauthorizationState =
        MutableStateFlow<RepositoryReauthorizationState>(RepositoryReauthorizationState.Idle)
    val reauthorizationState = _reauthorizationState.asStateFlow()

    private val _validationState = MutableStateFlow<AddRepoValidationState>(AddRepoValidationState.Idle)
    val validationState = _validationState.asStateFlow()
    private var inspectionJob: Job? = null

    fun onAddRepoUrlChanged(url: String) {
        cancelCurrentPreview()
        inspectionJob?.cancel()
        if (url != _addRepoUrl.value && _githubToken.value.isNotEmpty()) {
            _githubToken.value = ""
        }
        _addRepoUrl.value = url
        _validationState.value = AddRepoValidationState.Idle
        if (url.isBlank() || _accessKind.value == RepositoryAccessKind.GITHUB_CONTENTS) return
        inspectionJob = viewModelScope.launch {
            delay(600)
            inspectRoot(url)
        }
    }

    fun onAccessKindChanged(kind: RepositoryAccessKind) {
        if (kind == _accessKind.value) return
        cancelCurrentPreview()
        inspectionJob?.cancel()
        _accessKind.value = kind
        _githubToken.value = ""
        _validationState.value = AddRepoValidationState.Idle
    }

    fun onGithubTokenChanged(token: String) {
        cancelCurrentPreview()
        inspectionJob?.cancel()
        _githubToken.value = token
        _validationState.value = AddRepoValidationState.Idle
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
            var confirmedDomain: RepositoryTrustDomain? = null
            try {
                val domain = marketplaceRepository.confirmRepositoryRoot(state.preview.confirmationToken)
                confirmedDomain = domain
                repoRepository.addRepositoryDomain(domain)
            } catch (error: CancellationException) {
                confirmedDomain?.let { cleanUpFailedConfirmation(it) }
                throw error
            } catch (error: Exception) {
                confirmedDomain?.let { cleanUpFailedConfirmation(it) }
                _validationState.value = AddRepoValidationState.Error(
                    "無法加入來源，沒有保存存取憑證。請重新檢查後再試。",
                )
                return@launch
            }
            val domain = requireNotNull(confirmedDomain)
            clearAddDraft()
            _validationState.value = AddRepoValidationState.Success(domain)
            onSuccess()
        }
    }

    fun cancelTrustConfirmation() {
        cancelCurrentPreview()
        _githubToken.value = ""
        _validationState.value = AddRepoValidationState.Idle
    }

    fun suspendRepository(domainId: String) = changeState(domainId, RepositoryDomainState.SUSPENDED)
    fun resumeRepository(domainId: String) = changeState(domainId, RepositoryDomainState.ACTIVE)
    fun revokeRepository(domainId: String) = changeState(domainId, RepositoryDomainState.REVOKED)

    fun beginReauthorization(domainId: String) {
        _reauthorizationState.value = RepositoryReauthorizationState.Editing(domainId)
    }

    fun onReplacementTokenChanged(token: String) {
        val current = _reauthorizationState.value as? RepositoryReauthorizationState.Editing ?: return
        _reauthorizationState.value = current.copy(token = token, errorMessage = null)
    }

    fun cancelReauthorization() {
        _reauthorizationState.value = RepositoryReauthorizationState.Idle
    }

    fun confirmReauthorization() {
        val current = _reauthorizationState.value as? RepositoryReauthorizationState.Editing ?: return
        val credential = runCatching { RepositoryAccessCredential.githubToken(current.token) }
            .getOrElse {
                _reauthorizationState.value = current.copy(token = "", errorMessage = "請輸入有效的 access token。")
                return
            }
        _reauthorizationState.value = RepositoryReauthorizationState.Verifying(current.domainId)
        viewModelScope.launch {
            try {
                marketplaceRepository.verifyRepositoryAccess(current.domainId, credential)
                credentialStore.saveCredential(current.domainId, credential)
                accessStatusStore.clear(current.domainId)
                _reauthorizationState.value = RepositoryReauthorizationState.Idle
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Never put a rejected token back into UI state or interpolate it into errors.
                val message = if (error is RepositoryAccessRequiredException) {
                    when (error.reason) {
                        RepositoryAccessFailureReason.MISSING_CREDENTIAL ->
                            "請輸入 GitHub access token。"
                        RepositoryAccessFailureReason.CREDENTIAL_REJECTED ->
                            "GitHub 拒絕這個 token，請確認 Contents: read 權限。"
                        RepositoryAccessFailureReason.NOT_FOUND_OR_INACCESSIBLE ->
                            "找不到 repository 或無法存取，請檢查網址、revision 與 token。"
                    }
                } else {
                    "無法驗證新的 token，原有設定未變更。"
                }
                _reauthorizationState.value = RepositoryReauthorizationState.Editing(
                    domainId = current.domainId,
                    errorMessage = message,
                )
            }
        }
    }

    private suspend fun inspectRoot(url: String) {
        _validationState.value = AddRepoValidationState.InspectingRoot
        try {
            val access = when (_accessKind.value) {
                RepositoryAccessKind.PUBLIC_HTTPS -> RepositoryAccessDescriptor.publicHttps()
                RepositoryAccessKind.GITHUB_CONTENTS -> RepositoryAccessDescriptor.githubContents()
            }
            val credential = when (_accessKind.value) {
                RepositoryAccessKind.PUBLIC_HTTPS -> null
                RepositoryAccessKind.GITHUB_CONTENTS -> RepositoryAccessCredential.githubToken(
                    _githubToken.value,
                )
            }
            val preview = marketplaceRepository.inspectRepositoryRoot(
                RepositoryAccessDraft(repositoryUrl = url, access = access),
                credential,
            )
            _validationState.value = AddRepoValidationState.AwaitingTrustConfirmation(preview)
        } catch (error: CancellationException) {
            throw error
        } catch (error: RepositoryAccessRequiredException) {
            _githubToken.value = ""
            _validationState.value = AddRepoValidationState.AccessRequired(
                "GitHub 無法存取這個 repository。請重新輸入具備 Contents: read 權限的 token。",
            )
        } catch (error: Exception) {
            _githubToken.value = ""
            _validationState.value = AddRepoValidationState.Error(
                "無法檢查來源，請確認網址與安全 metadata 後再試。",
            )
        }
    }

    private fun changeState(domainId: String, state: RepositoryDomainState) {
        viewModelScope.launch {
            runCatching { repoRepository.setRepositoryDomainState(domainId, state) }
                .onSuccess {
                    if (state == RepositoryDomainState.REVOKED) accessStatusStore.clear(domainId)
                }
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

    private fun clearAddDraft() {
        _addRepoUrl.value = ""
        _githubToken.value = ""
        _accessKind.value = RepositoryAccessKind.PUBLIC_HTTPS
    }

    private suspend fun cleanUpFailedConfirmation(domain: RepositoryTrustDomain) {
        runCatching { credentialStore.deleteCredential(domain.id) }
        runCatching {
            marketplaceRepository.setRepositoryDomainState(
                domain.copy(state = RepositoryDomainState.REVOKED),
            )
        }
    }

    override fun onCleared() {
        cancelCurrentPreview()
        super.onCleared()
    }
}
