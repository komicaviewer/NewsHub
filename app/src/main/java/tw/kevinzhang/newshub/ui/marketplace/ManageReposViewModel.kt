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
import tw.kevinzhang.newshub.repo.RepoRepository
import javax.inject.Inject

sealed class AddRepoValidationState {
    object Idle : AddRepoValidationState()
    object Validating : AddRepoValidationState()
    data class Error(val message: String) : AddRepoValidationState()
    object Success : AddRepoValidationState()
}

@HiltViewModel
class ManageReposViewModel @Inject constructor(
    private val repoRepository: RepoRepository,
    private val marketplaceRepository: MarketplaceRepository,
) : ViewModel() {

    val repoUrls = repoRepository.getRepoUrls()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _addRepoUrl = MutableStateFlow("")
    val addRepoUrl = _addRepoUrl.asStateFlow()

    private val _validationState = MutableStateFlow<AddRepoValidationState>(AddRepoValidationState.Idle)
    val validationState = _validationState.asStateFlow()

    private var validationJob: Job? = null

    fun onAddRepoUrlChanged(url: String) {
        _addRepoUrl.value = url
        _validationState.value = AddRepoValidationState.Idle
        validationJob?.cancel()
        if (url.isBlank()) return
        validationJob = viewModelScope.launch {
            delay(600)
            validateUrl(url.trim().trimEnd('/'))
        }
    }

    private suspend fun validateUrl(url: String) {
        val githubPattern = Regex("^https://github\\.com/[^/]+/[^/]+$")
        if (!githubPattern.matches(url)) {
            _validationState.value = AddRepoValidationState.Error(
                "請輸入有效的 GitHub repo 網址（例：https://github.com/owner/repo）"
            )
            return
        }
        _validationState.value = AddRepoValidationState.Validating
        try {
            marketplaceRepository.fetchRepoMetadata(url)
            marketplaceRepository.fetchExtensions(url)
            _validationState.value = AddRepoValidationState.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _validationState.value = AddRepoValidationState.Error("無法驗證此 repo：${e.message}")
        }
    }

    fun addRepo(onSuccess: () -> Unit) {
        if (_validationState.value !is AddRepoValidationState.Success) return
        val url = _addRepoUrl.value.trim().trimEnd('/')
        viewModelScope.launch {
            try {
                repoRepository.addRepoUrl(url)
                _addRepoUrl.value = ""
                _validationState.value = AddRepoValidationState.Idle
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _validationState.value = AddRepoValidationState.Error("新增失敗：${e.message}")
            }
        }
    }

    fun removeRepo(url: String) {
        viewModelScope.launch { repoRepository.removeRepoUrl(url) }
    }
}
