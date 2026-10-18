package tw.kevinzhang.newshub.ui.marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tw.kevinzhang.newshub.github.*
import javax.inject.Inject

@HiltViewModel
class GitHubRepositoryPickerViewModel @Inject constructor(
    val session: GitHubAccountSession, catalog: GitHubRepositoryCatalog,
) : ViewModel() {
    private val directory = GitHubRepositorySearch(viewModelScope, session.state, session::currentCredential, catalog::listRepositories)
    val search = directory.state
    private var loginJob: Job? = null
    fun login() { loginJob?.cancel(); loginJob = viewModelScope.launch { session.login() } }
    fun cancel() { session.cancelLogin(); loginJob?.cancel() }
    fun logout() { session.logout() }
    fun query(value: String) = directory.query(value)
    fun loadMore() = directory.loadMore()
    override fun onCleared() { super.onCleared(); if (session.state.value.busy) session.cancelLogin() }
}
