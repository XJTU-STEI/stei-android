package com.example.xjtu

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.xjtu.ui.theme.XjtuTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var webView: android.webkit.WebView? = null
    private var savedWebViewState: Bundle? = null
    private var pendingFilePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(OpenWebFilesContract()) { uris ->
        completeFileChooser(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedWebViewState = savedInstanceState?.getBundle(WEB_VIEW_STATE_KEY)?.let(::Bundle)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentWebView = webView
                if (currentWebView?.canGoBack() == true) {
                    currentWebView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setContent {
            XjtuTheme {
                val darkTheme = isSystemInDarkTheme()
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebViewScreen(
                        restoreState = savedWebViewState,
                        onWebViewCreated = { view ->
                            webView = view
                            savedWebViewState = null
                        },
                        onShowFileChooser = ::showFileChooser,
                        onLaunchExternal = ::launchExternalUrl,
                        onRetry = ::retryCurrentPage
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        CookieManager.getInstance().flush()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        saveWebViewState(webView, outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        completeFileChooser(null)
        webView?.let { view ->
            view.stopLoading()
            (view.parent as? ViewGroup)?.removeView(view)
            view.setWebChromeClient(android.webkit.WebChromeClient())
            view.setWebViewClient(android.webkit.WebViewClient())
            view.removeAllViews()
            view.destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun retryCurrentPage() {
        val currentWebView = webView ?: return
        if (currentWebView.url.isNullOrBlank()) {
            currentWebView.loadUrl(APP_START_URL)
        } else {
            currentWebView.reload()
        }
    }

    private fun showFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: android.webkit.WebChromeClient.FileChooserParams
    ): Boolean {
        completeFileChooser(null)
        pendingFilePathCallback = callback
        return try {
            filePickerLauncher.launch(
                WebFilePickerRequest(
                    acceptTypes = params.acceptTypes.orEmpty().toList(),
                    allowMultiple = params.mode == android.webkit.WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
                )
            )
            true
        } catch (_: ActivityNotFoundException) {
            completeFileChooser(null)
            true
        } catch (_: IllegalStateException) {
            completeFileChooser(null)
            true
        }
    }

    private fun completeFileChooser(uris: Array<Uri>?) {
        val callback = pendingFilePathCallback ?: return
        pendingFilePathCallback = null
        callback.onReceiveValue(uris)
    }

    private fun launchExternalUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

@Composable
private fun WebViewScreen(
    restoreState: Bundle?,
    onWebViewCreated: (android.webkit.WebView) -> Unit,
    onShowFileChooser: (ValueCallback<Array<Uri>>, android.webkit.WebChromeClient.FileChooserParams) -> Boolean,
    onLaunchExternal: (String) -> Boolean,
    onRetry: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var pageLoadFailed by remember { mutableStateOf(false) }

    fun showMessage(message: String) {
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun retry() {
        pageLoadFailed = false
        isLoading = true
        loadProgress = 0
        onRetry()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
    ) {
        SteiWebViewHost(
            modifier = Modifier.fillMaxSize(),
            restoreState = restoreState,
            onWebViewCreated = onWebViewCreated,
            onShowFileChooser = onShowFileChooser,
            onPageStarted = {
                isLoading = true
                loadProgress = 0
                pageLoadFailed = false
            },
            onPageFinished = {
                isLoading = false
                loadProgress = 100
            },
            onMainFrameError = {
                isLoading = false
                pageLoadFailed = true
            },
            onProgressChanged = { progress -> loadProgress = progress.coerceIn(0, 100) },
            onExternalNavigation = { url ->
                if (!onLaunchExternal(url)) showMessage("无法打开此链接")
            },
            onNavigationBlocked = ::showMessage
        )

        if (isLoading && !pageLoadFailed) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }

        if (pageLoadFailed) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "网页暂时无法打开",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "请检查网络后重试",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = ::retry, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("重试")
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
