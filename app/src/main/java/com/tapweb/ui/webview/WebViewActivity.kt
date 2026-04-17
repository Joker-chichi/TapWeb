package com.tapweb.ui.webview

import android.app.DownloadManager
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.snackbar.Snackbar
import com.tapweb.R
import com.tapweb.databinding.ActivityWebviewBinding
import com.tapweb.ui.common.ErrorLayoutHelper
import com.tapweb.util.BrowserLauncher

class WebViewActivity : AppCompatActivity(), CustomWebViewClient.WebViewListener {

    companion object {
        const val EXTRA_WEBSITE_ID = "website_id"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"

        private const val JS_BLOCK_VISIBILITY = """
            (function() {
                document.addEventListener('visibilitychange', function(e) {
                    e.stopImmediatePropagation();
                }, true);
                window.addEventListener('visibilitychange', function(e) {
                    e.stopImmediatePropagation();
                }, true);
                try {
                    Object.defineProperty(document, 'hidden', {
                        value: false, writable: false, configurable: true
                    });
                    Object.defineProperty(document, 'visibilityState', {
                        value: 'visible', writable: false, configurable: true
                    });
                } catch(e) {}
            })();
        """

        private const val JS_PAUSE_MEDIA = """
            (function() {
                var videos = document.querySelectorAll('video');
                var audios = document.querySelectorAll('audio');
                videos.forEach(function(v) { v.pause(); });
                audios.forEach(function(a) { a.pause(); });
            })();
        """
    }

    private lateinit var binding: ActivityWebviewBinding
    private lateinit var errorHelper: ErrorLayoutHelper
    private lateinit var customWebViewClient: CustomWebViewClient
    private var currentUrl: String = ""
    private var launchedExternalApp = false
    private var hintShown = false

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupImmersive()
        setupFloatingCapsule()
        setupErrorHelper()
        setupWebView()
        setupBackHandler()

        currentUrl = intent.getStringExtra(EXTRA_URL) ?: ""

        if (currentUrl.isNotBlank()) {
            if (isNetworkAvailable()) {
                loadUrl(currentUrl)
            } else {
                errorHelper.show(
                    ErrorLayoutHelper.ErrorType.NO_NETWORK,
                    onRetry = { loadUrl(currentUrl) },
                    onOpenBrowser = { BrowserLauncher.open(this, currentUrl) }
                )
            }
        }
    }

    private fun setupImmersive() {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val controller = WindowCompat.getInsetsController(window, binding.webView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }

    private fun setupFloatingCapsule() {
        binding.floatingCapsule.onOpenBrowser = {
            BrowserLauncher.open(this, currentUrl)
        }
        binding.floatingCapsule.onShare = {
            shareUrl()
        }
        binding.floatingCapsule.setPositionDefault()
    }

    private fun setupErrorHelper() {
        errorHelper = ErrorLayoutHelper(binding.errorLayout.root)
    }

    private fun setupWebView() {
        customWebViewClient = CustomWebViewClient(this, this)

        currentUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        val domain = try {
            java.net.URI(currentUrl).host
        } catch (_: Exception) {
            null
        }
        if (domain != null) {
            customWebViewClient.setBaseDomain(domain)
        }

        binding.webView.apply {
            this.webViewClient = this@WebViewActivity.customWebViewClient

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    this@WebViewActivity.onProgressChanged(newProgress)
                }

                override fun onShowFileChooser(
                    webView: WebView,
                    callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams
                ): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = callback
                    try {
                        val types = params.acceptTypes.filter { it.isNotBlank() }.toTypedArray()
                        fileChooserLauncher.launch(types.ifEmpty { arrayOf("*/*") })
                    } catch (_: Exception) {
                        filePathCallback = null
                        Toast.makeText(
                            this@WebViewActivity,
                            getString(R.string.no_app_to_handle),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return true
                }
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                handleDownload(url, contentDisposition, mimetype)
            }
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun handleDownload(
        url: String,
        contentDisposition: String?,
        mimetype: String?
    ) {
        try {
            val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription(getString(R.string.downloading))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("cookie", cookies)
                }
            }
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, getString(R.string.downloading), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            BrowserLauncher.open(this, url)
        }
    }

    private fun loadUrl(url: String) {
        errorHelper.hide()
        customWebViewClient.startWhiteScreenTimer()
        binding.webView.loadUrl(url)
    }

    private fun shareUrl() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, currentUrl)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    // WebViewListener

    override fun onPageStarted(url: String) {
        currentUrl = url
        binding.progressBar.visibility = View.VISIBLE
        binding.floatingCapsule.resetVisibility()
    }

    override fun onPageFinished(url: String) {
        binding.progressBar.visibility = View.GONE
        binding.webView.evaluateJavascript(JS_BLOCK_VISIBILITY, null)
        showHintOnce()
    }

    private fun showHintOnce() {
        if (hintShown) return
        hintShown = true
        Snackbar.make(
            binding.root,
            getString(R.string.webview_hint_msg),
            Snackbar.LENGTH_LONG
        ).setAction(getString(R.string.webview_hint_action)) {}
            .show()
    }

    override fun onProgressChanged(newProgress: Int) {
        binding.progressBar.progress = newProgress
        if (newProgress == 100) {
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onError(errorCode: Int, description: String?, failingUrl: String) {
        binding.progressBar.visibility = View.GONE

        val errorType = when (errorCode) {
            -1 -> ErrorLayoutHelper.ErrorType.TIMEOUT
            -2 -> ErrorLayoutHelper.ErrorType.LOAD_FAILED
            else -> if (!isNetworkAvailable()) ErrorLayoutHelper.ErrorType.NO_NETWORK
            else ErrorLayoutHelper.ErrorType.LOAD_FAILED
        }

        errorHelper.show(
            errorType,
            onRetry = { loadUrl(currentUrl) },
            onOpenBrowser = { BrowserLauncher.open(this, currentUrl) }
        )
    }

    override fun onExternalLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.no_app_to_handle), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onLaunchedExternalApp() {
        launchedExternalApp = true
    }

    override fun onResume() {
        super.onResume()

        if (customWebViewClient.isOnOAuthPage()) {
            // OAuth page: don't touch WebView at all — let JS keep running
            // so QR code polling/WebSocket can detect the scan result
            return
        }

        binding.webView.onResume()
        binding.webView.resumeTimers()

        if (launchedExternalApp) {
            launchedExternalApp = false
            binding.webView.reload()
        }
    }

    override fun onPause() {
        if (customWebViewClient.isOnOAuthPage()) {
            // OAuth page: keep WebView fully alive so JS polling continues
            super.onPause()
            return
        }

        binding.webView.evaluateJavascript(JS_PAUSE_MEDIA, null)
        binding.webView.onPause()
        binding.webView.pauseTimers()
        super.onPause()
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        customWebViewClient.cancelWhiteScreenTimer()
        binding.webView.destroy()
        super.onDestroy()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
