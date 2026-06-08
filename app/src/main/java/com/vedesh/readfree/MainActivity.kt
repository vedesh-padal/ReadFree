package com.vedesh.readfree

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.vedesh.readfree.databinding.ActivityMainBinding
import com.vedesh.readfree.databinding.BottomSheetSettingsBinding

class MainActivity : AppCompatActivity(), ReadFreeWebViewClient.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mirrors: MirrorRepository

    // Tracks the original article URL so we can retry with a different mirror
    private var currentArticleUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mirrors = MirrorRepository(this)

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

        // Retry button: resets failover index and reloads from mirror 0
        binding.btnRetry.setOnClickListener {
            if (currentArticleUrl.isNotEmpty()) {
                mirrors.resetMirrorIndex()
                loadArticle(currentArticleUrl)
            }
        }

        // Handle the intent that launched this activity
        handleIntent(intent)
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun setupBackNavigation() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.webView.canGoBack() -> binding.webView.goBack()
                    binding.readerLayout.visibility == View.VISIBLE -> showHomeScreen()
                    else -> {
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
                val url = UrlUtils.extractUrl(sharedText)
                if (url != null) loadArticle(url) else showHomeScreen()
            }
            // Tapped a medium.com link directly
            Intent.ACTION_VIEW -> {
                val url = intent.data?.toString()
                if (url != null) loadArticle(url) else showHomeScreen()
            }
            // Opened from launcher with no link
            else -> showHomeScreen()
        }
    }

    // ── WebView setup ─────────────────────────────────────────────────────────

    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            }
            webViewClient = ReadFreeWebViewClient(mirrors, this@MainActivity)
        }
    }

    // ── ReadFreeWebViewClient.Listener ────────────────────────────────────────

    override fun onPageStarted() {
        binding.loadingView.visibility = View.VISIBLE
    }

    override fun onPageFinished(url: String?) {
        binding.loadingView.visibility = View.GONE
        url?.let { binding.urlBar.text = UrlUtils.formatUrlForDisplay(it) }
    }

    override fun onMainFrameError() {
        tryNextMirrorOrShowError()
    }

    override fun onMainFrameHttpError(statusCode: Int) {
        tryNextMirrorOrShowError()
    }

    override fun onSslError(handler: android.webkit.SslErrorHandler?) {
        binding.loadingView.visibility = View.GONE
        AlertDialog.Builder(this)
            .setTitle("SSL Certificate Warning")
            .setMessage("This site has a certificate issue. Proceed anyway? Your connection may not be secure.")
            .setPositiveButton("Proceed") { _, _ -> handler?.proceed() }
            .setNegativeButton("Cancel") { _, _ -> handler?.cancel() }
            .show()
    }

    override fun onExternalUrlRequested(url: String) {
        openInBrowser(url)
    }

    // ── Mirror failover ───────────────────────────────────────────────────────

    private fun tryNextMirrorOrShowError() {
        binding.loadingView.visibility = View.GONE
        if (mirrors.tryNextMirror() && currentArticleUrl.isNotEmpty()) {
            binding.errorView.visibility = View.GONE
            binding.loadingView.visibility = View.VISIBLE
            binding.webView.loadUrl(mirrors.currentMirrorUrl(currentArticleUrl))
        } else {
            binding.errorView.visibility = View.VISIBLE
            binding.errorText.text = "Failed to load the article."
            binding.errorSubText.text = "Both the selected and default mirrors failed. You can verify your connection or configure a different mirror."
        }
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.btnCopyLink.setOnClickListener {
            val url = binding.webView.url ?: currentArticleUrl
            if (url.isNotEmpty()) {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Article Link", url)
                clipboard.setPrimaryClip(clip)
                
                // Android 13+ shows its own visual clipboard overlay.
                // Showing a toast here causes duplicate UI.
                if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S_V2) {
                    Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.btnOpenBrowser.setOnClickListener {
            binding.webView.url?.let { openInBrowser(it) }
        }
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                showHomeScreen()
            }
        }
        binding.btnSettings.setOnClickListener {
            showSettingsSheet()
        }
        binding.btnHomeSettings.setOnClickListener {
            showSettingsSheet()
        }
        binding.btnErrorSettings.setOnClickListener {
            showSettingsSheet()
        }
    }

    private fun showSettingsSheet() {
        val sheet = BottomSheetDialog(this)
        val sheetBinding = BottomSheetSettingsBinding.inflate(layoutInflater)
        sheet.setContentView(sheetBinding.root)

        val currentMirror = mirrors.getActiveMirror()

        // Pre-select the default or custom radio
        if (currentMirror == MirrorRepository.DEFAULT_MIRROR) {
            sheetBinding.radioMirrorDefault.isChecked = true
        } else {
            sheetBinding.radioMirrorCustom.isChecked = true
            sheetBinding.customUrlLayout.visibility = View.VISIBLE
            sheetBinding.etCustomMirrorUrl.setText(currentMirror)
        }

        // Show/hide custom URL input based on radio selection
        sheetBinding.radioGroupMirrors.setOnCheckedChangeListener { _, checkedId ->
            sheetBinding.customUrlLayout.visibility =
                if (checkedId == sheetBinding.radioMirrorCustom.id) View.VISIBLE else View.GONE
        }

        sheetBinding.btnApplyMirror.setOnClickListener {
            val selected = sheetBinding.root.findViewById<RadioButton>(
                sheetBinding.radioGroupMirrors.checkedRadioButtonId
            )
            val newUrl = if (selected?.id == sheetBinding.radioMirrorCustom.id) {
                val custom = sheetBinding.etCustomMirrorUrl.text.toString().trim()
                if (custom.isEmpty()) {
                    Toast.makeText(this, "Enter a mirror URL first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (custom.endsWith("/")) custom else "$custom/"
            } else {
                MirrorRepository.DEFAULT_MIRROR
            }

            mirrors.saveUserMirror(newUrl)
            sheet.dismiss()
            Toast.makeText(this, "Mirror set to: $newUrl", Toast.LENGTH_SHORT).show()

            // Reload the current article with the new mirror if reader is open
            if (currentArticleUrl.isNotEmpty() && binding.readerLayout.visibility == View.VISIBLE) {
                mirrors.resetMirrorIndex()
                loadArticle(currentArticleUrl)
            }
        }

        sheet.show()
    }

    // ── Article loading ───────────────────────────────────────────────────────

    private fun loadArticle(originalUrl: String) {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)

        val cleanUrl = originalUrl.trim()
        currentArticleUrl = cleanUrl
        mirrors.resetMirrorIndex()

        binding.homeScreen.visibility = View.GONE
        binding.btnHomeSettings.visibility = View.GONE
        binding.readerLayout.visibility = View.VISIBLE
        binding.errorView.visibility = View.GONE
        binding.loadingView.visibility = View.VISIBLE
        binding.webView.loadUrl(mirrors.buildProxyUrl(cleanUrl))
    }

    private fun showHomeScreen() {
        binding.homeScreen.visibility = View.VISIBLE
        binding.btnHomeSettings.visibility = View.VISIBLE
        binding.readerLayout.visibility = View.GONE
    }

    // ── External browser ──────────────────────────────────────────────────────

    private fun openInBrowser(url: String) {
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(this, Uri.parse(url))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
