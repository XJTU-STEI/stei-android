package com.example.xjtu

import java.net.URI
import java.util.Locale

internal const val APP_START_URL = "https://stei.top/"

internal sealed interface WebNavigationDecision {
    data object KeepInWebView : WebNavigationDecision
    data class LoadInWebView(val url: String) : WebNavigationDecision
    data class OpenExternally(val url: String) : WebNavigationDecision
    data class Blocked(val message: String) : WebNavigationDecision
}

internal fun decideWebNavigation(rawUrl: String): WebNavigationDecision {
    val uri = runCatching { URI(rawUrl) }.getOrNull()
        ?: return WebNavigationDecision.Blocked("无法打开此链接")
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
        ?: return WebNavigationDecision.Blocked("无法打开此链接")
    val host = uri.host?.lowercase(Locale.ROOT)

    return when (scheme) {
        "https" -> {
            if (isSteiHost(host)) {
                WebNavigationDecision.KeepInWebView
            } else {
                WebNavigationDecision.OpenExternally(rawUrl)
            }
        }

        "http" -> {
            if (isSteiHost(host)) {
                WebNavigationDecision.LoadInWebView(rawUrl.replaceFirst(HTTP_SCHEME, "https:"))
            } else {
                WebNavigationDecision.OpenExternally(rawUrl)
            }
        }

        "mailto", "tel", "sms" -> WebNavigationDecision.OpenExternally(rawUrl)
        else -> WebNavigationDecision.Blocked("暂不支持打开此链接")
    }
}

internal class WebViewStartupPolicy(
    private val startUrl: String = APP_START_URL
) {
    private var initialNavigationHandled = false

    /** Returns a URL only for the first view creation, and only when restoration did not succeed. */
    fun initialUrl(restoreSucceeded: Boolean): String? {
        if (initialNavigationHandled) return null
        initialNavigationHandled = true
        return if (restoreSucceeded) null else startUrl
    }
}

private val HTTP_SCHEME = Regex("(?i)^http:")

private fun isSteiHost(host: String?): Boolean =
    host == "stei.top"
