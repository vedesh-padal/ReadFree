package com.vedesh.readfree.ui.home

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.vedesh.readfree.MirrorRepository
import com.vedesh.readfree.R
import com.vedesh.readfree.ReadFreeApp
import com.vedesh.readfree.data.db.entity.ReadState
import com.vedesh.readfree.data.model.ArticleWithTags
import com.vedesh.readfree.databinding.BottomSheetSettingsBinding
import com.vedesh.readfree.databinding.FragmentHomeBinding
import com.vedesh.readfree.ui.ViewModelFactory
import com.vedesh.readfree.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var mirrors: MirrorRepository
    private lateinit var adapter: ArticleAdapter

    private val viewModel: HomeViewModel by viewModels {
        val app = requireContext().applicationContext as ReadFreeApp
        ViewModelFactory(app.articleRepository, app.listRepository, app.tagRepository, app.raindropRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        mirrors = MirrorRepository(requireContext())
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchAndFilters()
        observeViewModel()

        binding.btnHomeSettings.setOnClickListener {
            showSettingsSheet()
        }

        binding.btnHomeLists.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_listsFragment)
        }

        binding.btnHomeTags.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_tagsFragment)
        }
    }

    private fun setupRecyclerView() {
        adapter =
            ArticleAdapter(
                onClick = { articleWithTags ->
                    // Spec: opened from library → UNREAD becomes READING
                    if (articleWithTags.article.readState == ReadState.UNREAD) {
                        viewModel.setReadState(articleWithTags.article.url, ReadState.READING)
                    }
                    val intent = Intent(requireContext(), ReaderActivity::class.java)
                    intent.putExtra("url", articleWithTags.article.url)
                    startActivity(intent)
                },
                onLongClick = { articleWithTags ->
                    showArticleContextSheet(articleWithTags)
                },
            )
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val swipeHandler =
            object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                override fun onMove(
                    r: RecyclerView,
                    v: RecyclerView.ViewHolder,
                    t: RecyclerView.ViewHolder,
                ) = false

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int,
                ) {
                    val position = viewHolder.adapterPosition
                    val item = adapter.currentList[position]

                    if (direction == ItemTouchHelper.RIGHT) {
                        // Spec: swipe right always marks as READ (not a toggle)
                        viewModel.setReadState(item.article.url, ReadState.READ)
                    } else if (direction == ItemTouchHelper.LEFT) {
                        // Delete with 5s undo
                        viewModel.deleteArticle(item.article.url)
                        com.google.android.material.snackbar.Snackbar
                            .make(binding.root, "Removed from library", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                            .setAction("Undo") {
                                viewModel.restoreArticle(item)
                            }
                            .show()
                    }
                }
            }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerView)
    }

    private fun setupSearchAndFilters() {
        binding.btnHomeSearchToggle.setOnClickListener {
            binding.toolbar.visibility = View.GONE
            binding.searchLayout.visibility = View.VISIBLE
            binding.btnSearchScope.visibility = View.VISIBLE
            binding.etSearch.requestFocus()
            viewModel.setSearchScope(com.vedesh.readfree.ui.home.HomeViewModel.SearchScope.All)
        }

        binding.btnSearchBack.setOnClickListener {
            binding.searchLayout.visibility = View.GONE
            binding.toolbar.visibility = View.VISIBLE
            binding.btnSearchScope.visibility = View.GONE
            binding.etSearch.text?.clear()
            viewModel.setSearchQuery("")
        }

        binding.btnSearchClear.setOnClickListener {
            binding.etSearch.text?.clear()
        }

        binding.etSearch.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    viewModel.setSearchQuery(s.toString())
                }

                override fun afterTextChanged(s: Editable?) {}
            },
        )

        // Search scope chip: show popup with options + user lists
        binding.btnSearchScope.setOnClickListener { button ->
            val popup = android.widget.PopupMenu(requireContext(), button)
            popup.menu.add(0, 0, 0, "All")
            popup.menu.add(0, 1, 0, "Unsorted")
            popup.menu.add(0, 2, 0, "Offline")
            val currentLists = viewModel.lists.value
            currentLists.forEachIndexed { index, listWithCount ->
                popup.menu.add(0, 10 + index, 0, listWithCount.list.name)
            }
            popup.setOnMenuItemClickListener { item ->
                val scope = when (item.itemId) {
                    0 -> com.vedesh.readfree.ui.home.HomeViewModel.SearchScope.All
                    1 -> com.vedesh.readfree.ui.home.HomeViewModel.SearchScope.Unsorted
                    2 -> com.vedesh.readfree.ui.home.HomeViewModel.SearchScope.Offline
                    else -> {
                        val idx = item.itemId - 10
                        val lwc = currentLists[idx]
                        com.vedesh.readfree.ui.home.HomeViewModel.SearchScope.ListScope(lwc.list.id, lwc.list.name)
                    }
                }
                viewModel.setSearchScope(scope)
                binding.btnSearchScope.text = "${item.title} ▾"
                true
            }
            popup.show()
        }

        // Setup Paste URL functionality
        binding.btnPasteRead.setOnClickListener {
            val url = binding.etPasteUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                val intent = Intent(requireContext(), ReaderActivity::class.java)
                intent.putExtra("url", url)
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "Enter a URL first", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPasteSave.setOnClickListener {
            val url = binding.etPasteUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                val intent = Intent(requireContext(), ReaderActivity::class.java)
                intent.putExtra("url", url)
                intent.putExtra("show_save_sheet", true)
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "Enter a URL first", Toast.LENGTH_SHORT).show()
            }
        }

        binding.chipGroupFilters.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val checkedId = checkedIds.first()
            val chip = group.findViewById<Chip>(checkedId)
            val text = chip?.text?.toString() ?: return@setOnCheckedStateChangeListener

            when (text) {
                "All" -> viewModel.setFilter(HomeViewModel.FilterType.ALL)
                "Unsorted" -> viewModel.setFilter(HomeViewModel.FilterType.UNSORTED)
                "Offline" -> viewModel.setFilter(HomeViewModel.FilterType.OFFLINE)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        viewModel.articles,
                        viewModel.searchQuery,
                    ) { articles, query -> articles to query }.collectLatest { (articles, query) ->
                        adapter.submitList(articles)
                        if (articles.isEmpty()) {
                            if (query.isNotEmpty()) {
                                binding.tvEmptyState.text = "No articles match \"$query\""
                            } else {
                                binding.tvEmptyState.text = "Library is empty.\nPaste a URL to save!"
                            }
                            binding.tvEmptyState.visibility = View.VISIBLE
                        } else {
                            binding.tvEmptyState.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun showArticleContextSheet(item: ArticleWithTags) {
        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_article_context, null)
        sheet.setContentView(view)

        view.findViewById<android.widget.TextView>(R.id.tvContextTitle).text = item.article.title

        view.findViewById<View>(R.id.btnContextOpen).setOnClickListener {
            sheet.dismiss()
            if (item.article.readState == ReadState.UNREAD) {
                viewModel.setReadState(item.article.url, ReadState.READING)
            }
            val intent = Intent(requireContext(), ReaderActivity::class.java)
            intent.putExtra("url", item.article.url)
            startActivity(intent)
        }

        view.findViewById<View>(R.id.btnContextEdit).setOnClickListener {
            sheet.dismiss()
            showMoveToListDialog(item)
        }

        view.findViewById<View>(R.id.btnContextRaindrop).setOnClickListener {
            sheet.dismiss()
            val app = requireContext().applicationContext as ReadFreeApp
            app.saveToRaindrop(requireContext(), item.article.url, item.article.title) { success ->
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(),
                        if (success) "Sent to Raindrop" else "Failed to save to Raindrop",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }

        view.findViewById<View>(R.id.btnContextCopy).setOnClickListener {
            sheet.dismiss()
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("URL", item.article.url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "URL copied", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnContextShare).setOnClickListener {
            sheet.dismiss()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, item.article.url)
            }
            startActivity(Intent.createChooser(intent, "Share via"))
        }

        val btnMarkRead = view.findViewById<android.widget.Button>(R.id.btnContextMarkRead)
        btnMarkRead.text = if (item.article.readState == ReadState.READ) "↩ Mark as Unread" else "✓ Mark as Read"
        btnMarkRead.setOnClickListener {
            sheet.dismiss()
            viewModel.toggleReadState(item.article.url, item.article.readState)
        }

        view.findViewById<View>(R.id.btnContextDelete).setOnClickListener {
            sheet.dismiss()
            viewModel.deleteArticle(item.article.url)
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, "Removed from library", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction("Undo") { viewModel.restoreArticle(item) }
                .show()
        }

        sheet.show()
    }

    private fun showMoveToListDialog(item: ArticleWithTags) {
        val allLists = viewModel.lists.value
        if (allLists.isEmpty()) {
            Toast.makeText(requireContext(), "No lists yet. Create one from the Lists screen.", Toast.LENGTH_SHORT).show()
            return
        }
        val listNames = allLists.map { "${it.list.emoji} ${it.list.name}" }.toTypedArray()
        val currentListIds = item.lists.map { it.id }.toSet()
        val checked = BooleanArray(allLists.size) { allLists[it].list.id in currentListIds }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Move to List")
            .setMultiChoiceItems(listNames, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                viewModel.updateArticleLists(item.article.url, allLists.map { it.list.id }, checked)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSettingsSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetSettingsBinding.inflate(layoutInflater)
        sheet.setContentView(sheetBinding.root)

        val currentMirror = mirrors.getActiveMirror()
        val app = requireContext().applicationContext as ReadFreeApp

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

        // Load Raindrop settings
        sheetBinding.etRaindropToken.setText(app.settingsRepository.getRaindropToken() ?: "")
        sheetBinding.switchRaindropSync.isChecked = app.settingsRepository.isRaindropSyncEnabled()

        val savedMode = app.settingsRepository.getRaindropSaveMode()
        android.util.Log.d("ReadFreeSettings", "Loaded save mode: '$savedMode'")

        // Clear any auto-checked state and set the correct radio button directly
        // to avoid RadioGroup.check() no-op bugs (b/77937021)
        sheetBinding.radioRaindropApi.isChecked = false
        sheetBinding.radioRaindropIntent.isChecked = false
        sheetBinding.root.post {
            if (app.settingsRepository.getRaindropSaveMode() == "API") {
                sheetBinding.radioRaindropApi.isChecked = true
            } else {
                sheetBinding.radioRaindropIntent.isChecked = true
            }
        }

        sheetBinding.radioGroupRaindropMode.setOnCheckedChangeListener { _, id ->
            val rb = sheetBinding.root.findViewById<RadioButton>(id)
            android.util.Log.d("ReadFreeSettings", "Radio changed: id=$id, tag=${rb?.tag}")
        }

        sheetBinding.btnVerifyToken.setOnClickListener {
            val token = sheetBinding.etRaindropToken.text.toString().trim()
            if (token.isEmpty()) {
                sheetBinding.tvTokenStatus.text = "Enter a token first"
                sheetBinding.tvTokenStatus.setTextColor(android.graphics.Color.RED)
                return@setOnClickListener
            }
            sheetBinding.tvTokenStatus.text = "Verifying..."
            app.raindropRepository.verifyToken(token) { success, message ->
                requireActivity().runOnUiThread {
                    sheetBinding.tvTokenStatus.text = message
                    sheetBinding.tvTokenStatus.setTextColor(
                        if (success) android.graphics.Color.GREEN else android.graphics.Color.RED,
                    )
                }
            }
        }

        // Offline Storage section
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val offlineDir = java.io.File(requireContext().filesDir, "offline")
                val sizeBytes =
                    if (offlineDir.exists()) {
                        offlineDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                    } else {
                        0L
                    }
                val sizeMb = String.format("%.2f MB", sizeBytes / (1024f * 1024f))

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    sheetBinding.tvOfflineStorageUsed.text = "Used: $sizeMb"
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    sheetBinding.tvOfflineStorageUsed.text = "Used: 0.00 MB"
                }
            }
        }

        sheetBinding.btnClearOffline.setOnClickListener {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val offlineDir = java.io.File(requireContext().filesDir, "offline")
                if (offlineDir.exists()) {
                    offlineDir.deleteRecursively()
                }
                app.articleRepository.clearAllOfflinePaths()

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    sheetBinding.tvOfflineStorageUsed.text = "Used: 0.00 MB"
                    Toast.makeText(requireContext(), "Offline files cleared", Toast.LENGTH_SHORT).show()
                }
            }
        }

        sheetBinding.btnApplyMirror.setOnClickListener {
            // Save Mirror
            val selected =
                sheetBinding.root.findViewById<RadioButton>(
                    sheetBinding.radioGroupMirrors.checkedRadioButtonId,
                )
            val newUrl =
                if (selected?.id == sheetBinding.radioMirrorCustom.id) {
                    val custom = sheetBinding.etCustomMirrorUrl.text.toString().trim()
                    if (custom.isEmpty()) {
                        Toast.makeText(requireContext(), "Enter a mirror URL first", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (custom.endsWith("/")) custom else "$custom/"
                } else {
                    MirrorRepository.DEFAULT_MIRROR
                }

            mirrors.saveUserMirror(newUrl)

            // Save Raindrop Settings
            app.settingsRepository.saveRaindropToken(sheetBinding.etRaindropToken.text.toString())
            app.settingsRepository.setRaindropSyncEnabled(sheetBinding.switchRaindropSync.isChecked)

            val checkedId = sheetBinding.radioGroupRaindropMode.checkedRadioButtonId
            val raindropModeRb = sheetBinding.root.findViewById<RadioButton>(checkedId)
            val modeToSave = raindropModeRb?.tag?.toString() ?: "API"
            android.util.Log.d("ReadFreeSettings", "Saving mode: '$modeToSave' (checkedId=$checkedId)")
            app.settingsRepository.setRaindropSaveMode(modeToSave)

            Toast.makeText(requireContext(), "Settings saved", Toast.LENGTH_SHORT).show()
            sheet.dismiss()
        }

        sheet.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
