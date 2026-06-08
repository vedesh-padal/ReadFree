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
import android.view.ViewGroup
import android.widget.TextView
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
import com.vedesh.readfree.R
import com.vedesh.readfree.ReadFreeApp
import com.vedesh.readfree.ReadFreeWebViewClient
import com.vedesh.readfree.UrlUtils
import com.vedesh.readfree.databinding.ActivityReaderBinding
import com.vedesh.readfree.util.tooltipFromContentDescription
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

    private var suppressBanner = false
    private var isSaved = false
    private var currentReadState = com.vedesh.readfree.data.db.entity.ReadState.UNREAD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mirrors = MirrorRepository(this)

        setupWebView()
        setupToolbar()
        setupBackNavigation()

        binding.btnErrorSettings.setOnClickListener {
            android.widget.Toast.makeText(this, "Please configure mirror in Home Settings", android.widget.Toast.LENGTH_SHORT).show()
        }

        binding.btnRetry.setOnClickListener {
            if (currentArticleUrl.isNotEmpty()) {
                mirrors.resetMirrorIndex()
                loadArticle(currentArticleUrl)
            }
        }

        // Bottom action bar buttons
        binding.btnBottomBookmark.setOnClickListener {
            showQuickSaveSheet()
        }

        binding.btnBottomMarkRead.setOnClickListener {
            toggleReadState()
        }

        binding.btnBottomRaindrop.setOnClickListener {
            if (currentArticleUrl.isEmpty()) return@setOnClickListener
            val app = applicationContext as ReadFreeApp
            val url = currentArticleUrl
            val title = binding.webView.title ?: currentArticleUrl
            app.saveToRaindrop(this, url, title) { success ->
                runOnUiThread {
                    if (success) {
                        com.google.android.material.snackbar.Snackbar.make(binding.root, "Sent to Raindrop", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                    } else {
                        com.google.android.material.snackbar.Snackbar.make(binding.root, "Failed to save to Raindrop", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                            .setAction("Retry") {
                                app.saveToRaindrop(this, url, title) { retrySuccess ->
                                    runOnUiThread {
                                        com.google.android.material.snackbar.Snackbar.make(
                                            binding.root,
                                            if (retrySuccess) "Sent to Raindrop" else "Failed to save to Raindrop",
                                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }.show()
                    }
                }
            }
        }

        binding.btnBottomBrowser.setOnClickListener {
            binding.webView.url?.let { url -> openInBrowser(url) }
        }

        binding.btnBottomSettings.setOnClickListener {
            showSettingsSheet()
        }

        // Overflow menu button
        binding.btnReaderMore.setOnClickListener {
            showOverflowSheet()
        }

        // Observe progress for the reading progress bar
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.progressPercentage.collectLatest { pct ->
                    binding.readingProgress.progress = pct
                    binding.readingProgress.visibility = if (pct > 0 && pct < 100) View.VISIBLE else View.GONE
                }
            }
        }

        // Flush progress on pause
        lifecycle.addObserver(object : androidx.lifecycle.LifecycleEventObserver {
            override fun onStateChanged(
                source: androidx.lifecycle.LifecycleOwner,
                event: androidx.lifecycle.Lifecycle.Event,
            ) {
                if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE && currentArticleUrl.isNotEmpty()) {
                    binding.webView.evaluateJavascript(
                        "(function(){ return JSON.stringify({scrollY: window.scrollY, height: document.body.scrollHeight, viewport: window.innerHeight}); })();",
                    ) { json ->
                        if (json != null) {
                            try {
                                val obj = org.json.JSONObject(json)
                                val scrollY = obj.getInt("scrollY")
                                val height = obj.getInt("height")
                                val viewport = obj.getInt("viewport")
                                val pct = if (height > viewport) ((scrollY.toFloat() / (height - viewport)) * 100f) else 100f
                                viewModel.updateScrollProgress(currentArticleUrl, scrollY, pct)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        })

        // Long-press tooltips for icon-only buttons
        with(binding) {
            btnBack.tooltipFromContentDescription()
            btnReaderMore.tooltipFromContentDescription()
            btnBottomBookmark.tooltipFromContentDescription()
            btnBottomMarkRead.tooltipFromContentDescription()
            btnBottomRaindrop.tooltipFromContentDescription()
            btnBottomBrowser.tooltipFromContentDescription()
            btnBottomSettings.tooltipFromContentDescription()
            btnBannerDismiss.tooltipFromContentDescription()
        }

        handleIntent(intent)
    }

    private fun toggleReadState() {
        if (!isSaved || currentArticleUrl.isEmpty()) return
        currentReadState = if (currentReadState == com.vedesh.readfree.data.db.entity.ReadState.READ)
            com.vedesh.readfree.data.db.entity.ReadState.UNREAD
        else
            com.vedesh.readfree.data.db.entity.ReadState.READ
        viewModel.setReadState(currentArticleUrl, currentReadState)
        updateBottomBar()
    }

    private fun updateBottomBar() {
        if (isSaved) {
            binding.btnBottomBookmark.setImageResource(R.drawable.ic_bookmark)
            binding.btnBottomBookmark.imageTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.accent, null))
            binding.btnBottomMarkRead.isEnabled = true
            binding.btnBottomMarkRead.imageTintList = android.content.res.ColorStateList.valueOf(
                if (currentReadState == com.vedesh.readfree.data.db.entity.ReadState.READ)
                    resources.getColor(R.color.read_state_read, null)
                else
                    resources.getColor(R.color.text_primary, null)
            )
        } else {
            binding.btnBottomBookmark.setImageResource(R.drawable.ic_bookmark_outline)
            binding.btnBottomBookmark.imageTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.text_primary, null))
            binding.btnBottomMarkRead.isEnabled = false
            binding.btnBottomMarkRead.imageTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.text_tertiary, null))
        }
    }

    private fun showOverflowSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_reader_overflow, null)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.btnOverflowMoveToList).setOnClickListener {
            sheet.dismiss()
            showQuickSaveSheet()
        }

        view.findViewById<View>(R.id.btnOverflowEditTags).setOnClickListener {
            sheet.dismiss()
            showQuickSaveSheet()
        }

        view.findViewById<View>(R.id.btnOverflowSaveOffline).setOnClickListener {
            sheet.dismiss()
            saveOffline()
        }

        view.findViewById<View>(R.id.btnOverflowCopyUrl).setOnClickListener {
            sheet.dismiss()
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("URL", currentArticleUrl)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "URL copied", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnOverflowShare).setOnClickListener {
            sheet.dismiss()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, currentArticleUrl)
            }
            startActivity(Intent.createChooser(intent, "Share via"))
        }

        sheet.show()
    }

    private fun setupBackNavigation() {
        val callback =
            object : OnBackPressedCallback(true) {
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
        val url =
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    UrlUtils.extractUrl(sharedText)
                }
                Intent.ACTION_VIEW -> intent.data?.toString()
                else -> intent.getStringExtra("url")
            }

        if (url != null) {
            loadArticle(url)

            if (intent.getBooleanExtra("show_save_sheet", false) || intent.action == Intent.ACTION_SEND) {
                // We delay slightly to let the view settle if needed, but direct call is fine
                binding.root.post {
                    showQuickSaveSheet()
                }
            }
        } else {
            finish()
        }
    }

    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                @Suppress("SetJavaScriptEnabled")
                // Required to load embedded resources within .mht web archive files
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            }
            addJavascriptInterface(ReadFreeJSInterface(), "AndroidJS")
            webViewClient = ReadFreeWebViewClient(this@ReaderActivity)
        }
    }

    private inner class ReadFreeJSInterface {
        @JavascriptInterface
        fun onScrollProgress(
            scrollY: Int,
            percentage: Float,
        ) {
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
            binding.webView.evaluateJavascript(
                """
                (function() {
                    let debounceTimer;
                    function reportScroll() {
                        var maxScroll = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight) - window.innerHeight;
                        var currentScroll = window.scrollY;
                        var percentage = maxScroll > 0 ? (currentScroll / maxScroll) * 100 : 0;
                        AndroidJS.onScrollProgress(Math.round(currentScroll), percentage);
                    }
                    window.addEventListener('scroll', function() {
                        clearTimeout(debounceTimer);
                        debounceTimer = setTimeout(reportScroll, 500);
                    });
                    window.addEventListener('resize', function() {
                        clearTimeout(debounceTimer);
                        debounceTimer = setTimeout(reportScroll, 300);
                    });
                    return document.title;
                })();
                """.trimIndent(),
            ) { titleRaw ->
                val title = titleRaw?.removeSurrounding("\"")?.trim() ?: "Untitled"
                viewModel.updateTitle(currentArticleUrl, title)
                saveBinding?.tvSaveTitle?.text = title
            }

            // Auto-mark READ if page fits entirely in viewport
            binding.webView.evaluateJavascript(
                "(function(){ var h = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight); return h <= window.innerHeight ? 1 : 0; })();",
            ) { isShort ->
                if (isShort == "1") {
                    viewModel.setReadState(currentArticleUrl, com.vedesh.readfree.data.db.entity.ReadState.READ)
                }
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
            val selected =
                sheetBinding.root.findViewById<RadioButton>(
                    sheetBinding.radioGroupMirrors.checkedRadioButtonId,
                )
            val newUrl =
                if (selected?.id == sheetBinding.radioMirrorCustom.id) {
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

            // Track saved state and read state for bottom bar
            isSaved = article != null
            if (article != null) {
                currentReadState = article.readState
            }
            runOnUiThread { updateBottomBar() }

            // Banner logic: only show 'in library' if the article was explicitly saved
            if (article == null) {
                runOnUiThread { showSaveBanner(isSaved = false) }
            } else if (suppressBanner) {
                suppressBanner = false
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

    private fun showCreateListSheet(onCreated: (String) -> Unit) {
        val sheet = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_create_list, null)
        sheet.setContentView(sheetView)

        val header = (sheetView as? ViewGroup)?.getChildAt(0) as? TextView
        header?.text = "New List"

        val etListName = sheetView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etListName)
        val chipGroupEmojis = sheetView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupEmojis)
        val radioGroupColors = sheetView.findViewById<android.widget.RadioGroup>(R.id.radioGroupColors)
        val btnCreateList = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCreateList)

        val emojis = listOf("\uD83D\uDCC1", "\uD83D\uDCDA", "\uD83D\uDCBC", "\uD83E\uDD16", "\uD83C\uDFAF", "\u2B50", "\uD83D\uDD2C", "\uD83C\uDFA8", "\uD83C\uDF10", "\uD83D\uDCDD", "\uD83C\uDFE0", "\uD83D\uDCA1", "\uD83D\uDD25", "\uD83C\uDF31", "\uD83C\uDFB5", "\uD83C\uDFAE")
        val colors = listOf("#6C63FF", "#FF6584", "#4CAF50", "#FF9800", "#00BCD4", "#E91E63", "#9C27B0", "#3F51B5")

        chipGroupEmojis.removeAllViews()
        emojis.forEach { emoji ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = emoji
                isCheckable = true
                setChipDrawable(com.google.android.material.chip.ChipDrawable.createFromAttributes(this@ReaderActivity, null, 0, com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice))
            }
            chipGroupEmojis.addView(chip)
        }

        radioGroupColors.removeAllViews()
        colors.forEach { colorHex ->
            val rb = android.widget.RadioButton(this).apply {
                buttonTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(colorHex))
                tag = colorHex
            }
            radioGroupColors.addView(rb)
        }

        if (chipGroupEmojis.childCount > 0) (chipGroupEmojis.getChildAt(0) as com.google.android.material.chip.Chip).isChecked = true
        if (radioGroupColors.childCount > 0) (radioGroupColors.getChildAt(0) as android.widget.RadioButton).isChecked = true

        btnCreateList.text = "Create"
        btnCreateList.setOnClickListener {
            val name = etListName.text.toString().trim()
            if (name.isNotEmpty()) {
                val selectedEmojiChip = chipGroupEmojis.findViewById<com.google.android.material.chip.Chip>(chipGroupEmojis.checkedChipId)
                val emoji = selectedEmojiChip?.text?.toString() ?: "\uD83D\uDCC1"
                val selectedColorRb = radioGroupColors.findViewById<android.widget.RadioButton>(radioGroupColors.checkedRadioButtonId)
                val color = selectedColorRb?.tag?.toString() ?: "#6C63FF"
                viewModel.createList(name, emoji, color)
                onCreated(name)
                sheet.dismiss()
            }
        }

        sheet.show()
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

        saveBinding?.tvSaveTitle?.text = binding.webView.title?.takeIf { it.isNotBlank() } ?: "Loading title..."

        var selectedListId: Long? = null
        val selectedTags = mutableSetOf<String>()

        // Tag autocomplete: show existing tags as suggestions
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allTags.collectLatest { tags ->
                    val tagNames = tags.map { it.name }.toTypedArray()
                    val tagAdapter =
                        android.widget.ArrayAdapter(
                            this@ReaderActivity,
                            android.R.layout.simple_dropdown_item_1line,
                            tagNames,
                        )
                    saveBinding?.etAddTag?.setAdapter(tagAdapter)
                    saveBinding?.etAddTag?.threshold = 1
                    // Allow dropdown to overlay the bottom sheet
                    saveBinding?.etAddTag?.dropDownAnchor = View.NO_ID
                    saveBinding?.etAddTag?.dropDownHeight = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }
        }

        fun addTag(raw: String) {
            val text = raw.trim().lowercase()
            if (text.isNotEmpty() && text.length < 30 && selectedTags.add(text)) {
                val chip = com.google.android.material.chip.Chip(this)
                chip.text = text
                chip.isCloseIconVisible = true
                chip.setOnCloseIconClickListener {
                    selectedTags.remove(chip.text.toString())
                    saveBinding?.chipGroupTags?.removeView(chip)
                }
                saveBinding?.chipGroupTags?.addView(chip)
            }
        }

        fun flushTagInput() {
            val text = saveBinding?.etAddTag?.text?.toString() ?: return
            if (text.isBlank()) return
            text.split(",").forEach { addTag(it) }
            saveBinding?.etAddTag?.setText("")
        }

        // Set up Tag input
        saveBinding?.etAddTag?.setOnEditorActionListener { v, _, _ ->
            val text = v.text.toString()
            if (text.isNotBlank()) {
                text.split(",").forEach { addTag(it) }
                v.text = ""
            }
            true
        }

        // Tap a suggestion: add the tag immediately
        saveBinding?.etAddTag?.setOnItemClickListener { parent, _, position, _ ->
            val tagName = parent.getItemAtPosition(position) as String
            addTag(tagName)
            saveBinding?.etAddTag?.setText("")
        }

        // Populate lists
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lists.collectLatest { lists ->
                    val names = (lists.map { it.list.name } + "+ Create new list").toTypedArray()
                    val adapter =
                        android.widget.ArrayAdapter(
                            this@ReaderActivity,
                            android.R.layout.simple_dropdown_item_1line,
                            names,
                        )
                    saveBinding?.spinnerLists?.setAdapter(adapter)

                    saveBinding?.spinnerLists?.setOnItemClickListener { _, _, position, _ ->
                        if (position < lists.size) {
                            selectedListId = lists[position].list.id
                        } else {
                            showCreateListSheet { newListName ->
                                saveBinding?.spinnerLists?.setText(newListName, false)
                            }
                        }
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
            flushTagInput()
            lifecycleScope.launch {
                viewModel.saveArticle(
                    currentArticleUrl,
                    saveBinding?.tvSaveTitle?.text.toString(),
                    selectedListId,
                    selectedTags.toList(),
                    UrlUtils.isMediumDomain(currentArticleUrl),
                ).join()
                saveSheet?.dismiss()
                Toast.makeText(this@ReaderActivity, if (isEditMode) "Updated library" else "Saved to library", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        saveBinding?.btnReadNow?.setOnClickListener {
            flushTagInput()
            viewModel.saveArticle(
                currentArticleUrl,
                saveBinding?.tvSaveTitle?.text.toString(),
                selectedListId,
                selectedTags.toList(),
                UrlUtils.isMediumDomain(currentArticleUrl),
            )
            isSaved = true
            currentReadState = com.vedesh.readfree.data.db.entity.ReadState.UNREAD
            updateBottomBar()
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
                    } else {
                        val title = binding.webView.title?.takeIf { it.isNotBlank() } ?: "Untitled Article"
                        suppressBanner = true
                        viewModel.saveArticle(currentArticleUrl, title, null, emptyList(), UrlUtils.isMediumDomain(currentArticleUrl))
                        viewModel.updateOfflinePath(currentArticleUrl, savedPath)
                        isSaved = true
                        currentReadState = com.vedesh.readfree.data.db.entity.ReadState.UNREAD
                    }
                    runOnUiThread {
                        updateBottomBar()
                        Toast.makeText(this, "Saved for offline reading", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                runOnUiThread {
                    com.google.android.material.snackbar.Snackbar
                        .make(binding.root, "Could not save offline — page has cross-origin restrictions", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .show()
                }
            }
        }
    }
}
