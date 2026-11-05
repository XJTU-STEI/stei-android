package com.example.xjtu

import android.os.Bundle
import android.webkit.WebView

internal const val WEB_VIEW_STATE_KEY = "com.example.xjtu.WEB_VIEW_STATE"

internal fun saveWebViewState(webView: WebView?, outState: Bundle) {
    val savedState = Bundle()
    if (webView?.saveState(savedState) != null) {
        outState.putBundle(WEB_VIEW_STATE_KEY, savedState)
    }
}

internal fun restoreWebViewState(webView: WebView, savedState: Bundle?): Boolean {
    savedState ?: return false
    return webView.restoreState(Bundle(savedState)) != null
}
