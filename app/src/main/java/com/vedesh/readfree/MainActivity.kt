package com.vedesh.readfree

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.vedesh.readfree.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Ordered list of Freedium-compatible mirrors to try on failure
    private val MIRRORS = listOf(
        "https://freedium.cfd/",
        "https://www.freedium.cfd/",
        "https://scribe.rip/"
    )
    private var currentMirrorIndex = 0

    // Tracks the original article URL so we can retry with a different mirror
    private var currentArticleUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupToolbar()
        setupBackNavigation()

        // Set up click listener once in onCreate to prevent leaks
        binding.btnPasteLoad.setOnClickListener {
            val pasted = binding.etUrl.text.toString().trim()
            if (pasted.isNotEmpty()) {
                loadArticle(pasted)
            } else {
                Toast.makeText(this, "Paste a Medium link first", Toast.LENGTH_SHORT).show()
            }
        }

        // Retry button: resets the mirror index and retries from the first mirror
        binding.btnRetry.setOnClickListener {
            if (currentArticleUrl.isNotEmpty()) {
                currentMirrorIndex = 0
                loadArticle(currentArticleUrl)
            }
        }

        // Handle the intent that launched this activity
        handleIntent(intent)
    }

    private fun setupBackNavigation() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.webView.canGoBack() -> binding.webView.goBack()
                    binding.readerLayout.visibility == View.VISIBLE -> showHomeScreen()
                    else -> {
                        // Disable this callback and let system handle back (e.g. finish activity)
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            // Shared via the share sheet (e.g. share from Chrome)
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                val url = extractUrl(sharedText)
                if (url != null) {
                    loadArticle(url)
                } else {
                    showHomeScreen()
                }
            }
            // Tapped a medium.com link directly
            Intent.ACTION_VIEW -> {
                val url = intent.data?.toString()
                if (url != null) {
                    loadArticle(url)
                } else {
                    showHomeScreen()
                }
            }
            // Opened from launcher with no link
            else -> showHomeScreen()
        }
    }

    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                // Respect HTTP cache-control headers; falls back to cache if offline
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    // Allow any mirror URL or known medium domain to load inside the WebView
                    val isMirror = MIRRORS.any { url.startsWith(it) }
                    return if (isMirror || isMediumDomain(url)) {
                        false // let WebView handle it
                    } else {
                        openInBrowser(url)
                        true
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    binding.loadingView.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.loadingView.visibility = View.GONE
                    url?.let { binding.urlBar.text = formatUrlForDisplay(it) }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    // Only react to main-frame failures, not sub-resource errors (images, scripts)
                    if (request?.isForMainFrame == true) {
                        tryNextMirrorOrShowError()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: android.webkit.WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    val statusCode = errorResponse?.statusCode ?: return
                    // Only failover on server-side errors (5xx), not client errors (4xx)
                    if (request?.isForMainFrame == true && statusCode >= 500) {
                        tryNextMirrorOrShowError()
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: android.net.http.SslError?
                ) {
                    binding.loadingView.visibility = View.GONE
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("SSL Certificate Warning")
                        .setMessage("This site has a certificate issue. Proceed anyway? Your connection may not be secure.")
                        .setPositiveButton("Proceed") { _, _ -> handler?.proceed() }
                        .setNegativeButton("Cancel") { _, _ -> handler?.cancel() }
                        .show()
                }
            }
        }
    }

    /**
     * Advances to the next mirror in the list and retries the current article.
     * If all mirrors are exhausted, shows the error panel instead.
     */
    private fun tryNextMirrorOrShowError() {
        binding.loadingView.visibility = View.GONE
        val nextIndex = currentMirrorIndex + 1
        if (nextIndex < MIRRORS.size && currentArticleUrl.isNotEmpty()) {
            currentMirrorIndex = nextIndex
            val nextMirrorUrl = MIRRORS[nextIndex] + currentArticleUrl
            binding.errorView.visibility = View.GONE
            binding.loadingView.visibility = View.VISIBLE
            binding.webView.loadUrl(nextMirrorUrl)
        } else {
            // All mirrors exhausted — show the error panel
            binding.errorView.visibility = View.VISIBLE
            binding.errorText.text = "All mirrors failed to load this article."
            binding.errorSubText.text = "Check your connection or try again later."
        }
    }

    private fun setupToolbar() {
        binding.btnOpenBrowser.setOnClickListener {
            val currentUrl = binding.webView.url
            if (currentUrl != null) {
                openInBrowser(currentUrl)
            }
        }

        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            }
        }
    }

    private fun loadArticle(originalUrl: String) {
        val cleanUrl = originalUrl.trim()
        currentArticleUrl = cleanUrl  // store for mirror retry
        currentMirrorIndex = 0        // always start from first mirror on a new load

        val freediumUrl = buildFreediumUrl(cleanUrl)

        binding.homeScreen.visibility = View.GONE
        binding.readerLayout.visibility = View.VISIBLE
        binding.errorView.visibility = View.GONE
        binding.loadingView.visibility = View.VISIBLE
        binding.webView.loadUrl(freediumUrl)
    }

    private fun buildFreediumUrl(originalUrl: String): String {
        // If already a mirror URL, don't double-wrap
        if (MIRRORS.any { originalUrl.startsWith(it) }) return originalUrl
        return "${MIRRORS[currentMirrorIndex]}$originalUrl"
    }

    private fun showHomeScreen() {
        binding.homeScreen.visibility = View.VISIBLE
        binding.readerLayout.visibility = View.GONE
    }

    private fun openInBrowser(url: String) {
        try {
            // Try Chrome Custom Tab first (stays branded)
            val customTabIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabIntent.launchUrl(this, Uri.parse(url))
        } catch (e: Exception) {
            // Fall back to default browser
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun extractUrl(text: String): String? {
        // Pull a URL out of shared text (Chrome shares "Title\nhttps://..." format)
        val regex = Regex("https?://[^\\s]+")
        return regex.find(text)?.value
    }

    private fun isMediumDomain(url: String): Boolean {
        val knownMediumHosts = setOf(
            "medium.com", "towardsdatascience.com", "betterprogramming.pub",
            "levelup.gitconnected.com", "javascript.plainenglish.io",
            "itnext.io", "blog.bitsrc.io", "hackernoon.com"
        )
        return try {
            val host = Uri.parse(url).host ?: return false
            knownMediumHosts.any { host.endsWith(it) }
        } catch (e: Exception) {
            false
        }
    }

    private fun formatUrlForDisplay(url: String): String {
        return if (url.length > 60) url.take(57) + "…" else url
    }


}
