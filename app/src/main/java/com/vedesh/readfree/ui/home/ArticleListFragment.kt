package com.vedesh.readfree.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.vedesh.readfree.ReadFreeApp
import com.vedesh.readfree.databinding.FragmentArticleListBinding
import com.vedesh.readfree.data.model.ArticleWithTags
import com.vedesh.readfree.data.repository.ArticleRepository
import com.vedesh.readfree.ui.ViewModelFactory
import com.vedesh.readfree.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Isolated ViewModel that holds a single fixed flow — doesn't touch HomeViewModel's filter state
class ArticleListViewModel(
    private val articleRepo: ArticleRepository,
) : ViewModel() {
    private var _articles: Flow<List<ArticleWithTags>>? = null
    val articles: Flow<List<ArticleWithTags>>
        get() = _articles ?: articleRepo.getAll()

    fun setListFilter(listId: Long) {
        _articles = articleRepo.getByList(listId)
    }

    fun setTagFilter(tagName: String) {
        _articles = articleRepo.getByTag(tagName)
    }

    fun setOfflineFilter() {
        _articles = articleRepo.getOffline()
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
                return ArticleListViewModel(app.articleRepository) as T
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

        // Set the right filter BEFORE collecting — the ViewModel holds a cold Flow reference
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
                val intent = Intent(requireContext(), ReaderActivity::class.java)
                intent.putExtra("url", articleWithTags.article.url)
                startActivity(intent)
            },
            onLongClick = { _ ->
                // TODO: wire to context sheet
            },
        )
        binding.recyclerViewArticles.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewArticles.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
