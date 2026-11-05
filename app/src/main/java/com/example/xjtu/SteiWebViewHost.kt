package com.example.xjtu

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

@Composable
internal fun SteiWebViewHost(
    modifier: Modifier = Modifier,
    restoreState: Bundle?,
    onWebViewCreated: (WebView) -> Unit,
    onShowFileChooser: (android.webkit.ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onMainFrameError: () -> Unit,
    onProgressChanged: (Int) -> Unit,
    onExternalNavigation: (String) -> Unit,
    onNavigationBlocked: (String) -> Unit
) {
    val startupPolicy = remember { WebViewStartupPolicy() }
    val currentOnWebViewCreated = rememberUpdatedState(onWebViewCreated)
    val currentOnShowFileChooser = rememberUpdatedState(onShowFileChooser)
    val currentOnPageStarted = rememberUpdatedState(onPageStarted)
    val currentOnPageFinished = rememberUpdatedState(onPageFinished)
    val currentOnMainFrameError = rememberUpdatedState(onMainFrameError)
    val currentOnProgressChanged = rememberUpdatedState(onProgressChanged)
    val currentOnExternalNavigation = rememberUpdatedState(onExternalNavigation)
    val currentOnNavigationBlocked = rememberUpdatedState(onNavigationBlocked)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val refreshLayout = SwipeRefreshLayout(context).apply {
                setColorSchemeResources(android.R.color.holo_blue_dark)
            }
            val webView = WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
                CookieManager.getInstance().setAcceptCookie(true)
            }

            refreshLayout.addView(
                webView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            refreshLayout.setOnChildScrollUpCallback { _, _ -> webView.canScrollVertically(-1) }
            refreshLayout.setOnRefreshListener {
                if (webView.url.isNullOrBlank()) webView.loadUrl(APP_START_URL) else webView.reload()
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    refreshLayout.isRefreshing = false
                    currentOnPageStarted.value()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    refreshLayout.isRefreshing = false
                    currentOnPageFinished.value()
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    return handleNavigation(view, request.url.toString())
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        refreshLayout.isRefreshing = false
                        currentOnMainFrameError.value()
                    }
                }

                override fun onReceivedSslError(
                    view: WebView,
                    handler: SslErrorHandler,
                    error: android.net.http.SslError
                ) {
                    handler.cancel()
                    if (error.url == view.url) {
                        refreshLayout.isRefreshing = false
                        currentOnMainFrameError.value()
                    }
                }

                private fun handleNavigation(view: WebView, rawUrl: String): Boolean {
                    return when (val decision = decideWebNavigation(rawUrl)) {
                        WebNavigationDecision.KeepInWebView -> false
                        is WebNavigationDecision.LoadInWebView -> {
                            view.loadUrl(decision.url)
                            true
                        }
                        is WebNavigationDecision.OpenExternally -> {
                            currentOnExternalNavigation.value(decision.url)
                            true
                        }
                        is WebNavigationDecision.Blocked -> {
                            currentOnNavigationBlocked.value(decision.message)
                            true
                        }
                    }
                }
            }

            webView.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    currentOnProgressChanged.value(newProgress)
                }

                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: android.webkit.ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams
                ): Boolean = currentOnShowFileChooser.value(filePathCallback, fileChooserParams)
            }

            val restored = restoreWebViewState(webView, restoreState)
            currentOnWebViewCreated.value(webView)
            startupPolicy.initialUrl(restored)?.let(webView::loadUrl)
            refreshLayout
        },
        update = { /* Navigation is issued only by startup, WebView callbacks, or explicit actions. */ }
    )
}
