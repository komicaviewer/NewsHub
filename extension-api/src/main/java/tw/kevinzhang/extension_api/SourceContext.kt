package tw.kevinzhang.extension_api

interface SourceContext {
    /**
     * Asks the host app to open a WebView so the user can complete login or reCAPTCHA.
     * Suspends until the user taps Done or cancels.
     *
     * @param loginUrl  The URL to load in the WebView.
     * @param onPageLoadJs  Optional JavaScript to execute after the page finishes loading
     *                      (e.g. `"User.Login.requireLoginIframe();"` to open a login modal).
     */
    suspend fun requestWebViewAuth(
        loginUrl: String,
        onPageLoadJs: String? = null,
    ): AuthResult
}

sealed class AuthResult {
    object Success : AuthResult()
    object Cancelled : AuthResult()
}
