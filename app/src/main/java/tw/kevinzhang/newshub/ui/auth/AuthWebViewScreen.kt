package tw.kevinzhang.newshub.ui.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import tw.kevinzhang.extension_api.AuthSpec
import tw.kevinzhang.newshub.auth.WebLoginRequest

/** Host-owned login surface. There is no extension Activity convention or JS bridge. */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthWebViewScreen(
    request: WebLoginRequest,
    isVerifying: Boolean,
    errorMessage: String?,
    onFinishLogin: () -> Unit,
    onCancelLogin: () -> Unit,
) {
    val allowedHosts = request.spec.allowedHosts.map { it.lowercase() }.toSet()
    val loginUrlAllowed = request.spec.loginUrl.toUriOrNull()?.isAllowedHttpsHost(allowedHosts) == true
    var webView by remember(request.sourceId) { mutableStateOf<WebView?>(null) }

    BackHandler {
        if (!isVerifying) {
            val currentWebView = webView
            if (currentWebView?.canGoBack() == true) currentWebView.goBack()
            else onCancelLogin()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("網頁登入")
                        Text(
                            text = "允許網域：${allowedHosts.joinToString("、")}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onCancelLogin, enabled = !isVerifying) {
                        Text("取消")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onFinishLogin,
                        enabled = loginUrlAllowed && !isVerifying,
                    ) {
                        Text("完成登入")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            if (!loginUrlAllowed) {
                Text(
                    text = "來源提供了不允許的登入網址。請取消後重試，或確認來源設定。",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useConstrainedContent = maxWidth >= 840.dp
                    Surface(
                        modifier = if (useConstrainedContent) {
                            Modifier
                                .widthIn(max = 840.dp)
                                .fillMaxSize()
                                .align(Alignment.Center)
                        } else {
                            Modifier.fillMaxSize()
                        },
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        RestrictedLoginWebView(
                            request = request,
                            allowedHosts = allowedHosts,
                            onWebViewCreated = { webView = it },
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    tonalElevation = 2.dp,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("登入驗證失敗")
                        Text(errorMessage, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onFinishLogin, enabled = !isVerifying) {
                            Text("重新嘗試")
                        }
                    }
                }
            }

            if (isVerifying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.36f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(tonalElevation = 6.dp, shape = MaterialTheme.shapes.medium) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "正在驗證登入狀態…",
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RestrictedLoginWebView(
    request: WebLoginRequest,
    allowedHosts: Set<String>,
    onWebViewCreated: (WebView) -> Unit,
) {
    val context = LocalContext.current
    val bundleStateSaver = remember {
        Saver<MutableState<Bundle?>, Bundle>(
            save = { state -> state.value?.let { Bundle(it) } },
            restore = { bundle -> mutableStateOf(Bundle(bundle)) },
        )
    }
    var savedWebViewState by rememberSaveable(request.sourceId, saver = bundleStateSaver) {
        mutableStateOf<Bundle?>(null)
    }
    val webView = remember(request.sourceId) {
        WebView(context).apply {
            settings.javaScriptEnabled = request.spec.javaScriptEnabled
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(false)
            webViewClient = object : WebViewClient() {
                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean =
                    url?.toUriOrNull()?.isAllowedHttpsHost(allowedHosts) != true

                override fun shouldOverrideUrlLoading(view: WebView, navigation: WebResourceRequest): Boolean =
                    !navigation.url.isAllowedHttpsHost(allowedHosts)

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    // A malicious redirect must not render even briefly.
                    if (url?.toUriOrNull()?.isAllowedHttpsHost(allowedHosts) != true) view.stopLoading()
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    if (url?.toUriOrNull()?.isAllowedHttpsHost(allowedHosts) == true) {
                        savedWebViewState = Bundle().also(view::saveState)
                    }
                }
            }
            if (savedWebViewState?.let(::restoreState) == null) loadUrl(request.spec.loginUrl)
        }
    }
    DisposableEffect(webView) {
        onWebViewCreated(webView)
        onDispose {
            savedWebViewState = Bundle().also(webView::saveState)
            webView.stopLoading()
            webView.destroy()
        }
    }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { webView })
}

private fun android.net.Uri.isAllowedHttpsHost(allowedHosts: Set<String>): Boolean =
    scheme.equals("https", ignoreCase = true) && host?.lowercase() in allowedHosts

private fun String.toUriOrNull(): android.net.Uri? = runCatching { toUri() }.getOrNull()
