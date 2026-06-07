package com.vedesh.readfree

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.vedesh.readfree.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Freedium mirror base URL — swap this if the mirror goes down
    private val FREEDIUM_BASE = "https://freedium-mirror.cfd/"

    // Domains we know are Medium publications
    private val MEDIUM_DOMAINS = setOf(
        "medium.com",
        "towardsdatascience.com",
        "betterprogramming.pub",
        "levelup.gitconnected.com",
        "javascript.plainenglish.io",
        "itnext.io",
        "blog.bitsrc.io",
        "hackernoon.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupToolbar()

        // Handle the intent that launched this activity
        handleIntent(intent)
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
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    // Stay inside the app for freedium URLs, open external links in browser
                    val url = request?.url?.toString() ?: return false
                    return if (url.contains("freedium") || isMediumDomain(url)) {
                        false // let WebView handle it
                    } else {
                        openInBrowser(url)
                        true
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.progressBar.visibility = View.GONE
                    // Show toolbar with current URL for reference
                    url?.let { binding.urlBar.text = formatUrlForDisplay(it) }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    binding.progressBar.progress = newProgress
                    binding.progressBar.visibility =
                        if (newProgress < 100) View.VISIBLE else View.GONE
                }
            }
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

        if (!isMediumDomain(cleanUrl) && !cleanUrl.contains("medium.com")) {
            // Not a Medium article — show message and offer to open anyway
            Toast.makeText(this, "Not a Medium link. Opening anyway…", Toast.LENGTH_SHORT).show()
        }

        val freediumUrl = buildFreediumUrl(cleanUrl)

        binding.homeScreen.visibility = View.GONE
        binding.readerLayout.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        binding.webView.loadUrl(freediumUrl)
    }

    private fun buildFreediumUrl(originalUrl: String): String {
        // Already a freedium URL? Don't double-wrap
        if (originalUrl.contains("freedium")) return originalUrl
        return "$FREEDIUM_BASE$originalUrl"
    }

    private fun showHomeScreen() {
        binding.homeScreen.visibility = View.VISIBLE
        binding.readerLayout.visibility = View.GONE

        binding.btnPasteLoad.setOnClickListener {
            val pasted = binding.etUrl.text.toString().trim()
            if (pasted.isNotEmpty()) {
                loadArticle(pasted)
            } else {
                Toast.makeText(this, "Paste a Medium link first", Toast.LENGTH_SHORT).show()
            }
        }
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
        return try {
            val host = Uri.parse(url).host ?: return false
            MEDIUM_DOMAINS.any { host.endsWith(it) }
        } catch (e: Exception) {
            false
        }
    }

    private fun formatUrlForDisplay(url: String): String {
        return if (url.length > 60) url.take(57) + "…" else url
    }

    override fun onBackPressed() {
        when {
            binding.webView.canGoBack() -> binding.webView.goBack()
            binding.readerLayout.visibility == View.VISIBLE -> showHomeScreen()
            else -> super.onBackPressed()
        }
    }
}
