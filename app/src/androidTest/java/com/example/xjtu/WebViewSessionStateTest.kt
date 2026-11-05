package com.example.xjtu

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WebViewSessionStateTest {
    @Test
    fun savesAndRestoresCurrentPageAndBackStack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val initialPageFinished = CountDownLatch(1)
        val secondPageFinished = CountDownLatch(1)
        val restoredPageFinished = CountDownLatch(1)
        val originalWaiters = ConcurrentHashMap<String, CountDownLatch>().apply {
            put("/first", initialPageFinished)
            put("/second", secondPageFinished)
        }
        val restoredWaiters = ConcurrentHashMap<String, CountDownLatch>().apply {
            put("/second", restoredPageFinished)
        }
        var originalView: WebView? = null
        var restoredView: WebView? = null

        try {
            instrumentation.runOnMainSync {
                originalView = testWebView(context, originalWaiters)
                originalView?.loadUrl("https://stei.top/first")
            }
            assertTrue(initialPageFinished.await(10, TimeUnit.SECONDS))

            instrumentation.runOnMainSync {
                originalView?.loadUrl("https://stei.top/second")
            }
            assertTrue(secondPageFinished.await(10, TimeUnit.SECONDS))

            val activityState = Bundle()
            instrumentation.runOnMainSync { saveWebViewState(originalView, activityState) }
            assertNotNull(activityState.getBundle(WEB_VIEW_STATE_KEY))

            instrumentation.runOnMainSync {
                restoredView = testWebView(context, restoredWaiters)
                assertTrue(restoreWebViewState(requireNotNull(restoredView), activityState.getBundle(WEB_VIEW_STATE_KEY)))
            }
            assertTrue(restoredPageFinished.await(10, TimeUnit.SECONDS))

            instrumentation.runOnMainSync {
                assertEquals("https://stei.top/second", requireNotNull(restoredView).url)
                assertTrue(requireNotNull(restoredView).canGoBack())
            }
        } finally {
            instrumentation.runOnMainSync {
                originalView?.let(::dispose)
                restoredView?.let(::dispose)
            }
        }
    }

    private fun testWebView(context: Context, finishedPages: Map<String, CountDownLatch>): WebView =
        WebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (request.url.host != "stei.top") return null
                    val path = request.url.path ?: return null
                    val html = "<html><body><main>$path</main></body></html>".toByteArray(Charsets.UTF_8)
                    return WebResourceResponse(
                        "text/html",
                        "UTF-8",
                        200,
                        "OK",
                        emptyMap(),
                        ByteArrayInputStream(html)
                    )
                }

                override fun onPageFinished(view: WebView, url: String) {
                    finishedPages[Uri.parse(url).path]?.countDown()
                }
            }
        }

    private fun dispose(webView: WebView) {
        webView.stopLoading()
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.removeAllViews()
        webView.destroy()
    }
}
