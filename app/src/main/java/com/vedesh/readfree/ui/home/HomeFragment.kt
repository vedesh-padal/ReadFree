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
            binding.etSearch.requestFocus()
            // optionally show keyboard here
        }

        binding.btnSearchBack.setOnClickListener {
            binding.searchLayout.visibility = View.GONE
            binding.toolbar.visibility = View.VISIBLE
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
                    viewModel.articles.collectLatest { articles ->
                        adapter.submitList(articles)
                        binding.tvEmptyState.visibility = if (articles.isEmpty()) View.VISIBLE else View.GONE
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
            val intent = Intent(requireContext(), ReaderActivity::class.java)
            intent.putExtra("url", item.article.url)
            startActivity(intent)
        }

        view.findViewById<View>(R.id.btnContextEdit).setOnClickListener {
            sheet.dismiss()
            // In a full implementation, this opens SaveBottomSheet from HomeFragment.
            // For now, we can show a Toast.
            Toast.makeText(requireContext(), "Edit List & Tags via ReaderActivity right now.", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnContextRaindrop).setOnClickListener {
            sheet.dismiss()
            val app = requireContext().applicationContext as ReadFreeApp
            app.raindropRepository.syncArticle(item.article.url, item.article.title)
            Toast.makeText(requireContext(), "Sent to Raindrop", Toast.LENGTH_SHORT).show()
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
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, item.article.url)
                }
            startActivity(Intent.createChooser(intent, "Share via"))
        }

        val btnMarkRead = view.findViewById<android.widget.Button>(R.id.btnContextMarkRead)
        btnMarkRead.text = if (item.article.readState == ReadState.READ) "Mark as Unread" else "✓ Mark as Read"
        btnMarkRead.setOnClickListener {
            sheet.dismiss()
            viewModel.toggleReadState(item.article.url, item.article.readState)
        }

        view.findViewById<View>(R.id.btnContextDelete).setOnClickListener {
            sheet.dismiss()
            viewModel.deleteArticle(item.article.url)
        }

        sheet.show()
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

        if (app.settingsRepository.getRaindropSaveMode() == "API") {
            sheetBinding.radioGroupRaindropMode.check(R.id.radioRaindropApi)
        } else {
            sheetBinding.radioGroupRaindropMode.check(R.id.radioRaindropIntent)
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

            val raindropModeRb =
                sheetBinding.root.findViewById<RadioButton>(
                    sheetBinding.radioGroupRaindropMode.checkedRadioButtonId,
                )
            app.settingsRepository.setRaindropSaveMode(raindropModeRb?.tag?.toString() ?: "API")

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
