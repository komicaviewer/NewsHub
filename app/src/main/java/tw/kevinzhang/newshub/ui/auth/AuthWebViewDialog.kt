package tw.kevinzhang.newshub.ui.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import tw.kevinzhang.extension_api.AuthSpec
import tw.kevinzhang.newshub.auth.WebLoginRequest

/** Host-owned login surface. There is no extension Activity convention or JS bridge. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthWebViewDialog(
    request: WebLoginRequest,
    onFinished: () -> Unit,
    onCancelled: () -> Unit,
) {
    val allowedHosts = request.spec.allowedHosts.map { it.lowercase() }.toSet()
    val loginUrlAllowed = request.spec.loginUrl.toUriOrNull()?.isAllowedHttpsHost(allowedHosts) == true
    AlertDialog(
        onDismissRequest = onCancelled,
        title = { Text("登入") },
        text = {
            if (!loginUrlAllowed) {
                Text("來源提供了不允許的登入網址。")
            } else {
                RestrictedLoginWebView(request, allowedHosts)
            }
        },
        confirmButton = { Button(onClick = onFinished) { Text("完成登入") } },
        dismissButton = { TextButton(onClick = onCancelled) { Text("取消") } },
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RestrictedLoginWebView(request: WebLoginRequest, allowedHosts: Set<String>) {
    val context = LocalContext.current
    val webView = remember(request) {
        WebView(context).apply {
            settings.javaScriptEnabled = request.spec.javaScriptEnabled
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, navigation: WebResourceRequest): Boolean =
                    !navigation.url.isAllowedHttpsHost(allowedHosts)

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    // A malicious redirect must not render even briefly.
                    if (url?.toUriOrNull()?.isAllowedHttpsHost(allowedHosts) != true) view.stopLoading()
                }
            }
            loadUrl(request.spec.loginUrl)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { webView })
}

private fun android.net.Uri.isAllowedHttpsHost(allowedHosts: Set<String>): Boolean =
    scheme.equals("https", ignoreCase = true) && host?.lowercase() in allowedHosts

private fun String.toUriOrNull(): android.net.Uri? = runCatching { android.net.Uri.parse(this) }.getOrNull()
