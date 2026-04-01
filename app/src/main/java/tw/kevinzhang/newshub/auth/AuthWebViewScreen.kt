package tw.kevinzhang.newshub.auth

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import tw.kevinzhang.extension_api.AuthResult

private const val TAG = "AuthWebViewScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthWebViewScreen(
    loginUrl: String,
    cookieJar: AppCookieJar,
    onPageLoadJs: String? = null,
    onDismiss: (AuthResult) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var jsExecuted by remember { mutableStateOf(false) }

    val loginHost = remember(loginUrl) {
        runCatching { loginUrl.toHttpUrl().host }.getOrElse { loginUrl }
    }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack()
        else onDismiss(AuthResult.Cancelled)
    }

    ModalBottomSheet(
        onDismissRequest = { onDismiss(AuthResult.Cancelled) },
        sheetState = sheetState,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Login",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        syncWebViewCookiesToOkHttp(loginUrl, cookieJar)
                        onDismiss(AuthResult.Success)
                    },
                ) {
                    Text("Done")
                }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                if (onPageLoadJs != null) {
                                    if (!jsExecuted) {
                                        jsExecuted = true
                                        view.evaluateJavascript(onPageLoadJs, null)
                                    }
                                } else {
                                    val currentHost = runCatching { url.toHttpUrl().host }.getOrNull()
                                    if (currentHost != null && currentHost != loginHost) {
                                        // Sync cookies from both the login domain and the redirect destination
                                        syncWebViewCookiesToOkHttp(loginUrl, cookieJar)
                                        syncWebViewCookiesToOkHttp(url, cookieJar)
                                        onDismiss(AuthResult.Success)
                                    }
                                }
                            }
                        }
                        loadUrl(loginUrl)
                        webView = this
                    }
                },
            )
        }
    }
}

private fun syncWebViewCookiesToOkHttp(pageUrl: String, cookieJar: AppCookieJar) {
    val rawCookies = CookieManager.getInstance().getCookie(pageUrl)
    if (rawCookies.isNullOrBlank()) {
        Log.d(TAG, "syncWebViewCookiesToOkHttp: no cookies for $pageUrl")
        return
    }
    val httpUrl = runCatching { pageUrl.toHttpUrl() }.getOrNull() ?: return
    val cookies = rawCookies.split(";")
        .mapNotNull { raw -> Cookie.parse(httpUrl, raw.trim()) }
    if (cookies.isNotEmpty()) {
        Log.d(TAG, "syncWebViewCookiesToOkHttp: syncing ${cookies.size} cookies from $pageUrl")
        cookieJar.addCookies(httpUrl, cookies)
    }
}
