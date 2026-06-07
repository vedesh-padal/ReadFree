package com.vedesh.readfree.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.vedesh.readfree.ReadFreeApp
import com.vedesh.readfree.databinding.FragmentArticleListBinding
import com.vedesh.readfree.ui.ViewModelFactory
import com.vedesh.readfree.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ArticleListFragment : Fragment() {
    private var _binding: FragmentArticleListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels {
        val app = requireContext().applicationContext as ReadFreeApp
        ViewModelFactory(
            app.articleRepository,
            app.listRepository,
            app.tagRepository,
            app.raindropRepository,
        )
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

        binding.toolbar.title = title
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupRecyclerView()

        if (listId != -1L) {
            viewModel.setFilter(HomeViewModel.FilterType.LIST, listId = listId)
        } else if (tagName != null) {
            viewModel.setFilter(HomeViewModel.FilterType.TAG, tag = tagName)
        } else {
            viewModel.setFilter(HomeViewModel.FilterType.ALL)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.articles.collectLatest { articles ->
                    adapter.submitList(articles)
                    if (articles.isEmpty()) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.recyclerViewArticles.visibility = View.GONE
                    } else {
                        binding.tvEmptyState.visibility = View.GONE
                        binding.recyclerViewArticles.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter =
            ArticleAdapter(
                onClick = { articleWithTags ->
                    val intent = Intent(requireContext(), ReaderActivity::class.java)
                    intent.putExtra("url", articleWithTags.article.url)
                    startActivity(intent)
                },
                onLongClick = { _ ->
                    // Basic context actions or ignore for now
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
