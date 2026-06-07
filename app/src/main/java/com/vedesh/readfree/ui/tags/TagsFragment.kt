package com.vedesh.readfree.ui.tags

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.vedesh.readfree.R
import com.vedesh.readfree.ReadFreeApp
import com.vedesh.readfree.ui.ViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TagsFragment : Fragment() {

    private val viewModel: TagsViewModel by viewModels {
        val app = requireContext().applicationContext as ReadFreeApp
        ViewModelFactory(app.articleRepository, app.listRepository, app.tagRepository, app.raindropRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tags, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupTags)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tags.collectLatest { tags ->
                    chipGroup.removeAllViews()
                    tags.forEach { tagWithCount ->
                        val chip = Chip(requireContext()).apply {
                            text = "${tagWithCount.name} (${tagWithCount.articleCount})"
                            isClickable = true
                            setOnClickListener {
                                // Filter HomeFragment by tag logic to be implemented
                                findNavController().navigateUp()
                            }
                            setOnLongClickListener {
                                // Long press to delete for now
                                viewModel.deleteTag(com.vedesh.readfree.data.db.entity.Tag(tagWithCount.name))
                                Toast.makeText(requireContext(), "Tag deleted", Toast.LENGTH_SHORT).show()
                                true
                            }
                        }
                        chipGroup.addView(chip)
                    }
                }
            }
        }
    }
}
