package com.tapweb.ui.webview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class CustomWebViewClient(
    private val context: Context,
    private val listener: WebViewListener
) : WebViewClient() {

    companion object {
        private const val WHITE_SCREEN_TIMEOUT_MS = 15_000L

        /**
         * Desktop Chrome UA — used on OAuth pages so providers show
         * web-based login (username/password) instead of trying to open native apps.
         */
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

        /**
         * OAuth / login domains that force desktop UA.
         * On these pages, native app schemes won't be launched —
         * the page shows web login form or QR code instead.
         */
        private val OAUTH_DOMAINS = setOf(
            // QQ
            "graph.qq.com",
            "xui.ptlogin2.qq.com",
            "ptlogin2.qq.com",
            "ssl.ptlogin2.qq.com",
            // WeChat
            "open.weixin.qq.com",
            // Weibo
            "passport.weibo.com",
            "api.weibo.com",
            // Baidu
            "passport.baidu.com",
            // Douyin
            "sso.douyin.com",
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var whiteScreenRunnable: Runnable? = null
    private var hasReceivedAnyContent = false
    private var usingDesktopUA = false
    private var originalUA: String? = null
    private var baseDomain: String? = null
    private var currentHost: String? = null

    /** Whether the WebView is currently on a known OAuth/login domain. */
    fun isOnOAuthPage(): Boolean = currentHost in OAUTH_DOMAINS

    interface WebViewListener {
        fun onPageStarted(url: String)
        fun onPageFinished(url: String)
        fun onProgressChanged(newProgress: Int)
        fun onError(errorCode: Int, description: String?, failingUrl: String)
        fun onExternalLink(url: String)
        fun onLaunchedExternalApp()
    }

    /**
     * Remember the user's original site domain.
     * Called once when the initial URL is loaded.
     */
    fun setBaseDomain(domain: String) {
        baseDomain = domain
    }

    fun startWhiteScreenTimer() {
        hasReceivedAnyContent = false
        cancelWhiteScreenTimer()
        whiteScreenRunnable = Runnable {
            if (!hasReceivedAnyContent) {
                listener.onError(-1, "Page loading timed out", "")
            }
        }
        handler.postDelayed(whiteScreenRunnable!!, WHITE_SCREEN_TIMEOUT_MS)
    }

    fun cancelWhiteScreenTimer() {
        whiteScreenRunnable?.let { handler.removeCallbacks(it) }
        whiteScreenRunnable = null
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase() ?: return false

        // On OAuth pages, block custom schemes to keep flow in WebView
        val host = request.url.host?.lowercase()
        val onOAuthPage = host != null && host in OAUTH_DOMAINS

        return when (scheme) {
            "http", "https" -> false

            "intent" -> {
                if (onOAuthPage) true // Block on OAuth pages
                else handleIntentScheme(url)
            }

            "mailto", "tel", "sms" -> {
                listener.onExternalLink(url)
                true
            }

            else -> {
                // On OAuth pages: block native app launch → page shows web login
                // On normal pages: launch native app (payment, etc.)
                if (onOAuthPage) true
                else launchNativeApp(url)
            }
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        hasReceivedAnyContent = true
        cancelWhiteScreenTimer()

        // UA switching: desktop for OAuth domains, mobile for everything else
        val host = extractHost(url)
        currentHost = host
        if (host in OAUTH_DOMAINS) {
            applyDesktopUA(view)
        } else if (usingDesktopUA) {
            restoreMobileUA(view)
        }

        listener.onPageStarted(url)
        listener.onProgressChanged(0)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        cancelWhiteScreenTimer()
        listener.onPageFinished(url)
        listener.onProgressChanged(100)
    }

    // --- UA Management ---

    private fun applyDesktopUA(view: WebView) {
        if (!usingDesktopUA) {
            originalUA = view.settings.userAgentString
            usingDesktopUA = true
        }
        view.settings.userAgentString = DESKTOP_UA
    }

    private fun restoreMobileUA(view: WebView) {
        view.settings.userAgentString = originalUA ?: WebSettings.getDefaultUserAgent(context)
        usingDesktopUA = false
    }

    // --- Intent Scheme ---

    private fun handleIntentScheme(url: String): Boolean {
        return try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)

            val resolvedIntent = if (intent.`package` != null) {
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                intent.setComponent(null)
                intent
            } else {
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                intent
            }

            context.startActivity(resolvedIntent)
            listener.onLaunchedExternalApp()
            true
        } catch (_: ActivityNotFoundException) {
            tryFallbackFromIntent(url)
        } catch (_: Exception) {
            true
        }
    }

    private fun tryFallbackFromIntent(url: String): Boolean {
        return try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (!fallback.isNullOrBlank()) {
                listener.onExternalLink(fallback)
            }
            true
        } catch (_: Exception) {
            true
        }
    }

    // --- Custom Scheme ---

    private fun launchNativeApp(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            context.startActivity(intent)
            listener.onLaunchedExternalApp()
            true
        } catch (_: ActivityNotFoundException) {
            true
        } catch (_: Exception) {
            true
        }
    }

    // --- Error Handling ---

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            listener.onError(
                error.errorCode,
                error.description?.toString(),
                request.url.toString()
            )
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        super.onReceivedSslError(view, handler, error)
        handler.cancel()
        listener.onError(-2, "SSL certificate error", view.url ?: "")
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request.isForMainFrame) {
            listener.onError(
                errorResponse.statusCode,
                errorResponse.reasonPhrase,
                request.url.toString()
            )
        }
    }

    // --- Utility ---

    private fun extractHost(url: String): String? {
        return try {
            Uri.parse(url).host?.lowercase()
        } catch (_: Exception) {
            null
        }
    }
}
