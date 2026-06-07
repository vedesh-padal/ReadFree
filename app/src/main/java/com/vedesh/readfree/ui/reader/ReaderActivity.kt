package com.vedesh.readfree.ui.reader

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.vedesh.readfree.MirrorRepository
import com.vedesh.readfree.ReadFreeApp
import com.vedesh.readfree.ReadFreeWebViewClient
import com.vedesh.readfree.UrlUtils
import com.vedesh.readfree.databinding.ActivityReaderBinding
import com.vedesh.readfree.databinding.BottomSheetSaveBinding
import com.vedesh.readfree.databinding.BottomSheetSettingsBinding
import com.vedesh.readfree.ui.ViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ReaderActivity : AppCompatActivity(), ReadFreeWebViewClient.Listener {

    private lateinit var binding: ActivityReaderBinding
    private lateinit var mirrors: MirrorRepository

    private val viewModel: ReaderViewModel by viewModels {
        val app = applicationContext as ReadFreeApp
        ViewModelFactory(app.articleRepository, app.listRepository, app.tagRepository, app.raindropRepository)
    }

    private var currentArticleUrl: String = ""
    private var saveSheet: BottomSheetDialog? = null
    private var saveBinding: BottomSheetSaveBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mirrors = MirrorRepository(this)

        setupWebView()
        setupToolbar()
        setupBackNavigation()

        binding.btnOffline.setOnClickListener {
            saveOffline()
        }
        binding.btnSettings.setOnClickListener {
            showSettingsSheet()
        }
        binding.btnErrorSettings.setOnClickListener {
            showSettingsSheet()
        }

        binding.btnRetry.setOnClickListener {
            if (currentArticleUrl.isNotEmpty()) {
                mirrors.resetMirrorIndex()
                loadArticle(currentArticleUrl)
            }
        }

        handleIntent(intent)
    }

    private fun setupBackNavigation() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    finish()
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
        val url = when (intent.action) {
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                UrlUtils.extractUrl(sharedText)
            }
            Intent.ACTION_VIEW -> intent.data?.toString()
            else -> intent.getStringExtra("url")
        }

        if (url != null) {
            loadArticle(url)
        } else {
            finish()
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
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            }
            addJavascriptInterface(ReadFreeJSInterface(), "AndroidJS")
            webViewClient = ReadFreeWebViewClient(mirrors, this@ReaderActivity)
        }
    }

    private inner class ReadFreeJSInterface {
        @JavascriptInterface
        fun onScrollProgress(scrollY: Int, percentage: Float) {
            // Debounce or directly save; for simplicity, we directly save since room is fast enough,
            // but in a real app, we might debounce.
            if (currentArticleUrl.isNotEmpty()) {
                viewModel.updateScrollProgress(currentArticleUrl, scrollY, percentage)
            }
        }
    }

    override fun onPageStarted() {
        binding.loadingView.visibility = View.VISIBLE
    }

    override fun onPageFinished(url: String?) {
        binding.loadingView.visibility = View.GONE
        if (url != null) {
            binding.urlBar.text = UrlUtils.formatUrlForDisplay(url)
            
            // Extract title and set up scroll tracking
            binding.webView.evaluateJavascript("""
                (function() {
                    let debounceTimer;
                    window.addEventListener('scroll', function() {
                        clearTimeout(debounceTimer);
                        debounceTimer = setTimeout(function() {
                            var maxScroll = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight) - window.innerHeight;
                            var currentScroll = window.scrollY;
                            var percentage = maxScroll > 0 ? (currentScroll / maxScroll) * 100 : 0;
                            AndroidJS.onScrollProgress(Math.round(currentScroll), percentage);
                        }, 500); // 500ms debounce
                    });
                    return document.title;
                })();
            """.trimIndent()) { titleRaw ->
                val title = titleRaw?.removeSurrounding("\"")?.trim() ?: "Untitled"
                viewModel.updateTitle(currentArticleUrl, title)
                saveBinding?.tvSaveTitle?.text = title
            }

            // Restore previous scroll position
            viewModel.getArticle(currentArticleUrl) { article ->
                article?.scrollProgress?.let { scrollY ->
                    if (scrollY > 0) {
                        binding.webView.post {
                            binding.webView.evaluateJavascript("window.scrollTo(0, $scrollY);", null)
                        }
                    }
                }
            }
        }
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

    private fun tryNextMirrorOrShowError() {
        binding.loadingView.visibility = View.GONE
        if (UrlUtils.isMediumDomain(currentArticleUrl) && mirrors.tryNextMirror()) {
            binding.errorView.visibility = View.GONE
            binding.loadingView.visibility = View.VISIBLE
            binding.webView.loadUrl(mirrors.currentMirrorUrl(currentArticleUrl))
        } else {
            binding.errorView.visibility = View.VISIBLE
            binding.errorText.text = "Failed to load the article."
            
            if (UrlUtils.isMediumDomain(currentArticleUrl)) {
                binding.errorSubText.text = "Both the selected and default mirrors failed. You can verify your connection or configure a different mirror."
                binding.btnErrorSettings.visibility = View.VISIBLE
            } else {
                binding.errorSubText.text = "Could not load this page. Check your connection."
                binding.btnErrorSettings.visibility = View.GONE
            }
        }
    }

    private fun setupToolbar() {
        binding.btnOpenBrowser.setOnClickListener {
            binding.webView.url?.let { openInBrowser(it) }
        }
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                finish()
            }
        }
    }

    private fun showSettingsSheet() {
        val sheet = BottomSheetDialog(this)
        val sheetBinding = BottomSheetSettingsBinding.inflate(layoutInflater)
        sheet.setContentView(sheetBinding.root)

        val currentMirror = mirrors.getActiveMirror()

        if (currentMirror == MirrorRepository.DEFAULT_MIRROR) {
            sheetBinding.radioMirrorDefault.isChecked = true
        } else {
            sheetBinding.radioMirrorCustom.isChecked = true
            sheetBinding.customUrlLayout.visibility = View.VISIBLE
            sheetBinding.etCustomMirrorUrl.setText(currentMirror)
        }

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

            if (currentArticleUrl.isNotEmpty()) {
                mirrors.resetMirrorIndex()
                loadArticle(currentArticleUrl)
            }
        }

        sheet.show()
    }

    private fun loadArticle(originalUrl: String) {
        val cleanUrl = originalUrl.trim()
        currentArticleUrl = cleanUrl
        mirrors.resetMirrorIndex()

        binding.errorView.visibility = View.GONE
        binding.loadingView.visibility = View.VISIBLE
        
        binding.loadingText.text = if (UrlUtils.isMediumDomain(cleanUrl)) "Contacting Freedium…" else "Loading…"

        // Check for offline load first
        viewModel.getArticle(cleanUrl) { article ->
            val offlinePath = article?.offlineFilePath
            
            runOnUiThread {
                if (isOffline() && offlinePath != null && java.io.File(offlinePath).exists()) {
                    binding.loadingText.text = "Loading offline version…"
                    binding.webView.loadUrl("file://$offlinePath")
                } else {
                    val loadUrl = if (UrlUtils.isMediumDomain(cleanUrl)) mirrors.buildProxyUrl(cleanUrl) else cleanUrl
                    binding.webView.loadUrl(loadUrl)
                }
            }
            
            // Check if article is already saved to show banner
            if (article == null) {
                runOnUiThread { showSaveBanner(isSaved = false) }
            } else {
                runOnUiThread { showSaveBanner(isSaved = true) }
            }
        }
    }
    
    private val bannerHandler = Handler(Looper.getMainLooper())
    private val hideBannerRunnable = Runnable { binding.saveBanner.visibility = View.GONE }

    private fun showSaveBanner(isSaved: Boolean) {
        binding.saveBanner.visibility = View.VISIBLE
        if (isSaved) {
            binding.tvBannerText.text = "In your library"
            binding.btnBannerAction.text = "Edit"
        } else {
            binding.tvBannerText.text = "Add to library?"
            binding.btnBannerAction.text = "Save"
        }

        binding.btnBannerAction.setOnClickListener {
            hideBannerRunnable.run() // dismiss immediately
            showQuickSaveSheet()
        }

        binding.btnBannerDismiss.setOnClickListener {
            hideBannerRunnable.run()
        }

        // Auto-dismiss after 5s
        bannerHandler.removeCallbacks(hideBannerRunnable)
        bannerHandler.postDelayed(hideBannerRunnable, 5000)
    }
    
    private fun showQuickSaveSheet() {
        if (saveSheet != null) return
        
        saveSheet = BottomSheetDialog(this)
        saveBinding = BottomSheetSaveBinding.inflate(layoutInflater)
        saveSheet?.setContentView(saveBinding!!.root)
        
        saveSheet?.setOnDismissListener {
            saveSheet = null
            saveBinding = null
        }
        
        saveBinding?.tvSaveTitle?.text = "Loading title..."
        
        var selectedListId: Long? = null
        val selectedTags = mutableSetOf<String>()

        // Set up Tag input
        saveBinding?.etAddTag?.setOnEditorActionListener { v, actionId, event ->
            val text = v.text.toString().trim()
            if (text.isNotEmpty() && text.length < 30) {
                if (selectedTags.add(text.lowercase())) {
                    val chip = com.google.android.material.chip.Chip(this)
                    chip.text = text.lowercase()
                    chip.isCloseIconVisible = true
                    chip.setOnCloseIconClickListener {
                        selectedTags.remove(chip.text.toString())
                        saveBinding?.chipGroupTags?.removeView(chip)
                    }
                    saveBinding?.chipGroupTags?.addView(chip)
                }
                v.text = ""
            }
            true
        }

        // Populate lists
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lists.collectLatest { lists ->
                    val names = lists.map { it.list.name }.toTypedArray()
                    val adapter = android.widget.ArrayAdapter(
                        this@ReaderActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        names
                    )
                    saveBinding?.spinnerLists?.setAdapter(adapter)
                    
                    saveBinding?.spinnerLists?.setOnItemClickListener { _, _, position, _ ->
                        selectedListId = lists[position].list.id
                    }
                }
            }
        }

        var isEditMode = false

        // Fetch existing details if any
        viewModel.getArticleDetails(currentArticleUrl) { article, listIds, tags ->
            runOnUiThread {
                if (article != null) {
                    isEditMode = true
                    saveBinding?.tvSaveTitle?.text = article.title
                    saveBinding?.btnSaveAndClose?.text = "Update & Close"
                    saveBinding?.btnReadNow?.text = "Update"
                    selectedListId = listIds.firstOrNull()
                    
                    tags.forEach { tagName ->
                        if (selectedTags.add(tagName.lowercase())) {
                            val chip = com.google.android.material.chip.Chip(this@ReaderActivity)
                            chip.text = tagName.lowercase()
                            chip.isCloseIconVisible = true
                            chip.setOnCloseIconClickListener {
                                selectedTags.remove(chip.text.toString())
                                saveBinding?.chipGroupTags?.removeView(chip)
                            }
                            saveBinding?.chipGroupTags?.addView(chip)
                        }
                    }
                }
            }
        }

        saveBinding?.btnSaveAndClose?.setOnClickListener {
            viewModel.saveArticle(currentArticleUrl, saveBinding?.tvSaveTitle?.text.toString(), selectedListId, selectedTags.toList(), UrlUtils.isMediumDomain(currentArticleUrl))
            saveSheet?.dismiss()
            Toast.makeText(this, if (isEditMode) "Updated library" else "Saved to library", Toast.LENGTH_SHORT).show()
            finish()
        }

        saveBinding?.btnReadNow?.setOnClickListener {
            viewModel.saveArticle(currentArticleUrl, saveBinding?.tvSaveTitle?.text.toString(), selectedListId, selectedTags.toList(), UrlUtils.isMediumDomain(currentArticleUrl))
            saveSheet?.dismiss()
        }

        saveSheet?.show()
    }

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

    private fun isOffline(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return true
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return true
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun saveOffline() {
        if (currentArticleUrl.isEmpty()) return
        
        val dir = java.io.File(filesDir, "offline")
        if (!dir.exists()) dir.mkdirs()
        
        val filePath = java.io.File(dir, "${currentArticleUrl.hashCode()}.mht").absolutePath
        binding.webView.saveWebArchive(filePath, false) { savedPath ->
            if (savedPath != null) {
                // Update database
                viewModel.getArticle(currentArticleUrl) { article ->
                    if (article != null) {
                        viewModel.updateOfflinePath(currentArticleUrl, savedPath)
                        runOnUiThread {
                            Toast.makeText(this, "Saved for offline reading", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Save the article first!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Failed to save offline", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
