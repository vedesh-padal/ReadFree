package com.vedesh.readfree.ui.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
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
        ViewModelFactory(app.articleRepository, app.listRepository, app.tagRepository)
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
            webViewClient = ReadFreeWebViewClient(mirrors, this@ReaderActivity)
        }
    }

    override fun onPageStarted() {
        binding.loadingView.visibility = View.VISIBLE
    }

    override fun onPageFinished(url: String?) {
        binding.loadingView.visibility = View.GONE
        if (url != null) {
            binding.urlBar.text = UrlUtils.formatUrlForDisplay(url)
            
            // Try to extract title from webview
            binding.webView.evaluateJavascript("(function() { return document.title; })();") { titleRaw ->
                val title = titleRaw?.removeSurrounding("\"")?.trim() ?: "Untitled"
                viewModel.updateTitle(currentArticleUrl, title)
                
                // If save sheet is currently showing, update its title
                saveBinding?.tvSaveTitle?.text = title
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
        binding.btnSettings.setOnClickListener {
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

        val loadUrl = if (UrlUtils.isMediumDomain(cleanUrl)) mirrors.buildProxyUrl(cleanUrl) else cleanUrl
        binding.webView.loadUrl(loadUrl)
        
        // Check if article is already saved, if not, show quick save sheet
        viewModel.checkIfExists(cleanUrl) { exists ->
            if (!exists) {
                showQuickSaveSheet()
            }
        }
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

        saveBinding?.btnSaveAndClose?.setOnClickListener {
            viewModel.saveArticle(currentArticleUrl, saveBinding?.tvSaveTitle?.text.toString(), selectedListId, UrlUtils.isMediumDomain(currentArticleUrl))
            saveSheet?.dismiss()
            Toast.makeText(this, "Saved to library", Toast.LENGTH_SHORT).show()
            finish()
        }

        saveBinding?.btnReadNow?.setOnClickListener {
            viewModel.saveArticle(currentArticleUrl, saveBinding?.tvSaveTitle?.text.toString(), selectedListId, UrlUtils.isMediumDomain(currentArticleUrl))
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
}
