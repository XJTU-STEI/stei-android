package com.example.xjtu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebNavigationPolicyTest {
    @Test
    fun appStartsAtTheSecureSiteRoot() {
        assertEquals("https://stei.top/", APP_START_URL)
    }

    @Test
    fun internalHttpsNavigationStaysInTheWebView() {
        assertEquals(
            WebNavigationDecision.KeepInWebView,
            decideWebNavigation("https://stei.top/thread/12")
        )
    }

    @Test
    fun internalHttpNavigationIsUpgradedWithoutDroppingThePath() {
        assertEquals(
            WebNavigationDecision.LoadInWebView("https://stei.top/forum?view=1#latest"),
            decideWebNavigation("http://stei.top/forum?view=1#latest")
        )
    }

    @Test
    fun externalHttpsNavigationOpensOutsideTheApp() {
        val url = "https://example.org/article"
        assertEquals(WebNavigationDecision.OpenExternally(url), decideWebNavigation(url))
    }

    @Test
    fun externalHttpOpensInTheSystemBrowserAndUnsupportedSchemesAreBlocked() {
        assertEquals(
            WebNavigationDecision.OpenExternally("http://example.org/article"),
            decideWebNavigation("http://example.org/article")
        )
        assertEquals(
            WebNavigationDecision.Blocked("暂不支持打开此链接"),
            decideWebNavigation("javascript:alert(1)")
        )
    }

    @Test
    fun onlyTheAuthorizedHostIsKeptInsideTheApp() {
        assertEquals(
            WebNavigationDecision.OpenExternally("https://stei.top.example.org/"),
            decideWebNavigation("https://stei.top.example.org/")
        )
        assertEquals(
            WebNavigationDecision.OpenExternally("https://www.stei.top/"),
            decideWebNavigation("https://www.stei.top/")
        )
        assertEquals(
            WebNavigationDecision.OpenExternally("https://forum.stei.top/"),
            decideWebNavigation("https://forum.stei.top/")
        )
    }

    @Test
    fun startupLoadsOnlyOnceAndRestorationDoesNotLoadTheHomePage() {
        val newSession = WebViewStartupPolicy()
        assertEquals(APP_START_URL, newSession.initialUrl(restoreSucceeded = false))
        assertNull(newSession.initialUrl(restoreSucceeded = false))

        val restoredSession = WebViewStartupPolicy()
        assertNull(restoredSession.initialUrl(restoreSucceeded = true))
        assertNull(restoredSession.initialUrl(restoreSucceeded = false))
    }
}
