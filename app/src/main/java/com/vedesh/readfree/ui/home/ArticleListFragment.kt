package com.vedesh.readfree.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.vedesh.readfree.ReadFreeApp
import com.vedesh.readfree.R
import com.vedesh.readfree.data.db.entity.ReadState
import com.vedesh.readfree.databinding.FragmentArticleListBinding
import com.vedesh.readfree.data.model.ArticleWithTags
import com.vedesh.readfree.data.model.ListWithCount
import com.vedesh.readfree.data.repository.ArticleRepository
import com.vedesh.readfree.data.repository.ListRepository
import com.vedesh.readfree.data.repository.TagRepository
import com.vedesh.readfree.data.repository.RaindropRepository
import com.vedesh.readfree.ui.reader.ReaderActivity
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Isolated ViewModel that holds a single fixed flow — doesn't touch HomeViewModel's filter state
class ArticleListViewModel(
    private val articleRepo: ArticleRepository,
    private val listRepo: ListRepository,
    private val tagRepo: TagRepository,
    private val raindropRepo: RaindropRepository,
) : ViewModel() {
    private var _articles: Flow<List<ArticleWithTags>>? = null
    val articles: Flow<List<ArticleWithTags>>
        get() = _articles ?: articleRepo.getAll()

    val lists: StateFlow<List<ListWithCount>> =
        listRepo.getAllWithCounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setListFilter(listId: Long) {
        _articles = articleRepo.getByList(listId)
    }

    fun setTagFilter(tagName: String) {
        _articles = articleRepo.getByTag(tagName)
    }

    fun setOfflineFilter() {
        _articles = articleRepo.getOffline()
    }

    fun setReadState(articleUrl: String, state: ReadState) {
        viewModelScope.launch { articleRepo.updateReadState(articleUrl, state) }
    }

    fun toggleReadState(articleUrl: String, currentState: ReadState) {
        val newState = if (currentState == ReadState.READ) ReadState.UNREAD else ReadState.READ
        viewModelScope.launch { articleRepo.updateReadState(articleUrl, newState) }
    }

    fun deleteArticle(articleUrl: String) {
        viewModelScope.launch {
            articleRepo.getByUrl(articleUrl)?.let { articleRepo.delete(it) }
        }
    }

    fun restoreArticle(item: ArticleWithTags) {
        viewModelScope.launch {
            articleRepo.insert(item.article)
            item.lists.forEach { list ->
                listRepo.addArticleToList(item.article.url, list.id)
            }
            item.tags.forEach { tag ->
                tagRepo.addTagToArticle(item.article.url, tag.name)
            }
        }
    }

    fun updateArticleLists(articleUrl: String, listIds: List<Long>, checked: BooleanArray) {
        viewModelScope.launch {
            listIds.forEachIndexed { index, listId ->
                if (checked[index]) {
                    listRepo.addArticleToList(articleUrl, listId)
                } else {
                    listRepo.removeArticleFromList(articleUrl, listId)
                }
            }
        }
    }
}

class ArticleListFragment : Fragment() {
    private var _binding: FragmentArticleListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ArticleListViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = requireContext().applicationContext as ReadFreeApp
                @Suppress("UNCHECKED_CAST")
                return ArticleListViewModel(app.articleRepository, app.listRepository, app.tagRepository, app.raindropRepository) as T
            }
        }
    }

    private lateinit var adapter: ArticleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentArticleListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val listId = arguments?.getLong("listId", -1L) ?: -1L
        val tagName = arguments?.getString("tagName")
        val title = arguments?.getString("title") ?: "Articles"
        val isOffline = arguments?.getBoolean("isOffline", false) ?: false

        binding.toolbar.title = title
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        when {
            listId != -1L -> viewModel.setListFilter(listId)
            tagName != null -> viewModel.setTagFilter(tagName)
            isOffline -> viewModel.setOfflineFilter()
        }

        setupRecyclerView()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.articles.collectLatest { articles ->
                    adapter.submitList(articles)
                    binding.tvEmptyState.visibility = if (articles.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerViewArticles.visibility = if (articles.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ArticleAdapter(
            onClick = { articleWithTags ->
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
        binding.recyclerViewArticles.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewArticles.adapter = adapter
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
