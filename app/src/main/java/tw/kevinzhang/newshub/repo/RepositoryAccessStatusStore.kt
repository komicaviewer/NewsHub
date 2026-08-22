package tw.kevinzhang.newshub.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Process-local access health. Credentials and failure details are never persisted here. */
@Singleton
class RepositoryAccessStatusStore @Inject constructor() {
    private val _requiredDomainIds = MutableStateFlow<Set<String>>(emptySet())
    val requiredDomainIds: StateFlow<Set<String>> = _requiredDomainIds.asStateFlow()

    fun requireAccess(domainId: String) {
        _requiredDomainIds.update { it + domainId }
    }

    fun clear(domainId: String) {
        _requiredDomainIds.update { it - domainId }
    }
}
