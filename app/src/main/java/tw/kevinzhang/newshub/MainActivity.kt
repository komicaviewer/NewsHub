package tw.kevinzhang.newshub

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.kevinzhang.extension_api.AuthenticatedSource
import tw.kevinzhang.extension_loader.ExtensionLoader
import tw.kevinzhang.newshub.auth.SourceSessionManager
import tw.kevinzhang.newshub.auth.oauth.OAuthCompletion
import tw.kevinzhang.newshub.auth.oauth.OAuthCoordinator
import tw.kevinzhang.newshub.ui.bindAppScreen
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var oauthCoordinator: OAuthCoordinator
    @Inject lateinit var sourceSessionManager: SourceSessionManager
    @Inject lateinit var extensionLoader: ExtensionLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            bindAppScreen(navController = navController)
        }
        handleOAuthCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthCallback(intent)
    }

    private fun handleOAuthCallback(callbackIntent: Intent) {
        if (callbackIntent.action != Intent.ACTION_VIEW) return
        val uri = callbackIntent.data ?: return
        if (!uri.scheme.equals("tw.kevinzhang.newshub.oauth", ignoreCase = true) ||
            !uri.host.equals("callback", ignoreCase = true)
        ) return
        // Do not leave a reusable authorization code in the Activity's retained Intent.
        callbackIntent.data = null
        lifecycleScope.launch {
            when (val result = oauthCoordinator.handleRedirect(uri)) {
                is OAuthCompletion.Success -> {
                    val source = extensionLoader.getSource(result.sourceId) as? AuthenticatedSource
                    val sessionIsValid = runCatching {
                        withContext(Dispatchers.IO) { source?.validateSession() == true }
                    }.getOrDefault(false)
                    if (sessionIsValid) {
                        sourceSessionManager.markSignedIn(result.sourceId)
                    } else {
                        oauthCoordinator.logout(result.identity)
                        sourceSessionManager.markExpired(result.sourceId)
                        Toast.makeText(
                            this@MainActivity,
                            "OAuth 已授權，但來源驗證失敗，請重新登入。",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                is OAuthCompletion.Failure -> {
                    result.sourceId?.let(sourceSessionManager::markExpired)
                    Toast.makeText(this@MainActivity, result.userMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
