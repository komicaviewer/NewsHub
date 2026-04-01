package tw.kevinzhang.newshub.auth

import tw.kevinzhang.extension_api.AuthResult
import tw.kevinzhang.extension_api.SourceContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSourceContext @Inject constructor(
    private val authRepository: AuthRepository,
) : SourceContext {
    override suspend fun requestWebViewAuth(loginUrl: String, onPageLoadJs: String?): AuthResult =
        authRepository.requestAuth(loginUrl, onPageLoadJs)
}
