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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.vedesh.readfree.MirrorRepository
import com.vedesh.readfree.R
import com.vedesh.readfree.ReadFreeApp
import com.vedesh.readfree.databinding.BottomSheetSettingsBinding
import com.vedesh.readfree.databinding.FragmentHomeBinding
import com.vedesh.readfree.ui.ViewModelFactory
import com.vedesh.readfree.ui.reader.ReaderActivity
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.graphics.Color
import android.widget.RadioGroup
import com.vedesh.readfree.data.model.ArticleWithTags
import com.vedesh.readfree.data.db.entity.ReadState

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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        mirrors = MirrorRepository(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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

        binding.fabAdd.setOnClickListener {
            // For now, launch intent directly, but Phase 4 covers the Quick Save Bottom Sheet.
            Toast.makeText(requireContext(), "Use Share intent to save for now. Phase 4 adds Quick Save Sheet.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = ArticleAdapter(
            onClick = { articleWithTags ->
                val intent = Intent(requireContext(), ReaderActivity::class.java)
                intent.putExtra("url", articleWithTags.article.url)
                startActivity(intent)
            },
            onLongClick = { articleWithTags ->
                showArticleContextSheet(articleWithTags)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = adapter.currentList[position]

                if (direction == ItemTouchHelper.RIGHT) {
                    viewModel.toggleReadState(item.article.url, item.article.readState)
                    // We don't remove the item immediately if filter is ALL, but UI updates via Flow
                    adapter.notifyItemChanged(position)
                } else if (direction == ItemTouchHelper.LEFT) {
                    viewModel.deleteArticle(item.article.url)
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerView)
    }

    private fun setupSearchAndFilters() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipGroupFilters.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            
            val checkedId = checkedIds.first()
            val chip = group.findViewById<Chip>(checkedId)
            val text = chip?.text?.toString() ?: return@setOnCheckedStateChangeListener

            when (text) {
                "All" -> viewModel.setFilter(HomeViewModel.FilterType.ALL)
                "Unsorted" -> viewModel.setFilter(HomeViewModel.FilterType.UNSORTED)
                "Offline" -> viewModel.setFilter(HomeViewModel.FilterType.OFFLINE)
                else -> {
                    // It's a custom list, find its ID from the lists StateFlow
                    val list = viewModel.lists.value.find { it.list.name == text }
                    if (list != null) {
                        viewModel.setFilter(HomeViewModel.FilterType.LIST, listId = list.list.id)
                    }
                }
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

                launch {
                    viewModel.lists.collectLatest { lists ->
                        // Remove old custom list chips
                        val childrenToRemove = mutableListOf<View>()
                        for (i in 0 until binding.chipGroupFilters.childCount) {
                            val view = binding.chipGroupFilters.getChildAt(i)
                            if (view.id != R.id.chipAll && view.id != R.id.chipUnsorted && view.id != R.id.chipOffline) {
                                childrenToRemove.add(view)
                            }
                        }
                        childrenToRemove.forEach { binding.chipGroupFilters.removeView(it) }

                        // Add new chips
                        lists.forEach { listWithCount ->
                            val chip = Chip(requireContext()).apply {
                                text = listWithCount.list.name
                                isCheckable = true
                                setChipDrawable(com.google.android.material.chip.ChipDrawable.createFromAttributes(requireContext(), null, 0, com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice))
                            }
                            binding.chipGroupFilters.addView(chip)
                        }

                        // Add + New List chip
                        val newChip = Chip(requireContext()).apply {
                            text = "+ New List"
                            isCheckable = false
                            setChipDrawable(com.google.android.material.chip.ChipDrawable.createFromAttributes(requireContext(), null, 0, com.google.android.material.R.style.Widget_MaterialComponents_Chip_Action))
                            setOnClickListener {
                                showCreateListSheet()
                            }
                        }
                        binding.chipGroupFilters.addView(newChip)
                    }
                }
            }
        }
    }

    private fun showCreateListSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_create_list, null)
        sheet.setContentView(view)

        val etListName = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etListName)
        val chipGroupEmojis = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupEmojis)
        val radioGroupColors = view.findViewById<RadioGroup>(R.id.radioGroupColors)
        val btnCreateList = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCreateList)

        val emojis = listOf("📁", "📚", "💼", "🤖", "🎯", "⭐", "🔬", "🎨", "🌐", "📝", "🏠", "💡", "🔥", "🌱", "🎵", "🎮")
        val colors = listOf("#6C63FF", "#FF6584", "#4CAF50", "#FF9800", "#00BCD4", "#E91E63", "#9C27B0", "#3F51B5")

        emojis.forEach { emoji ->
            val chip = Chip(requireContext()).apply {
                text = emoji
                isCheckable = true
                setChipDrawable(com.google.android.material.chip.ChipDrawable.createFromAttributes(requireContext(), null, 0, com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice))
            }
            chipGroupEmojis.addView(chip)
        }

        colors.forEach { colorHex ->
            val rb = RadioButton(requireContext()).apply {
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(colorHex))
                tag = colorHex
            }
            radioGroupColors.addView(rb)
        }

        // Default selections
        if (chipGroupEmojis.childCount > 0) (chipGroupEmojis.getChildAt(0) as Chip).isChecked = true
        if (radioGroupColors.childCount > 0) (radioGroupColors.getChildAt(0) as RadioButton).isChecked = true

        btnCreateList.setOnClickListener {
            val name = etListName.text.toString().trim()
            if (name.isNotEmpty()) {
                val selectedEmojiChip = chipGroupEmojis.findViewById<Chip>(chipGroupEmojis.checkedChipId)
                val emoji = selectedEmojiChip?.text?.toString() ?: "📁"
                
                val selectedColorRb = radioGroupColors.findViewById<RadioButton>(radioGroupColors.checkedRadioButtonId)
                val color = selectedColorRb?.tag?.toString() ?: "#6C63FF"

                viewModel.createList(name, emoji, color)
                sheet.dismiss()
            }
        }

        sheet.show()
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
            val intent = Intent(Intent.ACTION_SEND).apply {
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

        sheetBinding.btnApplyMirror.setOnClickListener {
            // Save Mirror
            val selected = sheetBinding.root.findViewById<RadioButton>(
                sheetBinding.radioGroupMirrors.checkedRadioButtonId
            )
            val newUrl = if (selected?.id == sheetBinding.radioMirrorCustom.id) {
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

            sheet.dismiss()
            Toast.makeText(requireContext(), "Settings saved", Toast.LENGTH_SHORT).show()
        }

        sheet.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
